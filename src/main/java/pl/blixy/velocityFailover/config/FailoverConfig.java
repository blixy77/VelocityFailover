package pl.blixy.velocityFailover.config;

import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Everything read from config.yml, with the messages already parsed so no handler parses MiniMessage per player. */
public record FailoverConfig(String limbo, Set<String> servers, Recovery recovery, List<String> shutdownKeywords,
                             Messages messages, ActionBar actionBar, TitleAnimation titleAnimation, Sounds sounds) {

    public record Recovery(Duration pingInterval, int pingsToReady, Duration gracePeriod, Duration transferInterval,
                           Duration pingTimeout) {}

    public record Messages(Component sentToLimbo, Component reconnecting, Notification connectionBlocked) {}

    /** A chat message with an optional title shown at the same point in the failover flow. */
    public record Notification(Component chat, Optional<Title> title) {

        public void send(Player player) {
            player.sendMessage(chat);
            showTitle(player);
        }

        public void showTitle(Player player) {
            title.ifPresent(player::showTitle);
        }
    }

    /** One rendered action bar per spinner frame, cycled every {@code interval}. */
    public record ActionBar(Duration interval, List<Component> frames) {}

    /** Persistent title frames shown while the player waits and while their server is recovering. */
    public record TitleAnimation(Duration interval, Duration connectingDelay,
                                 List<Title> waitingFrames, List<Title> connectingFrames) {}

    /** Optional client-side sounds for the two phases of the reconnect flow. */
    public record Sounds(Optional<Sound> waiting, Optional<Sound> connecting) {}

    /** Whether the player is parked on the limbo server right now. */
    public boolean isLimbo(Player player) {
        return player.getCurrentServer().map(connection -> connection.getServerInfo().getName().equals(limbo)).orElse(false);
    }
}
