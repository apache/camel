// https://github.com/apache/camel/blob/main/components/camel-telegram/src/main/java/org/apache/camel/component/telegram/model/UnixTimestampSerializer.java

package org.apache.camel.component.clickup;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.time.Instant;

/**
 * A serializer for {@link java.time.Instant} compatible with {@link UnixTimestampDeserializer}.
 */
public class UnixTimestampSerializer extends JsonSerializer<Instant> {

    @Override
    public void serialize(Instant value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeNumber(value.toEpochMilli());
    }

}