package org.apache.camel.component.as2.api.entity;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class TextPlainEntityTest {


    @Test
    void test_assert_content_marshalling() throws IOException {
        TextPlainEntity textPlainEntity =
                new TextPlainEntity(
                        "<root>\n  <item/>\n</root>\n",
                        StandardCharsets.US_ASCII.name(),
                        "binary",
                        false
                );
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        textPlainEntity.writeTo(out);

        Assertions.assertEquals(
                out.toString(StandardCharsets.US_ASCII),
                "Content-Type: text/plain; charset=US-ASCII\r\n" +
                        "Content-Transfer-Encoding: binary\r\n" +
                        "\r\n" +
                        textPlainEntity.getText());
    }
}
