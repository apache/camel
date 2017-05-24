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
package org.apache.camel.test.patterns;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class RouteProcessorDumpRouteCoverageTest extends CamelTestSupport {

    @Override
    public boolean isDumpRouteCoverage() {
        return true;
    }

    @Test
    public void testProcessor() throws Exception {
        String out = template.requestBody("direct:start", "Hello World", String.class);
        assertEquals("Bye World", out);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        // should create that file when test is done
        assertFileExists("target/camel-route-coverage/RouteProcessorDumpRouteCoverageTest-testProcessor.xml");
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .process(exchange -> exchange.getOut().setBody("Bye World"));
            }
        };
    }

}
