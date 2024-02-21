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
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class JacksonMarshalUnmarshalPojoArrayTest extends CamelTestSupport {

    @Test
    public void testUnmarshalPojoArray() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:endArray");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(Pojo[].class);

        String json = "[{\"text\":\"Camel\"}, {\"text\":\"World\"}]";
        template.sendBody("direct:beginArray", json);

        MockEndpoint.assertIsSatisfied(context);

        Pojo[] array = mock.getReceivedExchanges().get(0).getIn().getBody(Pojo[].class);
        assertNotNull(array);
        assertEquals(2, array.length);

        Pojo pojo = array[0];
        assertEquals("Camel", pojo.getText());
        pojo = array[1];
        assertEquals("World", pojo.getText());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:beginArray").unmarshal().json(Pojo[].class).to("mock:endArray");
            }
        };
    }

    public static class Pojo {
        private String text;

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }

}
