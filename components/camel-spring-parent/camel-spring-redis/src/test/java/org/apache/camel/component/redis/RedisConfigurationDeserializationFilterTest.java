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
package org.apache.camel.component.redis;

import java.io.InvalidClassException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The default serializer must not materialise arbitrary types. Redis payloads reach {@code RedisConsumer.setBody()} and
 * the producer read commands straight from the wire, so the deserialization filter is what stands between them and
 * {@code readObject()}.
 */
class RedisConfigurationDeserializationFilterTest {

    @SuppressWarnings("unchecked")
    private static RedisSerializer<Object> serializerOf(RedisConfiguration configuration) {
        return (RedisSerializer<Object>) configuration.getSerializer();
    }

    @Test
    void allowedTypeRoundTrips() {
        RedisSerializer<Object> serializer = serializerOf(new RedisConfiguration());
        List<String> body = new ArrayList<>(List.of("a", "b"));

        assertThat(serializer.deserialize(serializer.serialize(body))).isEqualTo(body);
    }

    @Test
    void deniedJavaNetTypeIsRejected() {
        RedisSerializer<Object> serializer = serializerOf(new RedisConfiguration());
        byte[] payload = serializer.serialize(URI.create("http://localhost/"));

        assertThatThrownBy(() -> serializer.deserialize(payload))
                .isInstanceOf(SerializationException.class)
                .hasRootCauseInstanceOf(InvalidClassException.class);
    }

    @Test
    void typeOutsideTheAllowListIsRejected() {
        RedisSerializer<Object> serializer = serializerOf(new RedisConfiguration());
        // a Serializable type that is not java.*, javax.* or org.apache.camel.*, so the trailing !* denies it
        byte[] payload = serializer.serialize(new SerializationException("outside the default allow-list"));

        assertThatThrownBy(() -> serializer.deserialize(payload))
                .isInstanceOf(SerializationException.class)
                .hasRootCauseInstanceOf(InvalidClassException.class);
    }

    @Test
    void configuredFilterOverridesTheDefault() {
        RedisConfiguration configuration = new RedisConfiguration();
        configuration.setDeserializationFilter("org.springframework.**;java.**;!*");
        RedisSerializer<Object> serializer = serializerOf(configuration);
        SerializationException outsideDefaultAllowList = new SerializationException("allowed by the custom filter");

        assertThat(serializer.deserialize(serializer.serialize(outsideDefaultAllowList)))
                .isInstanceOf(SerializationException.class);
    }

    @Test
    void customSerializerIsLeftAlone() {
        RedisConfiguration configuration = new RedisConfiguration();
        RedisSerializer<String> custom = RedisSerializer.string();
        configuration.setSerializer(custom);

        assertThat(configuration.getSerializer()).isSameAs(custom);
    }
}
