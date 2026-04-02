package pl.blixy.velocityFailover;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import org.slf4j.Logger;
import pl.blixy.velocityFailover.config.ConfigLoader;
import pl.blixy.velocityFailover.config.FailoverConfig;
import pl.blixy.velocityFailover.handler.ServerDownHandler;
import pl.blixy.velocityFailover.handler.ServerUpHandler;
import pl.blixy.velocityFailover.listener.ConnectionListener;
import pl.blixy.velocityFailover.listener.DisconnectListener;
import pl.blixy.velocityFailover.listener.KickListener;
import pl.blixy.velocityFailover.reconnect.PendingReconnectRegistry;
import pl.blixy.velocityFailover.server.RecoveryMonitor;
import pl.blixy.velocityFailover.server.ServerStateRegistry;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Plugin(id = "velocityfailover", name = "VelocityFailover", version = BuildConstants.VERSION,
        url = "blixy.pl", authors = {"blixy77"})
public class VelocityFailover {

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;

    private ScheduledTask recoveryTask;

    @Inject
    public VelocityFailover(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        FailoverConfig config;
        try {
            config = ConfigLoader.load(dataDirectory);
        } catch (Exception e) {
            logger.error("[Failover] Failed to load config!", e);
            return;
        }

        if (config.getMonitoredServers().isEmpty()) {
            logger.warn("[Failover] No monitored servers configured. Plugin disabled.");
            return;
        }

        logger.info("[Failover] Monitoring {} servers, limbo: {}", config.getMonitoredServers().size(), config.getLimboServer());

        PendingReconnectRegistry pendingRegistry = new PendingReconnectRegistry();
        ServerStateRegistry stateRegistry = new ServerStateRegistry(config.getMonitoredServers(), config.getPingsToReady(), logger);

        ServerDownHandler downHandler = new ServerDownHandler(proxy, logger, config, pendingRegistry);
        ServerUpHandler upHandler = new ServerUpHandler(this, proxy, logger, config, stateRegistry, pendingRegistry);

        stateRegistry.setOnServerDown(downHandler::handle);
        stateRegistry.setOnServerRecovering(upHandler::handle);

        proxy.getEventManager().register(this, new KickListener(proxy, config, stateRegistry, pendingRegistry));
        proxy.getEventManager().register(this, new ConnectionListener(config, stateRegistry, pendingRegistry));
        proxy.getEventManager().register(this, new DisconnectListener(config, pendingRegistry));

        RecoveryMonitor monitor = new RecoveryMonitor(proxy, config, stateRegistry);
        recoveryTask = proxy.getScheduler().buildTask(this, monitor)
                .repeat(config.getPingIntervalMs(), TimeUnit.MILLISECONDS)
                .schedule();

        logger.info("[Failover] Plugin enabled successfully.");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (recoveryTask != null) {
            recoveryTask.cancel();
        }
    }
}
