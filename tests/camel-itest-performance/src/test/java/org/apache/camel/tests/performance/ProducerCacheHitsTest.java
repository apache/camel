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
package org.apache.camel.tests.performance;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.tests.component.PerformanceTestComponent;
import org.apache.camel.util.StopWatch;
import org.junit.Test;


public class ProducerCacheHitsTest extends CamelTestSupport {
    private static final String SMALL_MESSAGE = "message";
    private static final DecimalFormat FORMAT = new DecimalFormat("#.##");

    @Test
    public void testRepeatProcessing() throws Exception {
        MockEndpoint data = getMandatoryEndpoint("mock:results", MockEndpoint.class);
        data.expectedMessageCount(4 * 7);

        for (int iter = 10; iter <= 10000; iter *= 10) {
            for (int t = 2; t <= 128; t *= 2) {
                runTest("test-perf:endpoint", SMALL_MESSAGE, iter, t);
            }
        }
        
        data.assertIsSatisfied();
        for (Exchange ex : data.getExchanges()) {
            TestResult r = ex.getIn().getBody(TestResult.class);
            
            log.info(r.toString());
            
        }
    }
    
    protected Object runTest(String uri, String body, int iterations, int threads) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(PerformanceTestComponent.HEADER_ITERATIONS, iterations);
        headers.put(PerformanceTestComponent.HEADER_THREADS, threads);
        
        StopWatch watch = new StopWatch();
        Object result = template.requestBodyAndHeaders(uri, body, headers);
        template.sendBody("mock:results", new TestResult(uri, iterations, threads, watch.taken()));
        return result;
    }
    
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("test-perf:endpoint").to("echo:echo");
            }
        };
    }
    
    public final class TestResult {
        public String uri;
        public int iterations;
        public int threads;
        public long time;
        
        public TestResult(String uri, int iterations, int threads, long time) {
            this.uri = uri;
            this.iterations = iterations;
            this.threads = threads;
            this.time = time;
        }
        
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            sb.append(FORMAT.format(1000.0 * iterations / time));
            sb.append(" /s], ");
            sb.append(uri);
            sb.append(", ");
            sb.append(iterations);
            sb.append(", ");
            sb.append(threads);
            sb.append(", ");
            sb.append(time);
            return sb.toString();
        }
    }
}