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
package org.apache.camel.processor.enricher;

import java.io.File;
import java.io.FileWriter;
import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.junit.Test;

public class PollEnrichFileCustomAggregationStrategyTest extends ContextTestSupport {

    @Test
    public void testPollEnrichDefaultAggregationStrategyBody() throws Exception {

        Thread.sleep(2000);
        String enrichFilename = "target/pollEnrich/enrich.txt";
        String msgText = "Hello Camel";
        FileWriter enrichFile = new FileWriter(enrichFilename);
        enrichFile.write(msgText);
        enrichFile.close();

        getMockEndpoint("mock:result").expectedMinimumMessageCount(1);

        assertMockEndpointsSatisfied();

        List<Exchange> exchanges = getMockEndpoint("mock:result").getExchanges();
        assertEquals(1, exchanges.size());
        Exchange ex = (Exchange) exchanges.get(0);
        assertEquals(msgText, ex.getIn().getBody().toString());

        //This file should be deleted but it's there when the test end, so we have to use AssertTrue for the moment
        Thread.sleep(300);
        File markerFile = new File(enrichFilename + ".camelLock");
        assertTrue("Camel markerFile " + enrichFilename + ".camelLock did not get deleted after file consumption.", markerFile.exists());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("timer:foo?period=1000&repeatCount=1")
                    .setBody().constant("Hello from Camel.")
                    .pollEnrich("file:target/pollEnrich?fileName=enrich.txt&readLock=markerFile", new ReplaceAggregationStrategy())
                    .convertBodyTo(String.class)
                    .log("The body is ${body}")
                    .to("mock:result");
            }
        };
    }
    
    class ReplaceAggregationStrategy implements AggregationStrategy {

        public Exchange aggregate(Exchange original, Exchange resource) {
            Object resourceResponse = resource.getIn().getBody();
            if (original.getPattern().isOutCapable()) {
                original.getOut().setBody(resourceResponse);
            } else {
                original.getIn().setBody(resourceResponse);
            }
            return original;
        }
    }
}
