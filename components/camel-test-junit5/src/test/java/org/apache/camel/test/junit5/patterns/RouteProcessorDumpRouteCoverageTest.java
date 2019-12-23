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
package org.apache.camel.test.junit5.patterns;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestReporter;

import static org.apache.camel.test.junit5.TestSupport.assertFileExists;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class RouteProcessorDumpRouteCoverageTest extends CamelTestSupport {

    @Override
    public boolean isDumpRouteCoverage() {
        return true;
    }

    @Test
    public void testProcessorJunit5() {
        String out = template.requestBody("direct:start", "Hello World", String.class);
        assertEquals("Bye World", out);
    }

    @Test
    public void testProcessorJunit5WithTestParameterInjection(TestInfo info, TestReporter testReporter) {
        assertNotNull(info);
        assertNotNull(testReporter);
        String out = template.requestBody("direct:start", "Hello World", String.class);
        assertEquals("Bye World", out);
    }

    @AfterAll
    public static void checkDumpFilesCreatedAfterTests() {
        // should create that file when test is done
        assertFileExists("target/camel-route-coverage/RouteProcessorDumpRouteCoverageTest-testProcessorJunit5.xml");
        assertFileExists("target/camel-route-coverage/RouteProcessorDumpRouteCoverageTest-testProcessorJunit5WithTestParameterInjection.xml");
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").process(exchange -> exchange.getMessage().setBody("Bye World"));
            }
        };
    }

}
