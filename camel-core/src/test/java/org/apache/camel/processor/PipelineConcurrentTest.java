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
package org.apache.camel.processor;

import java.util.Random;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Unit test the pipeline in concurrent conditions.
 */
public class PipelineConcurrentTest extends ContextTestSupport {

    public void testConcurrentPipeline() throws Exception {
        int total = 10000;
        final int group = total / 20;
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(total);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(20);
        executor.afterPropertiesSet();
        for (int i = 0; i < 20; i++) {
            final int threadCount = i;
            executor.execute(new Runnable() {
                public void run() {
                    int start = threadCount * group;
                    for (int i = 0; i < group; i++) {
                        try {
                            // do some random sleep to simulate spread in user activity
                            Thread.sleep(new Random().nextInt(10));
                        } catch (InterruptedException e) {
                            // ignore
                        }
                        template.sendBody("seda:in?size=10000", "" + (start + i));
                    }
                }
            });
        }

        mock.assertIsSatisfied();
        mock.expectsNoDuplicates(body());
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // to force any exceptions coming forward imeddiately
                errorHandler(noErrorHandler());

                from("seda:in?size=10000")
                    .thread(10)
                    .pipeline("direct:do", "mock:result");

                from("direct:do")
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            String body = exchange.getIn().getBody(String.class);
                            exchange.getOut().setBody("Bye " + body);
                        }
                    });
            }
        };
    }
}
