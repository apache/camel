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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.Test;

/**
 * Unit test for the file filter option
 */
public class FileConsumerFileFilterOptimizedTest extends ContextTestSupport {

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry jndi = super.createCamelRegistry();
        jndi.bind("myFilter", new MyFileFilter());
        return jndi;
    }

    @Test
    public void testFilterFiles() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        template.sendBodyAndHeader(fileUri(), "This is a file to be filtered",
                Exchange.FILE_NAME,
                "skipme.txt");

        mock.setResultWaitTime(100);
        mock.assertIsSatisfied();
    }

    @Test
    public void testFilterFilesWithARegularFile() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader(fileUri(), "This is a file to be filtered",
                Exchange.FILE_NAME,
                "skipme.txt");

        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME,
                "hello.txt");

        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(fileUri("?initialDelay=0&delay=10&filter=#myFilter"))
                        .convertBodyTo(String.class).to("mock:result");
            }
        };
    }

    public static class MyFileFilter implements OptimizedFileFilter {

        @Override
        public Boolean accept(String name) {
            // we dont accept any files starting with skip in the name
            return !name.startsWith("skip");
        }
    }

}
