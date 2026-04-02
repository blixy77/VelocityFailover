package pl.blixy.velocityFailover.handler;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.slf4j.Logger;
import pl.blixy.velocityFailover.config.FailoverConfig;
import pl.blixy.velocityFailover.reconnect.PendingReconnectRegistry;
import pl.blixy.velocityFailover.server.ServerState;
import pl.blixy.velocityFailover.server.ServerStateRegistry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

public class ServerUpHandler {

    private final Object plugin;
    private final ProxyServer proxy;
    private final Logger logger;
    private final FailoverConfig config;
    private final ServerStateRegistry stateRegistry;
    private final PendingReconnectRegistry pendingRegistry;

    public ServerUpHandler(Object plugin, ProxyServer proxy, Logger logger, FailoverConfig config, ServerStateRegistry stateRegistry, PendingReconnectRegistry pendingRegistry) {
        this.plugin = plugin;
        this.proxy = proxy;
        this.logger = logger;
        this.config = config;
        this.stateRegistry = stateRegistry;
        this.pendingRegistry = pendingRegistry;
    }

    public void handle(String serverName) {
        proxy.getScheduler().buildTask(plugin, () -> startTransfer(serverName))
                .delay(config.getGracePeriodMs(), TimeUnit.MILLISECONDS)
                .schedule();
    }

    private void startTransfer(String serverName) {
        if (stateRegistry.getState(serverName) != ServerState.RECOVERY) {
            logger.info("[Failover] Server {} no longer in RECOVERY, aborting transfer", serverName);
            return;
        }

        Optional<RegisteredServer> serverOpt = proxy.getServer(serverName);
        if (serverOpt.isEmpty()) {
            stateRegistry.markOnline(serverName);
            return;
        }

        RegisteredServer server = serverOpt.get();
        List<UUID> players = pendingRegistry.getPlayersForServer(serverName);

        if (players.isEmpty()) {
            logger.info("[Failover] No players to transfer back to {}", serverName);
            stateRegistry.markOnline(serverName);
            return;
        }

        logger.info("[Failover] Starting gradual transfer of {} players to {}", players.size(), serverName);
        ConcurrentLinkedQueue<UUID> queue = new ConcurrentLinkedQueue<>(players);

        ScheduledTask[] taskHolder = new ScheduledTask[1];
        taskHolder[0] = proxy.getScheduler().buildTask(plugin, () -> {
            if (stateRegistry.getState(serverName) != ServerState.RECOVERY) {
                logger.warn("[Failover] Server {} left RECOVERY during transfer, cancelling", serverName);
                taskHolder[0].cancel();
                return;
            }

            if (queue.isEmpty()) {
                taskHolder[0].cancel();
                stateRegistry.markOnline(serverName);
                return;
            }

            UUID uuid = queue.poll();
            if (uuid == null) return;

            Optional<Player> playerOpt = proxy.getPlayer(uuid);
            if (playerOpt.isEmpty()) {
                pendingRegistry.remove(uuid);
                return;
            }

            Player player = playerOpt.get();

            boolean onLimbo = player.getCurrentServer()
                    .map(conn -> conn.getServerInfo().getName().equals(config.getLimboServer()))
                    .orElse(false);

            if (!onLimbo) {
                pendingRegistry.remove(uuid);
                return;
            }

            player.sendMessage(MiniMessage.miniMessage().deserialize(config.getReconnectingMessage()));
            player.createConnectionRequest(server).connect()
                    .whenComplete((result, error) -> {
                        pendingRegistry.remove(uuid);
                        if (error != null) {
                            logger.warn("[Failover] Failed to transfer {} to {}: {}", player.getUsername(), serverName, error.getMessage());
                        }
                    });
        }).repeat(config.getTransferIntervalMs(), TimeUnit.MILLISECONDS).schedule();
    }
}
