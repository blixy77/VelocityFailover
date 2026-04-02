package pl.blixy.velocityFailover.config;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FailoverConfig {

    private final String limboServer;
    private final Set<String> monitoredServers;
    private final long pingIntervalMs;
    private final int pingsToReady;
    private final long gracePeriodMs;
    private final long transferIntervalMs;
    private final long pingTimeoutMs;
    private final String sentToLimboMessage;
    private final String reconnectingMessage;
    private final String connectionBlockedMessage;

    @SuppressWarnings("unchecked")
    public FailoverConfig(Map<String, Object> yaml) {
        this.limboServer = (String) yaml.getOrDefault("limbo-server", "limbo");

        this.monitoredServers = new HashSet<>();
        Map<String, Object> groups = (Map<String, Object>) yaml.getOrDefault("groups", Map.of());
        for (Map.Entry<String, Object> entry : groups.entrySet()) {
            Map<String, Object> group = (Map<String, Object>) entry.getValue();
            List<String> servers = (List<String>) group.getOrDefault("servers", List.of());
            this.monitoredServers.addAll(servers);
        }

        Map<String, Object> recovery = (Map<String, Object>) yaml.getOrDefault("recovery", Map.of());
        this.pingIntervalMs = toLong(recovery.getOrDefault("ping-interval-ms", 2000));
        this.pingsToReady = toInt(recovery.getOrDefault("pings-to-ready", 3));
        this.gracePeriodMs = toLong(recovery.getOrDefault("grace-period-ms", 5000));
        this.transferIntervalMs = toLong(recovery.getOrDefault("transfer-interval-ms", 50));
        this.pingTimeoutMs = toLong(recovery.getOrDefault("ping-timeout-ms", 2000));

        Map<String, Object> messages = (Map<String, Object>) yaml.getOrDefault("messages", Map.of());
        this.sentToLimboMessage = (String) messages.getOrDefault("sent-to-limbo", "<red>The server is temporarily unavailable. You will be moved back automatically when it returns.");
        this.reconnectingMessage = (String) messages.getOrDefault("reconnecting", "<green>The server is back online! Reconnecting...");
        this.connectionBlockedMessage = (String) messages.getOrDefault("connection-blocked", "<red>This server is currently unavailable. Please try again in a moment.");
    }

    private static long toLong(Object value) {
        return ((Number) value).longValue();
    }

    private static int toInt(Object value) {
        return ((Number) value).intValue();
    }

    public String getLimboServer() {
        return limboServer;
    }

    public Set<String> getMonitoredServers() {
        return monitoredServers;
    }

    public long getPingIntervalMs() {
        return pingIntervalMs;
    }

    public int getPingsToReady() {
        return pingsToReady;
    }

    public long getGracePeriodMs() {
        return gracePeriodMs;
    }

    public long getTransferIntervalMs() {
        return transferIntervalMs;
    }

    public long getPingTimeoutMs() {
        return pingTimeoutMs;
    }

    public String getSentToLimboMessage() {
        return sentToLimboMessage;
    }

    public String getReconnectingMessage() {
        return reconnectingMessage;
    }

    public String getConnectionBlockedMessage() {
        return connectionBlockedMessage;
    }
}