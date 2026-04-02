package pl.blixy.velocityFailover.handler;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.slf4j.Logger;
import pl.blixy.velocityFailover.config.FailoverConfig;
import pl.blixy.velocityFailover.reconnect.PendingReconnectRegistry;

import java.util.Optional;

public class ServerDownHandler {

    private final ProxyServer proxy;
    private final Logger logger;
    private final FailoverConfig config;
    private final PendingReconnectRegistry pendingRegistry;

    public ServerDownHandler(ProxyServer proxy, Logger logger, FailoverConfig config, PendingReconnectRegistry pendingRegistry) {
        this.proxy = proxy;
        this.logger = logger;
        this.config = config;
        this.pendingRegistry = pendingRegistry;
    }

    public void handle(String serverName) {
        Optional<RegisteredServer> serverOpt = proxy.getServer(serverName);
        if (serverOpt.isEmpty()) return;

        Optional<RegisteredServer> limboOpt = proxy.getServer(config.getLimboServer());
        if (limboOpt.isEmpty()) {
            logger.error("[Failover] CRITICAL: Limbo server '{}' not found! Cannot redirect players.", config.getLimboServer());
            return;
        }

        RegisteredServer limbo = limboOpt.get();
        RegisteredServer server = serverOpt.get();

        for (Player player : server.getPlayersConnected()) {
            if (!pendingRegistry.register(player.getUniqueId(), serverName)) {
                continue;
            }

            player.sendMessage(MiniMessage.miniMessage().deserialize(config.getSentToLimboMessage()));
            player.createConnectionRequest(limbo).connect()
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            logger.warn("[Failover] Failed to move player {} to limbo: {}", player.getUsername(), error.getMessage());
                        }
                    });
        }
    }
}
