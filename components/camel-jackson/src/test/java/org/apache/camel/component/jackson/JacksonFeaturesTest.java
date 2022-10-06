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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

public class JacksonFeaturesTest extends CamelTestSupport {

    @Test
    public void testEnableDeserializationFeature() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.message(0).body().isNull();

        template.send("direct:format", exchange -> exchange.getIn().setBody("[]"));

        mock.expectedMessageCount(1);

        mock.assertIsSatisfied();
    }

    @Test
    public void testEnableMapperFeature() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.message(0).body().isInstanceOf(TestPojo.class);

        template.send("direct:format", exchange -> exchange.getIn().setBody("{\"nAmE\": \"test\"}"));

        mock.expectedMessageCount(1);
        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            @Override
            public void configure() {
                JacksonDataFormat format = new JacksonDataFormat(TestPojo.class);
                format.enableFeature(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT);
                format.enableFeature(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES);
                format.disableFeature(SerializationFeature.INDENT_OUTPUT);
                format.disableFeature(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
                format.disableFeature(MapperFeature.APPLY_DEFAULT_VALUES);

                from("direct:format").unmarshal(format).to("mock:result");
            }
        };
    }
}
