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
package org.apache.camel.language;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class XPathAnotherRouteConcurrentTest extends ContextTestSupport {

    public void testConcurrent() throws Exception {
        doSendMessages(100, 10);
    }

    private void doSendMessages(int files, int poolSize) throws Exception {
        getMockEndpoint("mock:claus").expectedMessageCount(files / 2);
        getMockEndpoint("mock:james").expectedMessageCount(files / 2);
        getMockEndpoint("mock:claus").expectsNoDuplicates(body());
        getMockEndpoint("mock:james").expectsNoDuplicates(body());
        getMockEndpoint("mock:other").expectedMessageCount(0);

        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        for (int i = 0; i < files; i++) {
            final int index = i;
            executor.submit(new Callable<Object>() {
                public Object call() throws Exception {
                    if (index % 2 == 0) {
                        template.sendBody("seda:foo", createXmlBody(index, "Claus"));
                    } else {
                        template.sendBody("seda:foo", createXmlBody(index, "James"));
                    }
                    return null;
                }
            });
        }

        assertMockEndpointsSatisfied();
        executor.shutdown();
    }

    private String createXmlBody(int index, String name) {
        StringBuilder sb = new StringBuilder();
        sb.append("<persons>\n");
        for (int i = 0; i < 100; i++) {
            sb.append("<person>");
            sb.append("<id>" + index + "-" + i + "</id>");
            sb.append("<name>");
            if (i == 95) {
                sb.append(name);
            } else {
                sb.append("Foo");
            }
            sb.append("</name>");
            sb.append("</person>'\n");
        }
        sb.append("\n</persons>");
        return sb.toString();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:foo?concurrentConsumers=10")
                    .choice()
                        .when().xpath("/persons/person/name = 'Claus'")
                            .to("mock:claus")
                        .when().xpath("/persons/person/name = 'James'")
                            .to("mock:james")
                        .otherwise()
                            .to("mock:other")
                    .end();

            }
        };
    }
}