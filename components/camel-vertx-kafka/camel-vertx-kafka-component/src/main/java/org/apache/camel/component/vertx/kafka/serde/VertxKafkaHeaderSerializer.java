/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.vertx.kafka.serde;

import io.vertx.core.buffer.Buffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VertxKafkaHeaderSerializer {

    private static final Logger LOG = LoggerFactory.getLogger(VertxKafkaHeaderSerializer.class);

    private VertxKafkaHeaderSerializer() {
    }

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
        } else if (value instanceof Buffer) {
            return (Buffer) value;
        } else if (value instanceof byte[]) {
            return Buffer.buffer((byte[]) value);
        }
        LOG.debug("Cannot propagate header value of type[{}], skipping... "
                  + "Supported types: String, Integer, Long, Double, byte[].",
                value != null ? value.getClass() : "null");
        return null;
    }

}
