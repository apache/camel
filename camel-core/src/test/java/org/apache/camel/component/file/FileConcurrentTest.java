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

import java.util.Map;
import java.util.Random;

import org.apache.camel.Body;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Header;
import org.apache.camel.Headers;
import org.apache.camel.TypeConverter;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import static org.apache.camel.language.simple.FileLanguage.file;

/**
 * File being processed sync vs async to demonstrate the time difference.
 *
 * @version $Revision$
 */
public class FileConcurrentTest extends ContextTestSupport {

    private static final Log LOG = LogFactory.getLog(FileConcurrentTest.class);

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("business", new MyBusinessBean());
        return jndi;
    }

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/concurrent");
        super.setUp();
        // create 10 files
        for (int i = 0; i < 10; i++) {
            template.sendBodyAndHeader("file://target/concurrent", "Total order: " + 100 * i, Exchange.FILE_NAME, i + ".txt");
        }
    }

    public void testProcessFilesConcurrently() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file://target/concurrent?delay=60000&initialDelay=2500")
                    .setHeader("id", file("${file:onlyname.noext}"))
                    .async(20)
                    .beanRef("business")
                    .aggregate(header("country"), new MyBusinessTotal()).batchSize(10).batchTimeout(60000).to("mock:result");
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
                    .aggregate(header("country"), new MyBusinessTotal()).batchSize(10).batchTimeout(60000).to("mock:result");
            }
        });

        long start = System.currentTimeMillis();

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceivedInAnyOrder("2000", "2500");

        assertMockEndpointsSatisfied();

        long delta = System.currentTimeMillis() - start;
        LOG.debug("Time taken sequentiel: " + delta);
    }

    public static class MyBusinessBean {

        private Random ran = new Random();

        public Integer processData(@Body String data, @Header(value = "id") int id, @Headers Map headers, TypeConverter converter) {
            // simulate some heavy calculations

            int num = 200 + ran.nextInt(500);
            try {
                Thread.sleep(num);
            } catch (InterruptedException e) {
                // ignore
            }

            String total = ObjectHelper.after(data, "Total order: ");
            String country = (id % 2 == 0) ? "dk" : "uk";

            LOG.debug("Order sum: " + total + " for country: " + country);

            headers.put("country", country);
            return converter.convertTo(Integer.class, total);
        }
    }

    public static class MyBusinessTotal implements AggregationStrategy {

        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            Exchange answer = newExchange;

            String country = newExchange.getIn().getHeader("country", String.class);
            Integer current = 0;
            if (oldExchange != null) {
                current = oldExchange.getIn().getBody(Integer.class);
                answer = oldExchange;
            }
            Integer add = newExchange.getIn().getBody(Integer.class);
            int total = current.intValue() + add.intValue();
            LOG.debug("Aggregated sum so far: " + total + " for country: " + country);
            answer.getIn().setBody(total);
            return answer;
        }
    }

}
