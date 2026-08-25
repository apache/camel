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
package org.apache.camel.component.netty;

import java.io.InvalidClassException;
import java.net.URI;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.serialization.ClassResolvers;
import org.apache.camel.component.netty.codec.ObjectDecoder;
import org.apache.camel.component.netty.codec.ObjectEncoder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that {@link ObjectDecoder} always installs a JEP-290 {@code ObjectInputFilter} resolved through
 * {@code DeserializationFilterHelper}: an unset pattern now falls back to the shared Camel default allow-list instead
 * of applying no filter at all.
 */
class ObjectDecoderDeserializationFilterTest {

    @Test
    void defaultFilterAllowsStandardType() {
        assertThat(decode(encode("hello netty"), null)).isEqualTo("hello netty");
    }

    @Test
    void singleArgConstructorRejectsClassOutsideDefaultAllowList() {
        ByteBuf frame = encode(URI.create("http://example.com/"));
        EmbeddedChannel channel = new EmbeddedChannel(new ObjectDecoder(ClassResolvers.weakCachingResolver(null)));
        assertThatThrownBy(() -> channel.writeInbound(frame))
                .isInstanceOf(DecoderException.class)
                .hasRootCauseInstanceOf(InvalidClassException.class);
        channel.finishAndReleaseAll();
    }

    @Test
    void defaultFilterRejectsClassOutsideAllowList() {
        ByteBuf frame = encode(URI.create("http://example.com/"));
        EmbeddedChannel channel
                = new EmbeddedChannel(new ObjectDecoder(ClassResolvers.weakCachingResolver(null), null));
        assertThatThrownBy(() -> channel.writeInbound(frame))
                .isInstanceOf(DecoderException.class)
                .hasRootCauseInstanceOf(InvalidClassException.class);
        channel.finishAndReleaseAll();
    }

    @Test
    void explicitFilterCanAllowOtherwiseDeniedClass() {
        Object result = decode(encode(URI.create("http://example.com/")), "java.**;!*");
        assertThat(result).isInstanceOf(URI.class);
        assertThat(result).hasToString("http://example.com/");
    }

    private static ByteBuf encode(Object value) {
        EmbeddedChannel channel = new EmbeddedChannel(new ObjectEncoder());
        assertThat(channel.writeOutbound(value)).isTrue();
        ByteBuf frame = channel.readOutbound();
        channel.finish();
        return frame;
    }

    private static Object decode(ByteBuf frame, String deserializationFilter) {
        EmbeddedChannel channel = new EmbeddedChannel(
                new ObjectDecoder(ClassResolvers.weakCachingResolver(null), deserializationFilter));
        assertThat(channel.writeInbound(frame)).isTrue();
        Object result = channel.readInbound();
        channel.finish();
        return result;
    }
}
