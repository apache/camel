// https://github.com/apache/camel/blob/main/components/camel-telegram/src/main/java/org/apache/camel/component/telegram/model/UnixTimestampDeserializer.java

package org.apache.camel.component.clickup;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;

/**
 * A deserializer for a unix timestamp expressed in milliseconds.
 */
public class UnixTimestampDeserializer extends JsonDeserializer<Instant> {

    private Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public Instant deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
        try {
            long unixTimestamp = Long.parseLong(jsonParser.getText());

            return Instant.ofEpochMilli(unixTimestamp);
        } catch (Exception e) {
            log.warn("Unable to deserialize the unix timestamp {}", jsonParser.getText(), e);
            return null;
        }
    }
}
