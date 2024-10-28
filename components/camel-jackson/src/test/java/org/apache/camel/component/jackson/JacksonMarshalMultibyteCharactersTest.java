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
package org.apache.camel.component.jackson;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class JacksonMarshalMultibyteCharactersTest extends CamelTestSupport {

    /**
     * Tests marshalling 4-byte characters by Jackson. Jackson, by default, marshals 4-byte characters as a pair of
     * UTF-16 escapes - a high and low surrogate, escaped. Since Jackson 2.18, there is an option to combine the
     * surrogates into one character.
     * <p>
     * This test verifies the latter behavior since it's expected that Camel users working with languages such as
     * Japanese or Chinese would prefer having the characters combined.
     */
    @Test
    public void testMarshal4ByteCharacter() throws Exception {
        Map<String, Object> in = new HashMap<>();
        in.put("name", "\u30b7\u30b9\u30c6\u30e0\uD867\uDE3D");

        MockEndpoint mock = getMockEndpoint("mock:reverse");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(Map.class);
        mock.message(0).body().isEqualTo(in);

        Object marshalled = template.requestBody("direct:in", in);
        String marshalledAsString = context.getTypeConverter().convertTo(String.class, marshalled);

        // just for clarity we test that escaped surrogate pair is no longer returned
        assertNotEquals("{\"name\":\"\u30b7\u30b9\u30c6\u30e0\\uD867\\uDE3D\"}", marshalledAsString);

        assertEquals("{\"name\":\"\u30b7\u30b9\u30c6\u30e0\uD867\uDE3D\"}", marshalledAsString);

        template.sendBody("direct:back", marshalled);
        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            @Override
            public void configure() {
                JacksonDataFormat format = new JacksonDataFormat();
                format.setCombineUnicodeSurrogates(true);
                from("direct:in").marshal(format).log("marshal: ${body}");
                from("direct:back").unmarshal(format).log("unmarshal: ${body}").to("mock:reverse");
            }
        };
    }
}
