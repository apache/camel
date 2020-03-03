/*
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
package org.apache.camel.component.dataset;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.apache.camel.util.StopWatch;
import org.junit.Test;

/**
 * A route for simple performance testing that can be used when we suspect
 * something is wrong. Inspired by end user on forum doing this as proof of
 * concept.
 */
public class RoutePerformanceTest extends ContextTestSupport {

    private int size = 250;

    private SimpleDataSet dataSet = new SimpleDataSet(size);

    private String uri = "mock:results";

    @Override
    protected Registry createRegistry() throws Exception {
        Registry answer = super.createRegistry();
        answer.bind("foo", dataSet);
        return answer;
    }

    @Test
    public void testPerformance() throws Exception {
        StopWatch watch = new StopWatch();

        MockEndpoint endpoint = getMockEndpoint(uri);
        endpoint.expectedMessageCount((int)dataSet.getSize());
        endpoint.expectedHeaderReceived("foo", 123);

        // wait 30 sec for slow servers
        MockEndpoint.assertIsSatisfied(context, 30, TimeUnit.SECONDS);

        log.info("RoutePerformanceTest: Sent: {} Took: {} ms", size, watch.taken());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                Map<String, Object> headers = new HashMap<>();
                headers.put("foo", 123);
                dataSet.setDefaultHeaders(headers);

                from("dataset:foo").to("direct:start");

                from("direct:start").to("log:a?level=OFF", "log:b?level=OFF", "direct:c");
                from("direct:c").choice().when().header("foo").to(uri, "dataset:foo").otherwise().to(uri, "dataset:foo").end();
            }
        };
    }
}
