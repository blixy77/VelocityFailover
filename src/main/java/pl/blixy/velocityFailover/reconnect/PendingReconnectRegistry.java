package pl.blixy.velocityFailover.reconnect;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PendingReconnectRegistry {

    private final ConcurrentHashMap<UUID, String> pending = new ConcurrentHashMap<>();

    public boolean register(UUID playerId, String serverName) {
        return pending.putIfAbsent(playerId, serverName) == null;
    }

    public void remove(UUID playerId) {
        pending.remove(playerId);
    }

    public List<UUID> getPlayersForServer(String serverName) {
        List<UUID> result = new ArrayList<>();
        for (var entry : pending.entrySet()) {
            if (entry.getValue().equals(serverName)) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    public boolean isPending(UUID playerId) {
        return pending.containsKey(playerId);
    }

    public String getPendingServer(UUID playerId) {
        return pending.get(playerId);
    }
}
