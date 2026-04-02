package pl.blixy.velocityFailover.server;

import org.slf4j.Logger;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ServerStateRegistry {

    private final ConcurrentHashMap<String, ServerState> states = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> successCount = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();
    private final Set<String> monitoredServers;
    private final int pingsToReady;
    private final Logger logger;

    private Consumer<String> onServerDown;
    private Consumer<String> onServerRecovering;

    public ServerStateRegistry(Set<String> monitoredServers, int pingsToReady, Logger logger) {
        this.monitoredServers = monitoredServers;
        this.pingsToReady = pingsToReady;
        this.logger = logger;

        for (String server : monitoredServers) {
            states.put(server, ServerState.ONLINE);
            successCount.put(server, new AtomicInteger(0));
            locks.put(server, new Object());
        }
    }

    public void setOnServerDown(Consumer<String> onServerDown) {
        this.onServerDown = onServerDown;
    }

    public void setOnServerRecovering(Consumer<String> onServerRecovering) {
        this.onServerRecovering = onServerRecovering;
    }

    public void markOffline(String serverName) {
        Object lock = locks.get(serverName);
        if (lock == null) return;

        synchronized (lock) {
            ServerState current = states.get(serverName);
            if (current == ServerState.OFFLINE) return;

            ServerState previous = current;
            states.put(serverName, ServerState.OFFLINE);
            successCount.get(serverName).set(0);
            logger.warn("[Failover] Server {} marked OFFLINE (was {})", serverName, previous);

            if (previous == ServerState.ONLINE && onServerDown != null) {
                onServerDown.accept(serverName);
            }
        }
    }

    public void recordRecoveryPing(String serverName, boolean success) {
        Object lock = locks.get(serverName);
        if (lock == null) return;

        synchronized (lock) {
            ServerState current = states.get(serverName);

            if (success) {
                if (current == ServerState.OFFLINE) {
                    int count = successCount.get(serverName).incrementAndGet();
                    if (count >= pingsToReady) {
                        states.put(serverName, ServerState.RECOVERY);
                        logger.info("[Failover] Server {} entering RECOVERY ({} successful pings)", serverName, count);
                        if (onServerRecovering != null) {
                            onServerRecovering.accept(serverName);
                        }
                    }
                }
            } else {
                successCount.get(serverName).set(0);
                if (current == ServerState.RECOVERY) {
                    states.put(serverName, ServerState.OFFLINE);
                    logger.warn("[Failover] Server {} failed ping during RECOVERY, back to OFFLINE", serverName);
                }
            }
        }
    }

    public void markOnline(String serverName) {
        Object lock = locks.get(serverName);
        if (lock == null) return;

        synchronized (lock) {
            states.put(serverName, ServerState.ONLINE);
            successCount.get(serverName).set(0);
            logger.info("[Failover] Server {} is now ONLINE", serverName);
        }
    }

    public ServerState getState(String serverName) {
        return states.getOrDefault(serverName, ServerState.ONLINE);
    }

    public Set<String> getOfflineServers() {
        return states.entrySet().stream()
                .filter(e -> e.getValue() == ServerState.OFFLINE)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    public boolean isMonitored(String serverName) {
        return monitoredServers.contains(serverName);
    }

    public boolean isAvailable(String serverName) {
        return getState(serverName) == ServerState.ONLINE;
    }
}
