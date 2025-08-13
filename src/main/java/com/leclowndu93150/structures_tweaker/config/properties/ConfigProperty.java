package com.leclowndu93150.structures_tweaker.config.properties;

import java.util.List;
import java.util.function.Function;

public class ConfigProperty<T> {
    private final String key;
    private final T defaultValue;
    private final Class<T> type;
    private final String description;
    private final Function<Object, T> converter;

    public ConfigProperty(String key, T defaultValue, Class<T> type, String description) {
        this.key = key;
        this.defaultValue = defaultValue;
        this.type = type;
        this.description = description;
        this.converter = createConverter(type);
    }

    @SuppressWarnings("unchecked")
    private Function<Object, T> createConverter(Class<T> type) {
        if (type == Boolean.class) {
            return obj -> {
                if (obj instanceof Boolean) return (T) obj;
                if (obj instanceof String) return (T) Boolean.valueOf((String) obj);
                return defaultValue;
            };
        } else if (type == Integer.class) {
            return obj -> {
                if (obj instanceof Number) return (T) Integer.valueOf(((Number) obj).intValue());
                if (obj instanceof String) return (T) Integer.valueOf((String) obj);
                return defaultValue;
            };
        } else if (type == String.class) {
            return obj -> (T) String.valueOf(obj);
        } else if (type == List.class) {
            return obj -> {
                if (obj instanceof List) return (T) obj;
                return defaultValue;
            };
        }
        return obj -> (T) obj;
    }

    public String getKey() { return key; }
    public T getDefaultValue() { return defaultValue; }
    public Class<T> getType() { return type; }
    public String getDescription() { return description; }
    public T convert(Object value) { return converter.apply(value); }
}