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
import org.apache.camel.processor.BodyInAggregatingStrategy;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import static org.apache.camel.language.simple.SimpleLanguage.simple;

/**
 * File being processed sync vs async to demonstrate the time difference.
 *
 * @version $Revision$
 */
public class FileConcurrentTest extends ContextTestSupport {

    private static final Log LOG = LogFactory.getLog(FileConcurrentTest.class);
    private static char[] chars = {'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J'};

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
            char ch = chars[i];
            template.sendBodyAndHeader("file://target/concurrent", "" + ch, Exchange.FILE_NAME, i + ".txt");
        }
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    public void testProcessFilesConcurrently() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file://target/concurrent?sortBy=file:name")
                    .setHeader("id", simple("${file:onlyname.noext}"))
                    .threads(10)
                    .beanRef("business")
                    .log("Country is ${in.header.country}")
                    .aggregate(header("country"), new BodyInAggregatingStrategy())
                        .completionTimeout(2000L)
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
                from("file://target/concurrent?sortBy=file:name")
                    .setHeader("id", simple("${file:onlyname.noext}"))
                    .beanRef("business")
                    .aggregate(header("country"), new BodyInAggregatingStrategy())
                        .completionTimeout(2000L)
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
        LOG.debug("Time taken sequentiel: " + delta);
    }

    public static class MyBusinessBean {

        private Random ran = new Random();

        public String processData(@Body String data, @Header(value = "id") int id, @Headers Map<String, Object> headers, TypeConverter converter) {
            // simulate some heavy calculations
            int num = 200 + ran.nextInt(500);
            try {
                Thread.sleep(num);
            } catch (InterruptedException e) {
                // ignore
            }

            String country = (id % 2 == 0) ? "dk" : "uk";
            LOG.debug("Data: " + data + " for country: " + country);
            headers.put("country", country);

            return data;
        }
    }

}
