package pl.blixy.velocityFailover.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import pl.blixy.velocityFailover.config.FailoverConfig;
import pl.blixy.velocityFailover.reconnect.PendingReconnectRegistry;

import java.util.UUID;

public class DisconnectListener {

    private final FailoverConfig config;
    private final PendingReconnectRegistry pendingRegistry;

    public DisconnectListener(FailoverConfig config, PendingReconnectRegistry pendingRegistry) {
        this.config = config;
        this.pendingRegistry = pendingRegistry;
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        pendingRegistry.remove(event.getPlayer().getUniqueId());
    }

    @Subscribe
    public void onServerSwitch(ServerConnectedEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();

        if (!pendingRegistry.isPending(playerId)) return;

        String connectedServer = event.getServer().getServerInfo().getName();
        String pendingServer = pendingRegistry.getPendingServer(playerId);

        if (!connectedServer.equals(config.getLimboServer()) && !connectedServer.equals(pendingServer)) {
            pendingRegistry.remove(playerId);
        }
    }
}
