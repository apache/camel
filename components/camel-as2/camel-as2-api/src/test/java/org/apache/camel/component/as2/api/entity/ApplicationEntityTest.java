package org.apache.camel.component.as2.api.entity;


import org.apache.hc.core5.http.ContentType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ApplicationEntityTest {

    @Test
    void checkWriteToWithMultiByteCharacter() throws IOException {
        String message = "Test message with special char ó";

        ApplicationEntity applicationEntity = new ApplicationEntity(message, ContentType.TEXT_PLAIN, "binary", true, null) {
            @Override
            public void close() throws IOException {
            }
        };
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        applicationEntity.writeTo(outputStream);

        byte[] expectedBytes = message.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = outputStream.toByteArray();

        assertArrayEquals(expectedBytes, actualBytes, "The output bytes should match the expected UTF-8 encoded bytes.");
        assertEquals(expectedBytes.length, actualBytes.length, "The byte length should match the length of the UTF-8 encoded message.");
    }
}
