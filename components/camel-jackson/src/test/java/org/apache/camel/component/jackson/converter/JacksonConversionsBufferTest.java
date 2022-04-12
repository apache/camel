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
package org.apache.camel.component.jackson.converter;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jackson.JacksonConstants;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JacksonConversionsBufferTest extends CamelTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        // enable jackson type converter by setting this property on
        // CamelContext
        context.getGlobalOptions().put(JacksonConstants.ENABLE_TYPE_CONVERTER, "true");
        return context;
    }

    @Test
    public void shouldConvertMapToByteBuffer() {
        String name = "someName";
        Map<String, String> pojoAsMap = new HashMap<>();
        pojoAsMap.put("name", name);

        ByteBuffer testByteBuffer = (ByteBuffer) template.requestBody("direct:test", pojoAsMap);

        assertEquals("{\"name\":\"someName\"}", StandardCharsets.UTF_8.decode(testByteBuffer).toString());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:test").convertBodyTo(ByteBuffer.class);
            }
        };
    }

}
