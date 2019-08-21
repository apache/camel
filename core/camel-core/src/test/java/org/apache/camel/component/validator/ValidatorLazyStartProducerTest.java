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
package org.apache.camel.component.validator;

import java.io.FileNotFoundException;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class ValidatorLazyStartProducerTest extends ContextTestSupport {

    @Test
    public void testLazyStartProducerFail() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);

        try {
            template.sendBody("direct:fail", "<mail xmlns='http://foo.com/bar'><subject>Hey</subject><body>Hello world!</body></mail>");
            fail("Should throw exception");
        } catch (Exception e) {
            assertIsInstanceOf(FileNotFoundException.class, e.getCause());
        }

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testLazyStartProducerOk() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:ok", "<mail xmlns='http://foo.com/bar'><subject>Hey</subject><body>Hello world!</body></mail>");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:fail").to("validator:org/apache/camel/component/validator/unknown.xsd?lazyStartProducer=true").to("mock:result");

                from("direct:ok").to("validator:org/apache/camel/component/validator/schema.xsd?lazyStartProducer=true").to("mock:result");
            }
        };
    }
}
