package org.apache.camel.component.stitch.client;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.camel.component.stitch.client.models.StitchException;
import org.apache.camel.component.stitch.client.models.StitchMessage;
import org.apache.camel.component.stitch.client.models.StitchRequestBody;
import org.apache.camel.component.stitch.client.models.StitchResponse;
import org.apache.camel.component.stitch.client.models.StitchSchema;
import org.junit.jupiter.api.Test;
import reactor.netty.http.client.HttpClient;

import static org.junit.jupiter.api.Assertions.*;

class StitchClientImplTest {

    @Test
    void initialTest() throws JsonProcessingException {
        final StitchClient client = new StitchClientImpl(HttpClient.create(), "https://api.eu-central-1.stitchdata.com", "36eb3b936e2cb9faba618ccbaa8f8a386d747db8e4201432080a42f762756eb7");

        final StitchMessage message1 = StitchMessage.builder()
                .withData("id", 2)
                .withData("name", "Jake")
                .withData("age", 6)
                .withData("has_magic", true)
                .withSequence(1565881320)
                .build();

        final Map<String, String> modifiedAtSchema = new LinkedHashMap<>();
        modifiedAtSchema.put("type", "string");
        modifiedAtSchema.put("format", "date-time");

        final Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("id", Collections.singletonMap("type", "integer"));
        properties.put("name", Collections.singletonMap("type", "string"));
        properties.put("age", Collections.singletonMap("type", "integer"));
        properties.put("has_magic", Collections.singletonMap("type", "boolean"));

        final StitchSchema schema = StitchSchema.builder()
                .addKeyword("properties", properties)
                .build();

        final StitchRequestBody body = StitchRequestBody.builder()
                .withTableName("test")
                .addMessage(message1)
                .withSchema(schema)
                .withKeyNames("id")
                .build();
        try {
            StitchResponse response = client.batch(body).block();
            System.out.println();
        } catch (Exception ex) {
            System.out.println();
        }
    }
}