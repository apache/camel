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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class JacksonMarshalUnmarshalArrayTest extends CamelTestSupport {

    @Test
    public void testUnmarshalArray() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:endArray");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(String[].class);

        String json = "[\"Camel\", \"World\"]";
        template.sendBody("direct:beginArray", json);

        MockEndpoint.assertIsSatisfied(context);

        String[] array = mock.getReceivedExchanges().get(0).getIn().getBody(String[].class);
        assertNotNull(array);
        assertEquals(2, array.length);

        String string = array[0];
        assertEquals("Camel", string);
        string = array[1];
        assertEquals("World", string);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:beginArray").unmarshal().json(JsonLibrary.Jackson, String[].class).to("mock:endArray");
            }
        };
    }

}
