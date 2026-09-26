package pl.blixy.velocityFailover;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import org.slf4j.Logger;
import pl.blixy.velocityFailover.command.ReloadCommand;
import pl.blixy.velocityFailover.config.ConfigLoader;
import pl.blixy.velocityFailover.config.FailoverConfig;
import pl.blixy.velocityFailover.listener.FailoverListener;
import pl.blixy.velocityFailover.reconnect.Failover;
import pl.blixy.velocityFailover.reconnect.WaitingActionBar;
import pl.blixy.velocityFailover.reconnect.WaitingPlayers;
import pl.blixy.velocityFailover.reconnect.WaitingTitles;
import pl.blixy.velocityFailover.server.RecoveryMonitor;
import pl.blixy.velocityFailover.server.ServerStates;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Keeps players on the network through a backend crash: a shutdown kick parks them on limbo, the
 * server is pinged until it answers, then they are moved back one at a time. Everything is rebuilt
 * from the config on {@code /failoverreload}, so the listener and both tasks are torn down first.
 */
@Plugin(id = "velocityfailover", name = "VelocityFailover", version = BuildConstants.VERSION, url = "blixy.pl", authors = "blixy77")
public final class VelocityFailover {

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private final List<ScheduledTask> tasks = new ArrayList<>();
    private FailoverListener listener;

    @Inject
    public VelocityFailover(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onInitialize(ProxyInitializeEvent event) {
        start();
        CommandManager commands = proxy.getCommandManager();
        commands.register(commands.metaBuilder("failoverreload").plugin(this).build(), new ReloadCommand(this));
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        stop();
    }

    public void reload() {
        stop();
        start();
        logger.info("[Failover] Configuration reloaded.");
    }

    private void start() {
        FailoverConfig config;
        try {
            config = ConfigLoader.load(dataDirectory);
        } catch (IOException e) {
            logger.error("[Failover] Failed to load config!", e);
            return;
        }

        if (config.servers().isEmpty()) {
            logger.warn("[Failover] No monitored servers configured. Plugin disabled.");
            return;
        }

        logger.info("[Failover] Monitoring {} servers, limbo: {}", config.servers().size(), config.limbo());
        WaitingPlayers waiting = new WaitingPlayers();
        ServerStates states = new ServerStates(config.servers(), config.recovery().pingsToReady(), logger);
        Failover failover = new Failover(this, proxy, logger, config, states, waiting);

        listener = new FailoverListener(proxy, config, states, waiting, failover);
        proxy.getEventManager().register(this, listener);
        tasks.add(proxy.getScheduler().buildTask(this, new RecoveryMonitor(proxy, config, states, failover))
                .repeat(config.recovery().pingInterval())
                .schedule());
        tasks.add(proxy.getScheduler().buildTask(this, new WaitingActionBar(proxy, config, waiting))
                .repeat(config.actionBar().interval())
                .schedule());
        tasks.add(proxy.getScheduler().buildTask(this, new WaitingTitles(proxy, config, waiting, states))
                .repeat(config.titleAnimation().interval())
                .schedule());
    }

    private void stop() {
        tasks.forEach(ScheduledTask::cancel);
        tasks.clear();
        // Only the failover listener: unregistering everything of the plugin would drop the shutdown handler too.
        if (listener != null) {
            proxy.getEventManager().unregisterListener(this, listener);
            listener = null;
        }
    }
}
