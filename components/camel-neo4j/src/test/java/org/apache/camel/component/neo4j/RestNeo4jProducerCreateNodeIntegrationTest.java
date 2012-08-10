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

package org.apache.camel.component.neo4j;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Ignore;
import org.junit.Test;
import org.neo4j.graphdb.Node;
import org.springframework.data.neo4j.rest.SpringRestGraphDatabase;

@Ignore("This test need to start the neo4j server first")
public class RestNeo4jProducerCreateNodeIntegrationTest extends CamelTestSupport {
   
    @Produce(uri = "direct:start")
    protected ProducerTemplate template;

    private final String neo4jEndpoint = "neo4j:http://localhost:7474/db/data/";

    private final SpringRestGraphDatabase db = new SpringRestGraphDatabase("http://localhost:7474/db/data/");

   
    @EndpointInject(uri = "mock:end")
    private MockEndpoint end;

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                from("direct:start").to(neo4jEndpoint).process(new Processor() {

                    @Override
                    public void process(Exchange arg0) throws Exception {
                        Long id = (Long)arg0.getIn().getHeader(Neo4jEndpoint.HEADER_NODE_ID);
                        assertNotNull(id);
                        Node node = db.getNodeById(id);
                        assertNotNull(node);
                    }
                }).to(end);
            }
        };
    }

    @Test
    public void testCreateNodes() throws InterruptedException {

        final int messageCount = 100;
        end.expectedMessageCount(messageCount);

        Thread t = new Thread(new Runnable() {

            @Override
            public void run() {
                for (int k = 0; k < messageCount; k++) {
                    template.sendBodyAndHeader(null, Neo4jEndpoint.HEADER_OPERATION,
                                               Neo4jOperation.CREATE_NODE);
                }
            }
        });
        t.start();
        t.join();
        end.assertIsSatisfied();
    }

}
