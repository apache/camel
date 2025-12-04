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

package org.apache.camel.component.bean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Header;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.Test;

public class FileBeanParameterBindingTest extends ContextTestSupport {
    private static final String TEST_FILE_NAME = "hello." + UUID.randomUUID() + ".txt";

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry jndi = super.createCamelRegistry();
        jndi.bind("foo", new MyFooBean());
        return jndi;
    }

    @Test
    public void testFileToBean() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, TEST_FILE_NAME);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri())
                        .to("bean:foo?method=before")
                        .process(new Processor() {
                            public void process(Exchange exchange) {
                                exchange.getIn().setHeader("bar", 123);
                            }
                        })
                        .to("bean:foo?method=after")
                        .to("mock:result");
            }
        };
    }

    public static class MyFooBean {

        public void before(@Header("bar") Integer bar, @Header(Exchange.FILE_NAME) String name) {
            assertNull(bar, "There should be no bar");
            assertEquals(TEST_FILE_NAME, name);
        }

        public void after(@Header("bar") Integer bar, @Header(Exchange.FILE_NAME) String name) {
            assertNotNull(bar, "There should be bar");
            assertEquals(123, bar.intValue());
            assertEquals(TEST_FILE_NAME, name);
        }
    }
}
