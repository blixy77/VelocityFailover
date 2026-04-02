package pl.blixy.velocityFailover.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import pl.blixy.velocityFailover.config.FailoverConfig;
import pl.blixy.velocityFailover.reconnect.PendingReconnectRegistry;
import pl.blixy.velocityFailover.server.ServerStateRegistry;

public class ConnectionListener {

    private final FailoverConfig config;
    private final ServerStateRegistry stateRegistry;
    private final PendingReconnectRegistry pendingRegistry;

    public ConnectionListener(FailoverConfig config, ServerStateRegistry stateRegistry, PendingReconnectRegistry pendingRegistry) {
        this.config = config;
        this.stateRegistry = stateRegistry;
        this.pendingRegistry = pendingRegistry;
    }

    @Subscribe(priority = 100)
    public void onConnect(ServerPreConnectEvent event) {
        if (event.getResult().getServer().isEmpty()) return;

        String serverName = event.getResult().getServer().get().getServerInfo().getName();

        if (!stateRegistry.isMonitored(serverName)) return;

        if (stateRegistry.isAvailable(serverName)) return;

        String pendingServer = pendingRegistry.getPendingServer(event.getPlayer().getUniqueId());
        if (serverName.equals(pendingServer)) return;

        event.setResult(ServerPreConnectEvent.ServerResult.denied());

        event.getPlayer().sendMessage(MiniMessage.miniMessage().deserialize(config.getConnectionBlockedMessage()));
    }
}
