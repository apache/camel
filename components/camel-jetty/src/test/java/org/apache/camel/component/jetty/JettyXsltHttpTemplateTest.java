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
package org.apache.camel.component.jetty;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JettyXsltHttpTemplateTest extends CamelTestSupport {

    private int port;

    @Test
    void testXsltHttpTemplate() throws Exception {
        // give Jetty a bit time to startup and be ready
        Thread.sleep(1000);

        String xml = template.requestBody("xslt:http://0.0.0.0:" + port + "/myxslt",
                "<mail><subject>Hey</subject><body>Hello world!</body></mail>", String.class);

        assertNotNull(xml, "The transformed XML should not be null");
        assertTrue(xml.contains("transformed"));
        // the cheese tag is in the transform.xsl
        assertTrue(xml.contains("cheese"));
        assertTrue(xml.contains("<subject>Hey</subject>"));
        assertTrue(xml.contains("<body>Hello world!</body>"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        port = AvailablePortFinder.getNextAvailable();

        return new RouteBuilder() {
            @Override
            public void configure() {
                from("jetty:http://0.0.0.0:" + port + "/myxslt")
                        .pollEnrich(
                                "file://src/test/resources/org/apache/camel/component/jetty/?fileName=transform.xsl&noop=true&readLock=none",
                                2000)
                        .convertBodyTo(String.class)
                        .to("log:transform");
            }
        };
    }

}
