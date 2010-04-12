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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.BodyInAggregatingStrategy;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import static org.apache.camel.language.simple.SimpleLanguage.simple;

/**
 * File being processed sync vs async to demonstrate the time difference.
 *
 * @version $Revision$
 */
public class FileConcurrentAggregateBatchConsumerTest extends FileConcurrentTest {

    private static final Log LOG = LogFactory.getLog(FileConcurrentAggregateBatchConsumerTest.class);

    public void testProcessFilesConcurrently() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file://target/concurrent")
                    .setHeader("id", simple("${file:onlyname.noext}"))
                    .threads(10)
                    .beanRef("business")
                    .aggregate(header("country"), new BodyInAggregatingStrategy())
                        .completionFromBatchConsumer()
                        .to("mock:result");
            }
        });
        context.start();

        long start = System.currentTimeMillis();

        MockEndpoint result = getMockEndpoint("mock:result");
        // can arrive in any order
        result.expectedMessageCount(2);

        assertMockEndpointsSatisfied();

        long delta = System.currentTimeMillis() - start;
        LOG.debug("Time taken parallel: " + delta);

        for (int i = 0; i < 2; i++) {
            String body = result.getReceivedExchanges().get(i).getIn().getBody(String.class);
            LOG.info("Got body: " + body);
            if (body.contains("A")) {
                assertTrue("Should contain C, was:" + body, body.contains("C"));
                assertTrue("Should contain E, was:" + body, body.contains("E"));
                assertTrue("Should contain G, was:" + body, body.contains("G"));
                assertTrue("Should contain I, was:" + body, body.contains("I"));
            } else if (body.contains("B")) {
                assertTrue("Should contain D, was:" + body, body.contains("D"));
                assertTrue("Should contain F, was:" + body, body.contains("F"));
                assertTrue("Should contain H, was:" + body, body.contains("H"));
                assertTrue("Should contain J, was:" + body, body.contains("J"));
            } else {
                fail("Unexpected body, was: " + body);
            }
        }
    }

    public void testProcessFilesSequentiel() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file://target/concurrent")
                    .setHeader("id", simple("${file:onlyname.noext}"))
                    .beanRef("business")
                    .aggregate(header("country"), new BodyInAggregatingStrategy())
                        .completionFromBatchConsumer()
                        .to("mock:result");
            }
        });
        context.start();

        long start = System.currentTimeMillis();

        MockEndpoint result = getMockEndpoint("mock:result");
        // should be ordered in the body, but the files can be loaded in different order per OS
        result.expectedBodiesReceivedInAnyOrder("A+C+E+G+I", "B+D+F+H+J");

        assertMockEndpointsSatisfied();

        long delta = System.currentTimeMillis() - start;
        LOG.debug("Time taken sequential: " + delta);
    }

}