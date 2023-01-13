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
package org.apache.camel.component.jsonb;

import java.math.BigDecimal;

import jakarta.json.bind.config.PropertyOrderStrategy;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JsonbAttributeOrderTest extends CamelTestSupport {

    final String expectedJson
            = "{\"bg\":123.123,\"bool\":true,\"doubleNumber\":123.123,\"floatNumber\":123.0,\"intNumber\":123,\"longNumber\":123}";

    @Test
    public void testMarshalAndUnmarshalMap() throws Exception {
        NumberPojo nc = new NumberPojo();
        nc.setBg(new BigDecimal("123.123"));
        nc.setDoubleNumber(123.123);
        nc.setBool(true);
        nc.setFloatNumber(123);
        nc.setLongNumber(123L);
        nc.setIntNumber(123);

        MockEndpoint mock = getMockEndpoint("mock:reverse");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(NumberPojo.class);
        mock.message(0).body().isEqualTo(nc);

        Object marshalled = template.requestBody("direct:in", nc);
        String marshalledAsString = context.getTypeConverter().convertTo(String.class, marshalled);
        assertEquals(expectedJson, marshalledAsString);

        template.sendBody("direct:back", marshalled);

        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            @Override
            public void configure() {
                JsonbDataFormat format = new JsonbDataFormat(NumberPojo.class);
                format.setPropertyOrder(PropertyOrderStrategy.LEXICOGRAPHICAL);

                from("direct:in").marshal(format);
                from("direct:back").unmarshal(format).to("mock:reverse");
            }
        };
    }

}
