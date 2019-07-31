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
package org.apache.camel.test.perf;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.StopWatch;
import org.junit.Test;

public class XsltPerformanceTest extends AbstractBasePerformanceTest {

    private final int count = 10000;

    @Test
    public void testXslt() throws InterruptedException {
        template.setDefaultEndpointUri("direct:xslt");

        // warm up with 1.000 messages so that the JIT compiler kicks in
        execute(1000);

        resetMock(count);

        StopWatch watch = new StopWatch();
        execute(count);

        assertMockEndpointsSatisfied();
        log.warn("Ran {} tests in {}ms", count, watch.taken());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:xslt")
                    .to("xslt://META-INF/xslt/transform.xslt")
                    .to("mock:end");
            }
        };
    }
}