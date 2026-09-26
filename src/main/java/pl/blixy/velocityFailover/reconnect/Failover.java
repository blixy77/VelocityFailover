package pl.blixy.velocityFailover.reconnect;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.sound.Sound;
import org.slf4j.Logger;
import pl.blixy.velocityFailover.VelocityFailover;
import pl.blixy.velocityFailover.config.FailoverConfig;
import pl.blixy.velocityFailover.server.ServerState;
import pl.blixy.velocityFailover.server.ServerStates;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * What happens around a crash: once a server is down, the players still on it are swept to limbo; once
 * it is back, they are returned one at a time after the grace period.
 */
public final class Failover {

    /** How long the kick that flagged the crash gets to redirect players itself before the sweep looks for stragglers. */
    private static final Duration REDIRECT_SETTLE = Duration.ofMillis(500);

    private final VelocityFailover plugin;
    private final ProxyServer proxy;
    private final Logger logger;
    private final FailoverConfig config;
    private final ServerStates states;
    private final WaitingPlayers waiting;

    public Failover(VelocityFailover plugin, ProxyServer proxy, Logger logger, FailoverConfig config, ServerStates states, WaitingPlayers waiting) {
        this.plugin = plugin;
        this.proxy = proxy;
        this.logger = logger;
        this.config = config;
        this.states = states;
        this.waiting = waiting;
    }

    public void serverDown(String server) {
        proxy.getScheduler().buildTask(plugin, () -> sweep(server)).delay(REDIRECT_SETTLE).schedule();
    }

    /** The grace period lets the server load its plugins before the first player arrives. */
    public void serverRecovering(String server) {
        Duration gracePeriod = config.recovery().gracePeriod();
        Duration connectingDelay = config.titleAnimation().connectingDelay();
        Duration delay = gracePeriod.compareTo(connectingDelay) >= 0 ? gracePeriod : connectingDelay;
        config.sounds().connecting().ifPresent(sound -> waiting.waitingFor(server).forEach(uuid ->
                proxy.getPlayer(uuid).filter(config::isLimbo)
                        .ifPresent(player -> player.playSound(sound, Sound.Emitter.self()))));
        proxy.getScheduler().buildTask(plugin, () -> startTransfer(server)).delay(delay).schedule();
    }

    private void sweep(String name) {
        RegisteredServer server = proxy.getServer(name).orElse(null);
        RegisteredServer limbo = proxy.getServer(config.limbo()).orElse(null);
        if (server == null) {
            return;
        }
        if (limbo == null) {
            logger.error("[Failover] CRITICAL: Limbo server '{}' not found! Cannot redirect players.", config.limbo());
            return;
        }

        for (Player player : server.getPlayersConnected()) {
            if (!waiting.add(player.getUniqueId(), name)) {
                continue;
            }

            player.sendMessage(config.messages().sentToLimbo());
            player.createConnectionRequest(limbo).connect().whenComplete((_, error) -> {
                if (error != null) {
                    logger.warn("[Failover] Failed to move player {} to limbo: {}", player.getUsername(), error.getMessage());
                }
            });
        }
    }

    private void startTransfer(String name) {
        if (states.state(name) != ServerState.RECOVERY) {
            logger.info("[Failover] Server {} no longer in RECOVERY, aborting transfer", name);
            return;
        }

        RegisteredServer server = proxy.getServer(name).orElse(null);
        if (server == null) {
            states.markOnline(name);
            return;
        }

        List<UUID> players = waiting.waitingFor(name);
        if (players.isEmpty()) {
            logger.info("[Failover] No players to transfer back to {}", name);
            states.markOnline(name);
            return;
        }

        logger.info("[Failover] Starting gradual transfer of {} players to {}", players.size(), name);
        proxy.getScheduler().buildTask(plugin, new TransferTask(proxy, logger, config, states, waiting, server, players))
                .repeat(config.recovery().transferInterval())
                .schedule();
    }
}
