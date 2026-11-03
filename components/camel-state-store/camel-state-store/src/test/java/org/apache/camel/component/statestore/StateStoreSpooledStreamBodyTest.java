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
package org.apache.camel.component.statestore;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.converter.stream.FileInputStreamCache;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StateStoreSpooledStreamBodyTest extends CamelTestSupport {

    private static final byte[] CONTENT = "a stream that is spooled to a file".getBytes(StandardCharsets.UTF_8);

    // the body that the producer of the put gets
    private volatile Object bodyToStore;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        // spool every stream of more than one byte to a file, which is deleted when its exchange completes
        context.getStreamCachingStrategy().setSpoolEnabled(true);
        context.getStreamCachingStrategy().setSpoolThreshold(1);
        return context;
    }

    @Test
    void testPutSpooledStreamStoresItsBytes() {
        // stream caching keeps a ByteArrayInputStream in memory whatever its size, so it is wrapped
        template.requestBodyAndHeaders("direct:put", new BufferedInputStream(new ByteArrayInputStream(CONTENT)),
                Map.of(StateStoreConstants.KEY, "spooled"));
        assertThat(bodyToStore).isInstanceOf(FileInputStreamCache.class);

        // the file is gone now that the put has completed, but the bytes were stored
        for (int i = 0; i < 2; i++) {
            Object value = template.requestBodyAndHeaders("direct:get", null, Map.of(StateStoreConstants.KEY, "spooled"));
            assertThat(value).isInstanceOf(byte[].class);
            assertThat((byte[]) value).containsExactly(CONTENT);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:put").streamCaching()
                        .process(exchange -> bodyToStore = exchange.getMessage().getBody())
                        .to("state-store:spooledStore?operation=put");
                from("direct:get").to("state-store:spooledStore?operation=get");
            }
        };
    }
}
