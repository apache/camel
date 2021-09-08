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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jackson.JacksonConstants;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JacksonConversionsAdvancedTest extends CamelTestSupport {

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
        ByteBuffer testByteBuffer = (ByteBuffer) template.requestBody("direct:bytebuffer", pojoAsMap);

        assertEquals("{\"name\":\"someName\"}", StandardCharsets.UTF_8.decode(testByteBuffer).toString());
    }

    @Test
    public void shouldConvertMapToInputStream() {
        String name = "someName";
        Map<String, String> pojoAsMap = new HashMap<>();
        pojoAsMap.put("name", name);
        InputStream testInputStream = (InputStream) template.requestBody("direct:inputstream", pojoAsMap);

        String text = new BufferedReader(
                new InputStreamReader(testInputStream, StandardCharsets.UTF_8))
                        .lines()
                        .collect(Collectors.joining("\n"));

        assertEquals("{\"name\":\"someName\"}", text);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                from("direct:bytebuffer").convertBodyTo(ByteBuffer.class);
                from("direct:inputstream").convertBodyTo(InputStream.class);
            }
        };
    }

}
