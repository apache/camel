package org.apache.camel.component.vertx.kafka.serde;

import io.vertx.core.buffer.Buffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultVertxKafkaHeaderSerializer {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultVertxKafkaHeaderSerializer.class);

    public static Buffer serialize(final Object value) {
        if (value instanceof String) {
            return Buffer.buffer((String) value);
        } else if (value instanceof Long) {
            return Buffer.buffer().appendLong((Long) value);
        } else if (value instanceof Integer) {
            return Buffer.buffer().appendInt((Integer) value);
        } else if (value instanceof Double) {
            return Buffer.buffer().appendDouble((Double) value);
        } else if (value instanceof Boolean) {
            return Buffer.buffer(value.toString());
        } else if (value instanceof byte[]) {
            return Buffer.buffer((byte[]) value);
        }
        LOG.debug("Cannot propagate header value of type[{}], skipping... "
                  + "Supported types: String, Integer, Long, Double, byte[].",
                value != null ? value.getClass() : "null");
        return null;
    }

}
