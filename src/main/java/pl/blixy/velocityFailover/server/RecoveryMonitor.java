package pl.blixy.velocityFailover.server;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;
import pl.blixy.velocityFailover.config.FailoverConfig;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class RecoveryMonitor implements Runnable {

    private final ProxyServer proxy;
    private final ServerStateRegistry stateRegistry;
    private final long pingTimeoutMs;

    public RecoveryMonitor(ProxyServer proxy, FailoverConfig config, ServerStateRegistry stateRegistry) {
        this.proxy = proxy;
        this.stateRegistry = stateRegistry;
        this.pingTimeoutMs = config.getPingTimeoutMs();
    }

    @Override
    public void run() {
        Set<String> offline = stateRegistry.getOfflineServers();
        if (offline.isEmpty()) return;

        for (String serverName : offline) {
            Optional<RegisteredServer> serverOpt = proxy.getServer(serverName);
            if (serverOpt.isEmpty()) {
                stateRegistry.recordRecoveryPing(serverName, false);
                continue;
            }

            serverOpt.get().ping()
                    .orTimeout(pingTimeoutMs, TimeUnit.MILLISECONDS)
                    .whenComplete((result, error) -> stateRegistry.recordRecoveryPing(serverName, error == null));
        }
    }
}
