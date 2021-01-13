package org.apache.camel.component.stitch.client;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.component.stitch.client.models.StitchMessage;
import org.apache.camel.component.stitch.client.models.StitchRequestBody;
import org.apache.camel.component.stitch.client.models.StitchSchema;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StitchRequestBodyTest {

    @Test
    public void testNormalRequestBodyToJson() throws JsonProcessingException {
        final StitchMessage message1 = StitchMessage.builder()
                .withData("id", 2)
                .withData("name", "Jake")
                .withData("age", 6)
                .withData("has_magic", true)
                .withData("modified_at", "2020-01-13T21:25:03+0000")
                .withSequence(1565881320)
                .build();

        final StitchMessage message2 = StitchMessage.builder()
                .withData("id", 3)
                .withData("name", "Bubblegum")
                .withData("age", 17)
                .withData("has_magic", true)
                .withData("modified_at", "2020-01-14T13:34:25+0000")
                .withSequence(1565838645)
                .build();

        final Map<String, String> modifiedAtSchema = new LinkedHashMap<>();
        modifiedAtSchema.put("type", "string");
        modifiedAtSchema.put("format", "date-time");

        final Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("id", Collections.singletonMap("type", "integer"));
        properties.put("name", Collections.singletonMap("type", "string"));
        properties.put("age", Collections.singletonMap("type", "integer"));
        properties.put("has_magic", Collections.singletonMap("type", "boolean"));
        properties.put("modified_at", modifiedAtSchema);

        final StitchSchema schema = StitchSchema.builder()
                .addKeyword("properties", properties)
                .build();

        final StitchRequestBody body = StitchRequestBody.builder()
                .withTableName("customers")
                .addMessage(message1)
                .addMessage(message2)
                .withSchema(schema)
                .withKeyNames("id")
                .build();

        final String expectedJson = "{\"table_name\":\"customers\",\"schema\":{\"properties\":{\"id\":{\"type\":\"integer\"},\"name\":{\"type\":\"string\"},\"age\":{\"type\":\"integer\"},\""
                + "has_magic\":{\"type\":\"boolean\"},\"modified_at\":{\"type\":\"string\",\"format\":\"date-time\"}}},\"messages\":[{\"action\":\"upsert\",\"sequence\":1565881320,\"data\":"
                + "{\"id\":2,\"name\":\"Jake\",\"age\":6,\"has_magic\":true,\"modified_at\":\"2020-01-13T21:25:03+0000\"}},{\"action\":\"upsert\",\"sequence\":1565838645,\"data\":{\"id\":3,"
                + "\"name\":\"Bubblegum\",\"age\":17,\"has_magic\":true,\"modified_at\":\"2020-01-14T13:34:25+0000\"}}],\"key_names\":[\"id\"]}";

        assertEquals(expectedJson, new ObjectMapper().writeValueAsString(body.toMap()));
    }

}