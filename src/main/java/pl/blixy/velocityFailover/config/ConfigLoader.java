package pl.blixy.velocityFailover.config;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Copies the bundled config.yml on first start and reads it into a {@link FailoverConfig}; a missing key keeps its default. */
public final class ConfigLoader {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final List<TitleFrame> DEFAULT_WAITING_TITLES = List.of(
            new TitleFrame("<red><bold>Server unavailable.</bold>", "<gray>Please wait..."),
            new TitleFrame("<red><bold>Server unavailable..</bold>", "<gray>Please wait..."),
            new TitleFrame("<red><bold>Server unavailable...</bold>", "<gray>Please wait..."));
    private static final List<TitleFrame> DEFAULT_CONNECTING_TITLES = List.of(
            new TitleFrame("<green><bold>Reconnecting.</bold>", "<gray>Please wait..."),
            new TitleFrame("<green><bold>Reconnecting..</bold>", "<gray>Please wait..."),
            new TitleFrame("<green><bold>Reconnecting...</bold>", "<gray>Please wait..."));

    private ConfigLoader() {}

    public static FailoverConfig load(Path dataDirectory) throws IOException {
        Path file = dataDirectory.resolve("config.yml");
        if (!Files.exists(file)) {
            Files.createDirectories(dataDirectory);
            try (InputStream bundled = ConfigLoader.class.getClassLoader().getResourceAsStream("config.yml")) {
                if (bundled != null) {
                    Files.copy(bundled, file);
                }
            }
        }

        try (InputStream in = Files.newInputStream(file)) {
            return read(new Section(new Yaml().load(in)));
        }
    }

    private static FailoverConfig read(Section root) {
        Set<String> servers = new HashSet<>();
        for (Object group : root.section("groups").values().values()) {
            servers.addAll(new Section(group).strings("servers", List.of()));
        }

        Section recovery = root.section("recovery");
        Section messages = root.section("messages");
        Section titles = root.section("titles");
        Section sounds = root.section("sounds");
        Section actionBar = root.section("action-bar");
        String waiting = messages.string("waiting-action-bar", "<yellow>Connecting to the server <gray>{spinner}");
        List<Component> frames = waiting.isBlank()
                ? List.of()
                : actionBar.strings("spinner-frames", List.of("[|]", "[/]", "[-]", "[\\]")).stream()
                        .map(frame -> MINI_MESSAGE.deserialize(waiting.replace("{spinner}", frame)))
                        .toList();
        Title.Times notificationTitleTimes = Title.Times.times(
                titles.millis("fade-in-ms", 300),
                titles.millis("stay-ms", 2500),
                titles.millis("fade-out-ms", 500));
        Title.Times animationTitleTimes = Title.Times.times(
                Duration.ZERO,
                titles.millis("animation-stay-ms", 30000),
                Duration.ZERO);
        List<Title> waitingTitles = titleFrames(
                titles, "waiting", "sent-to-limbo", DEFAULT_WAITING_TITLES, animationTitleTimes);
        List<Title> connectingTitles = titleFrames(
                titles, "connecting", "reconnecting", DEFAULT_CONNECTING_TITLES, animationTitleTimes);

        return new FailoverConfig(
                root.string("limbo-server", "limbo"),
                Set.copyOf(servers),
                new FailoverConfig.Recovery(
                        recovery.millis("ping-interval-ms", 2000),
                        recovery.integer("pings-to-ready", 3),
                        recovery.millis("grace-period-ms", 5000),
                        recovery.millis("transfer-interval-ms", 50),
                        recovery.millis("ping-timeout-ms", 2000)),
                root.strings("shutdown-keywords", List.of("Server closed", "Server shutting down")),
                new FailoverConfig.Messages(
                        messages.component("sent-to-limbo", "<red>The server is temporarily unavailable. You will be moved back automatically when it returns."),
                        messages.component("reconnecting", "<green>The server is back online! Reconnecting..."),
                        notification(messages, titles, "connection-blocked",
                                "<red>This server is currently unavailable. Please try again in a moment.",
                                "<red><bold>Server unavailable</bold>", "<gray>Please try again in a moment", notificationTitleTimes)),
                new FailoverConfig.ActionBar(actionBar.millis("interval-ms", 400), frames),
                new FailoverConfig.TitleAnimation(
                        titles.millis("interval-ms", 1000),
                        titles.millis("connecting-delay-ms", 2000),
                        waitingTitles,
                        connectingTitles),
                new FailoverConfig.Sounds(
                        sound(sounds.section("waiting"), "minecraft:entity.experience_orb.pickup", 0.5f, 1.0f),
                        sound(sounds.section("connecting"), "minecraft:entity.player.levelup", 1.0f, 1.0f)));
    }

    private static Optional<Sound> sound(Section section, String fallbackName, float fallbackVolume, float fallbackPitch) {
        String name = section.string("name", fallbackName);
        if (name.isBlank()) {
            return Optional.empty();
        }

        Sound.Source source;
        try {
            source = Sound.Source.valueOf(section.string("source", "master").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            source = Sound.Source.MASTER;
        }
        return Optional.of(Sound.sound(
                Key.key(name), source,
                section.decimal("volume", fallbackVolume),
                section.decimal("pitch", fallbackPitch)));
    }

    private static List<Title> titleFrames(Section titles, String key, String legacyKey,
                                           List<TitleFrame> fallbacks, Title.Times times) {
        Object configured = titles.value(key);
        if (configured != null) {
            return parseTitleFrames(configured, times);
        }

        Object legacy = titles.value(legacyKey);
        if (legacy != null) {
            return parseTitleFrames(legacy, times);
        }

        return fallbacks.stream().map(frame -> title(frame.title(), frame.subtitle(), times)).toList();
    }

    private static List<Title> parseTitleFrames(Object configured, Title.Times times) {
        if (configured instanceof List<?> list) {
            return list.stream().map(Section::new).map(section -> title(section, times)).flatMap(Optional::stream).toList();
        }

        return title(new Section(configured), times).stream().toList();
    }

    private static Optional<Title> title(Section section, Title.Times times) {
        String heading = section.string("title", "");
        String subtitle = section.string("subtitle", "<gray>Please wait...");
        if (heading.isBlank() && subtitle.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(title(heading, subtitle, times));
    }

    private static Title title(String heading, String subtitle, Title.Times times) {
        return Title.title(MINI_MESSAGE.deserialize(heading), MINI_MESSAGE.deserialize(subtitle), times);
    }

    private static FailoverConfig.Notification notification(Section messages, Section titles, String key,
                                                            String fallback, String fallbackTitle,
                                                            String fallbackSubtitle, Title.Times times) {
        Section configuredTitle = titles.section(key);
        String heading = configuredTitle.string("title", fallbackTitle);
        String subtitle = configuredTitle.string("subtitle", fallbackSubtitle);
        Optional<Title> title = heading.isBlank() && subtitle.isBlank()
                ? Optional.empty()
                : Optional.of(title(heading, subtitle, times));
        return new FailoverConfig.Notification(messages.component(key, fallback), title);
    }

    private record TitleFrame(String title, String subtitle) {}

    /** One mapping of the YAML tree; anything that is not a mapping reads as empty. */
    private record Section(Map<String, Object> values) {

        @SuppressWarnings("unchecked")
        Section(Object node) {
            this(node instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of());
        }

        Section section(String key) {
            return new Section(values.get(key));
        }

        Object value(String key) {
            return values.get(key);
        }

        String string(String key, String fallback) {
            return values.get(key) instanceof String value ? value : fallback;
        }

        @SuppressWarnings("unchecked")
        List<String> strings(String key, List<String> fallback) {
            return values.get(key) instanceof List<?> list ? (List<String>) list : fallback;
        }

        int integer(String key, int fallback) {
            return values.get(key) instanceof Number value ? value.intValue() : fallback;
        }

        float decimal(String key, float fallback) {
            return values.get(key) instanceof Number value ? value.floatValue() : fallback;
        }

        Duration millis(String key, long fallback) {
            return Duration.ofMillis(values.get(key) instanceof Number value ? value.longValue() : fallback);
        }

        Component component(String key, String fallback) {
            return MINI_MESSAGE.deserialize(string(key, fallback));
        }
    }
}
