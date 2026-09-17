package rescuenet.common;

import java.util.LinkedHashMap;
import java.util.Map;

public final class CliArgs {
    private final Map<String, String> values = new LinkedHashMap<>();

    private CliArgs() {
    }

    public static CliArgs parse(String[] args) {
        CliArgs parsed = new CliArgs();
        for (int i = 0; i < args.length; i++) {
            String token = args[i];
            if (!token.startsWith("--")) {
                throw new IllegalArgumentException("Argumento inesperado: " + token);
            }
            String key = token.substring(2);
            if (i + 1 >= args.length || args[i + 1].startsWith("--")) {
                parsed.values.put(key, "true");
            } else {
                parsed.values.put(key, args[++i]);
            }
        }
        return parsed;
    }

    public String req(String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Falta o argumento obrigatório --" + key);
        }
        return value;
    }

    public String opt(String key, String fallback) {
        String value = values.get(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    public int optInt(String key, int fallback) {
        return values.containsKey(key) ? Integer.parseInt(req(key)) : fallback;
    }

    public double optDouble(String key, double fallback) {
        return values.containsKey(key) ? Double.parseDouble(req(key)) : fallback;
    }

    public double reqDouble(String key) {
        return Double.parseDouble(req(key));
    }
}
