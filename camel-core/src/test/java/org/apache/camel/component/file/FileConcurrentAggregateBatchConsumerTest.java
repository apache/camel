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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import static org.apache.camel.language.simple.FileLanguage.file;

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
                from("file://target/concurrent?delay=60000&initialDelay=2500")
                    .setHeader("id", file("${file:onlyname.noext}"))
                    .async(20)
                    .beanRef("business")
                    .aggregate(header("country"), new MyBusinessTotal()).batchConsumer().batchTimeout(60000).to("mock:result");
            }
        });

        long start = System.currentTimeMillis();

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceivedInAnyOrder("2000", "2500");

        assertMockEndpointsSatisfied();

        long delta = System.currentTimeMillis() - start;
        LOG.debug("Time taken parallel: " + delta);
    }

    public void testProcessFilesSequentiel() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file://target/concurrent?delay=60000&initialDelay=2500")
                    .setHeader("id", file("${file:onlyname.noext}"))
                    .beanRef("business")
                    .aggregate(header("country"), new MyBusinessTotal()).batchConsumer().batchTimeout(60000).to("mock:result");
            }
        });

        long start = System.currentTimeMillis();

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceivedInAnyOrder("2000", "2500");

        assertMockEndpointsSatisfied();

        long delta = System.currentTimeMillis() - start;
        LOG.debug("Time taken sequentiel: " + delta);
    }

}