package pl.blixy.velocityFailover.config;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class ConfigLoader {

    public static FailoverConfig load(Path dataDirectory) throws IOException {
        Path configFile = dataDirectory.resolve("config.yml");

        if (!Files.exists(configFile)) {
            Files.createDirectories(dataDirectory);
            try (InputStream defaultConfig = ConfigLoader.class.getClassLoader().getResourceAsStream("config.yml")) {
                if (defaultConfig != null) {
                    Files.copy(defaultConfig, configFile);
                }
            }
        }

        Yaml yaml = new Yaml();
        try (InputStream in = Files.newInputStream(configFile)) {
            Map<String, Object> data = yaml.load(in);
            return new FailoverConfig(data);
        }
    }
}
