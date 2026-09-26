package pl.blixy.velocityFailover.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import pl.blixy.velocityFailover.config.FailoverConfig;
import pl.blixy.velocityFailover.reconnect.Failover;
import pl.blixy.velocityFailover.reconnect.WaitingPlayers;
import pl.blixy.velocityFailover.server.ServerStates;

import java.util.UUID;

/**
 * The proxy events the failover runs on: a shutdown kick flags the server and parks the player on limbo,
 * a join to a flagged server is refused, and a player who leaves or wanders off limbo stops waiting.
 */
public final class FailoverListener {

    /** Ahead of hub and fallback plugins, so the crash is noticed before anyone else routes the player. */
    private static final short EARLY = 100;

    private final ProxyServer proxy;
    private final FailoverConfig config;
    private final ServerStates states;
    private final WaitingPlayers waiting;
    private final Failover failover;

    public FailoverListener(ProxyServer proxy, FailoverConfig config, ServerStates states, WaitingPlayers waiting, Failover failover) {
        this.proxy = proxy;
        this.config = config;
        this.states = states;
        this.waiting = waiting;
        this.failover = failover;
    }

    @Subscribe(priority = EARLY)
    public void onKick(KickedFromServerEvent event) {
        String server = event.getServer().getServerInfo().getName();
        if (!states.isMonitored(server) || !isShutdown(event)) {
            return;
        }

        if (states.markOffline(server)) {
            failover.serverDown(server);
        }

        RegisteredServer limbo = proxy.getServer(config.limbo()).orElse(null);
        if (limbo == null) {
            return;
        }

        waiting.add(event.getPlayer().getUniqueId(), server);
        event.setResult(KickedFromServerEvent.RedirectPlayer.create(limbo, config.messages().sentToLimbo()));
    }

    @Subscribe(priority = EARLY)
    public void onPreConnect(ServerPreConnectEvent event) {
        String server = event.getResult().getServer().map(target -> target.getServerInfo().getName()).orElse(null);
        if (server == null || !states.isMonitored(server) || states.isAvailable(server)) {
            return;
        }

        // The transfer back to the server the player is waiting for is the one connection a flagged server takes.
        if (waiting.serverOf(event.getPlayer().getUniqueId()).filter(server::equals).isPresent()) {
            return;
        }

        event.setResult(ServerPreConnectEvent.ServerResult.denied());
        config.messages().connectionBlocked().send(event.getPlayer());
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        waiting.remove(event.getPlayer().getUniqueId());
    }

    /** A waiting player who ends up anywhere but limbo or their own server has moved on and is no longer brought back. */
    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        UUID player = event.getPlayer().getUniqueId();
        String server = event.getServer().getServerInfo().getName();
        if (server.equals(config.limbo())) {
            return;
        }

        waiting.serverOf(player).filter(expected -> !expected.equals(server)).ifPresent(_ -> waiting.remove(player));
    }

    private boolean isShutdown(KickedFromServerEvent event) {
        String reason = event.getServerKickReason().map(PlainTextComponentSerializer.plainText()::serialize).orElse(null);
        return reason != null && config.shutdownKeywords().stream().anyMatch(reason::contains);
    }
}
