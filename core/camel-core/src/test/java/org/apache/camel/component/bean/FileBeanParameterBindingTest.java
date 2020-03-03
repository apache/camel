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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Header;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.junit.Before;
import org.junit.Test;

public class FileBeanParameterBindingTest extends ContextTestSupport {

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/data/foo");
        super.setUp();
    }

    @Override
    protected Registry createRegistry() throws Exception {
        Registry jndi = super.createRegistry();
        jndi.bind("foo", new MyFooBean());
        return jndi;
    }

    @Test
    public void testFileToBean() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBodyAndHeader("file:target/data/foo", "Hello World", Exchange.FILE_NAME, "hello.txt");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:target/data/foo").to("bean:foo?method=before").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        exchange.getIn().setHeader("bar", 123);
                    }
                }).to("bean:foo?method=after").to("mock:result");

            }
        };
    }

    public static class MyFooBean {

        public void before(@Header("bar") Integer bar, @Header(Exchange.FILE_NAME) String name) {
            assertNull("There should be no bar", bar);
            assertEquals("hello.txt", name);
        }

        public void after(@Header("bar") Integer bar, @Header(Exchange.FILE_NAME) String name) {
            assertNotNull("There should be bar", bar);
            assertEquals(123, bar.intValue());
            assertEquals("hello.txt", name);
        }
    }
}
