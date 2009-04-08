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

import java.util.HashMap;
import java.util.Map;
import javax.naming.Context;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.dataset.SimpleDataSet;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A route for simple performance testing that can be used when we suspect
 * something is wrong. Inspired by end user on forum doing this as proof of concept.
 */
public class RoutePerformanceTest extends ContextTestSupport {
    private static final Log LOG = LogFactory.getLog(RoutePerformanceTest.class);

    protected SimpleDataSet dataSet = new SimpleDataSet(1000);

    public void testPerformance() throws Exception {
        long start = System.nanoTime();

        MockEndpoint endpoint = getMockEndpoint("mock:results");
        endpoint.expectedMessageCount((int) dataSet.getSize());

        assertMockEndpointsSatisfied();

        long delta = System.nanoTime() - start;

        LOG.debug("Took: " + delta + " ns");
        LOG.debug("Took: " + delta / 1000000 + " millis");
    }

    @Override
    protected Context createJndiContext() throws Exception {
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put("foo", 123);
        dataSet.setDefaultHeaders(headers);

        Context context = super.createJndiContext();
        context.bind("foo", dataSet);
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("dataset:foo").to("direct:start");

                from("direct:start").to("direct:a", "direct:b", "direct:c");
                from("direct:c")
                    .choice()
                        .when().header("foo").to("mock:results", "dataset:foo")
                        .otherwise().to("mock:results", "dataset:foo")
                    .end();
            }
        };
    }
}
