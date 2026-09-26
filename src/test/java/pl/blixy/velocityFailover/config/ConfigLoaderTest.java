package pl.blixy.velocityFailover.config;

import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLoaderTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @TempDir
    Path directory;

    @Test
    void addsDefaultTitlesToExistingConfigs() throws IOException {
        writeConfig("""
                messages:
                  sent-to-limbo: "Waiting"
                  reconnecting: "Returning"
                  connection-blocked: "Offline"
                """);

        FailoverConfig config = ConfigLoader.load(directory);

        assertEquals("Waiting", PLAIN.serialize(config.messages().sentToLimbo()));
        assertEquals(3, config.titleAnimation().waitingFrames().size());
        assertEquals(3, config.titleAnimation().connectingFrames().size());
        assertEquals("Server unavailable.", PLAIN.serialize(config.titleAnimation().waitingFrames().getFirst().title()));
        assertEquals("Reconnecting.", PLAIN.serialize(config.titleAnimation().connectingFrames().getFirst().title()));
        assertEquals("Server unavailable", PLAIN.serialize(config.messages().connectionBlocked().title().orElseThrow().title()));
        assertEquals("minecraft:entity.experience_orb.pickup", config.sounds().waiting().orElseThrow().name().asString());
        assertEquals("minecraft:entity.player.levelup", config.sounds().connecting().orElseThrow().name().asString());
    }

    @Test
    void allowsDisablingADefaultTitleAndActionBar() throws IOException {
        writeConfig("""
                messages:
                  waiting-action-bar: ""
                sounds:
                  waiting:
                    name: ""
                titles:
                  sent-to-limbo:
                    title: ""
                    subtitle: ""
                """);

        FailoverConfig config = ConfigLoader.load(directory);

        assertTrue(config.titleAnimation().waitingFrames().isEmpty());
        assertEquals(3, config.titleAnimation().connectingFrames().size());
        assertTrue(config.actionBar().frames().isEmpty());
        assertTrue(config.sounds().waiting().isEmpty());
    }

    @Test
    void parsesTitlesAndTheirTimings() throws IOException {
        writeConfig("""
                titles:
                  interval-ms: 750
                  connecting-delay-ms: 2250
                  animation-stay-ms: 10000
                  fade-in-ms: 150
                  stay-ms: 1800
                  fade-out-ms: 350
                  waiting:
                    - title: "<red>Unavailable."
                      subtitle: "Please wait"
                    - title: "<red>Unavailable.."
                      subtitle: "Please wait"
                  connecting:
                    - title: "<green>Connecting"
                      subtitle: "Almost ready"
                  connection-blocked:
                    title: "<red>Blocked"
                    subtitle: "Try later"
                sounds:
                  waiting:
                    name: "minecraft:block.note_block.pling"
                    source: "player"
                    volume: 0.25
                    pitch: 1.5
                """);

        FailoverConfig config = ConfigLoader.load(directory);
        Title waitingTitle = config.titleAnimation().waitingFrames().getFirst();
        Title.Times animationTimes = waitingTitle.times();
        Title blockedTitle = config.messages().connectionBlocked().title().orElseThrow();
        Title.Times notificationTimes = blockedTitle.times();

        assertEquals(Duration.ofMillis(750), config.titleAnimation().interval());
        assertEquals(Duration.ofMillis(2250), config.titleAnimation().connectingDelay());
        assertEquals(2, config.titleAnimation().waitingFrames().size());
        assertEquals(1, config.titleAnimation().connectingFrames().size());
        assertEquals("Unavailable.", PLAIN.serialize(waitingTitle.title()));
        assertEquals("Please wait", PLAIN.serialize(waitingTitle.subtitle()));
        assertNotNull(animationTimes);
        assertEquals(Duration.ZERO, animationTimes.fadeIn());
        assertEquals(Duration.ofMillis(10000), animationTimes.stay());
        assertEquals(Duration.ZERO, animationTimes.fadeOut());
        assertNotNull(notificationTimes);
        assertEquals(Duration.ofMillis(150), notificationTimes.fadeIn());
        assertEquals(Duration.ofMillis(1800), notificationTimes.stay());
        assertEquals(Duration.ofMillis(350), notificationTimes.fadeOut());
        assertEquals("minecraft:block.note_block.pling", config.sounds().waiting().orElseThrow().name().asString());
        assertEquals(Sound.Source.PLAYER, config.sounds().waiting().orElseThrow().source());
        assertEquals(0.25f, config.sounds().waiting().orElseThrow().volume());
        assertEquals(1.5f, config.sounds().waiting().orElseThrow().pitch());
    }

    private void writeConfig(String yaml) throws IOException {
        Files.writeString(directory.resolve("config.yml"), yaml);
    }
}
