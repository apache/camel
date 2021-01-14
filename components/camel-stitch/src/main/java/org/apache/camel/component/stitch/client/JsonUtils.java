package org.apache.camel.component.stitch.client;

import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class JsonUtils {

    final static ObjectMapper mapper = new ObjectMapper();

    private JsonUtils() {
    }

    public static String convertMapToJson(final Map<String, Object> inputMap) {
        try {
            return mapper.writeValueAsString(inputMap);
        } catch (JsonProcessingException exception) {
            throw new RuntimeException("Error occurred writing data map to JSON.", exception);
        }
    }

    public static Map<String, Object> convertJsonToMap(final String jsonString) {
        try {
            return mapper.readValue(jsonString, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException exception) {
        throw new RuntimeException("Error occurred writing JSON to Map.", exception);
    }
    }
}
