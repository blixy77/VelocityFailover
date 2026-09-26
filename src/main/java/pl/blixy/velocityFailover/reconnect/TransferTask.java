package pl.blixy.velocityFailover.reconnect;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import org.slf4j.Logger;
import pl.blixy.velocityFailover.config.FailoverConfig;
import pl.blixy.velocityFailover.server.ServerState;
import pl.blixy.velocityFailover.server.ServerStates;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Moves one waiting player per run back to a recovered server, so a fresh server is never hit by the
 * whole limbo at once. Cancels itself when the queue is empty (the server is then online) or when the
 * server drops out of recovery in the meantime.
 */
final class TransferTask implements Consumer<ScheduledTask> {

    private final ProxyServer proxy;
    private final Logger logger;
    private final FailoverConfig config;
    private final ServerStates states;
    private final WaitingPlayers waiting;
    private final RegisteredServer server;
    private final Deque<UUID> queue;

    TransferTask(ProxyServer proxy, Logger logger, FailoverConfig config, ServerStates states, WaitingPlayers waiting,
                 RegisteredServer server, Collection<UUID> players) {
        this.proxy = proxy;
        this.logger = logger;
        this.config = config;
        this.states = states;
        this.waiting = waiting;
        this.server = server;
        this.queue = new ArrayDeque<>(players);
    }

    @Override
    public void accept(ScheduledTask task) {
        String name = server.getServerInfo().getName();
        if (states.state(name) != ServerState.RECOVERY) {
            logger.warn("[Failover] Server {} left RECOVERY during transfer, cancelling", name);
            task.cancel();
            return;
        }

        UUID uuid = queue.poll();
        if (uuid == null) {
            task.cancel();
            states.markOnline(name);
            return;
        }

        Player player = proxy.getPlayer(uuid).orElse(null);
        if (player == null || !config.isLimbo(player)) {
            // Gone, or already somewhere else on their own: nothing to bring back.
            waiting.remove(uuid);
            return;
        }

        player.sendMessage(config.messages().reconnecting());
        player.resetTitle();
        player.createConnectionRequest(server).connect().whenComplete((_, error) -> {
            waiting.remove(uuid);
            if (error != null) {
                logger.warn("[Failover] Failed to transfer {} to {}: {}", player.getUsername(), name, error.getMessage());
            }
        });
    }
}
