/**
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
package org.apache.camel.impl.validator;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.TypeConverters;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class ValidatorContractTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testInputTypeOnly() throws Exception {
        context.getTypeConverterRegistry().addTypeConverters(new MyTypeConverters());
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                validator()
                    .type(A.class)
                    .withUri("direct:validator");
                from("direct:a")
                    .inputTypeWithValidate(A.class)
                    .to("mock:a");
                from("direct:validator")
                    .to("mock:validator");
            }
        });
        context.start();
        
        MockEndpoint mocka = context.getEndpoint("mock:a", MockEndpoint.class);
        MockEndpoint mockv = context.getEndpoint("mock:validator", MockEndpoint.class);
        mocka.setExpectedCount(1);
        mockv.setExpectedCount(1);
        Object answer = template.requestBody("direct:a", "foo");
        mocka.assertIsSatisfied();
        mockv.assertIsSatisfied();
        Exchange exa = mocka.getExchanges().get(0);
        assertEquals(A.class, exa.getIn().getBody().getClass());
        Exchange exv = mockv.getExchanges().get(0);
        assertEquals(A.class, exv.getIn().getBody().getClass());
        assertEquals(A.class, answer.getClass());
    }

    @Test
    public void testOutputTypeOnly() throws Exception {
        context.getTypeConverterRegistry().addTypeConverters(new MyTypeConverters());
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                validator()
                    .type(A.class)
                    .withUri("direct:validator");
                from("direct:a")
                    .outputTypeWithValidate(A.class)
                    .to("mock:a");
                from("direct:validator")
                    .to("mock:validator");
            }
        });
        context.start();
        
        MockEndpoint mocka = context.getEndpoint("mock:a", MockEndpoint.class);
        MockEndpoint mockv = context.getEndpoint("mock:validator", MockEndpoint.class);
        mocka.setExpectedCount(1);
        mockv.setExpectedCount(1);
        Object answer = template.requestBody("direct:a", "foo");
        mocka.assertIsSatisfied();
        mockv.assertIsSatisfied();
        Exchange exa = mocka.getExchanges().get(0);
        assertEquals("foo", exa.getIn().getBody());
        Exchange exv = mockv.getExchanges().get(0);
        assertEquals(A.class, exv.getIn().getBody().getClass());
        assertEquals(A.class, answer.getClass());
    }

    public static class MyTypeConverters implements TypeConverters {
        @Converter
        public A toA(String in) {
            return new A();
        }
    }

    public static class A { }
}
