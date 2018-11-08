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
package org.apache.camel.component.file;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Before;
import org.junit.Test;

public class FileConsumerPreMoveLastModifiedTest extends ContextTestSupport {

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/premove");
        super.setUp();
    }

    @Test
    public void testPreMoveLastModified() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        template.sendBodyAndHeader("file://target/premove", "Hello World", Exchange.FILE_NAME, "hello.txt");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file://target/premove?preMove=work/work-${file:name}&initialDelay=0&delay=10&keepLastModified=true")
                        .process(new LastModifiedCheckerProcessor())
                        .log("Got file ${file:name} modified=${file:modified}")
                        .to("mock:result");
            }
        };
    }

    private static class LastModifiedCheckerProcessor implements Processor {

        public void process(Exchange exchange) throws Exception {
            assertTrue(exchange.getIn().getHeader(Exchange.FILE_LAST_MODIFIED, Long.class) > 0L);
            assertTrue(exchange.getIn().getHeader(Exchange.FILE_LENGTH, Long.class) > 0L);
        }
    }
}
