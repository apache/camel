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
package org.apache.camel.component.file;

import java.util.UUID;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.Test;

/**
 * Unit test that we can chain bean and file producer.
 */
public class BeanToFileTest extends ContextTestSupport {
    private static final String TEST_FILE_NAME = "BeanToFileTest" + UUID.randomUUID() + ".txt";

    @Test
    public void testBeanToFile() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        mock.expectedFileExists(testFile(TEST_FILE_NAME), "Bye World");

        template.sendBody("direct:in", "World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry answer = super.createCamelRegistry();
        answer.bind("myBean", new MyBean());
        return answer;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:in").to("bean:myBean").setHeader(Exchange.FILE_NAME, constant(TEST_FILE_NAME))
                        .to(fileUri("?fileExist=Override"), "mock:result");
            }
        };
    }

    public static class MyBean {
        public String doSomething(String input) {
            return "Bye " + input;
        }
    }

}
