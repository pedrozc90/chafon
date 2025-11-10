package com.contare.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class ConfigLoader {

    private static final ObjectMapper mapper = createObjectMapper();

    private ConfigLoader() {
    }

    private static ObjectMapper createObjectMapper() {
        final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.findAndRegisterModules();
        return mapper;
    }

    public static Config load(final String[] args) throws Exception {
        final Optional<String> cli = findCliConfigArg(args);
        final String path = cli.orElseGet(() -> System.getProperty("config"));

        // If external config path provided -> load and return it (no merging)
        if (path != null && !path.isBlank()) {
            Path p = Path.of(path);
            if (!Files.exists(p)) {
                throw new IllegalArgumentException("Config file not found: " + path);
            }
            try (InputStream in = Files.newInputStream(p)) {
                return mapper.readValue(in, Config.class);
            }
        }

        // No external config: load packaged application.yml from resources
        final Thread thread = Thread.currentThread();
        final ClassLoader loader = thread.getContextClassLoader();
        try (InputStream in = loader.getResourceAsStream("application.yml")) {
            if (in == null) {
                throw new IllegalStateException("No config provided (no --config and no -Dconfig) and no application.yml found on classpath");
            }
            return mapper.readValue(in, Config.class);
        }
    }

    private static Optional<String> findCliConfigArg(final String[] args) {
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("--config=")) {
                return Optional.of(a.substring("--config=".length()));
            }
            if (a.equals("--config") && i + 1 < args.length) {
                return Optional.of(args[i + 1]);
            }
            if (a.startsWith("-c=")) {
                return Optional.of(a.substring("-c=".length()));
            }
            if (a.equals("-c") && i + 1 < args.length) {
                return Optional.of(args[i + 1]);
            }
        }
        return Optional.empty();
    }

    public static String toString(final Config obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

}
