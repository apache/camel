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

import java.util.concurrent.CountDownLatch;

import junit.framework.TestCase;
import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;

public class Camel715ThreadProcessorTest extends TestCase {
    private static final int ITERS = 50000;

    class SendingProcessor implements Processor {
        int iterationNumber;
        public SendingProcessor(int iter) {
            iterationNumber = iter;
        }

        public void process(Exchange exchange) throws Exception {
            Message in = exchange.getIn();
            in.setBody("a");
            // may set the property here
            exchange.setProperty("iterationNumber", iterationNumber);
        }

    }

    public void testThreadProcessor() {
        try {
            CamelContext context = new DefaultCamelContext();

            final CountDownLatch latch = new CountDownLatch(ITERS);

            context.addRoutes(new RouteBuilder() {

                @Override
                public void configure() throws Exception {
                    from("direct:a").thread(4).process(new Processor() {

                        public void process(Exchange ex) throws Exception {
                            latch.countDown();
                        }
                    });
                }

            });

            final ProducerTemplate<Exchange> template = context.createProducerTemplate();

            final Endpoint e = context.getEndpoint("direct:a");
            context.start();

            for (int i = 0; i < ITERS; i++) {
                template.send(e, new SendingProcessor(i), new AsyncCallback() {
                    public void done(boolean arg0) {
                        // Do nothing here
                    }
                });
            }

            latch.await();

            context.stop();
        } catch (Exception ex) {
            fail("Get the exception " + ex + "here");
            // Make sure we the threads will exit, or the test will hung
            System.exit(-1);
        }
    }

}
