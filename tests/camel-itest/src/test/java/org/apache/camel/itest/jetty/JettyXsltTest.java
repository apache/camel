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
package org.apache.camel.itest.jetty;

import java.io.InputStream;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.ObjectHelper;
import org.junit.Test;

public class JettyXsltTest extends CamelTestSupport {

    private int port;

    @Test
    public void testClasspath() throws Exception {
        String response = template.requestBody("xslt:org/apache/camel/itest/jetty/greeting.xsl", "<hello>Camel</hello>", String.class);

        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?><goodbye>Camel</goodbye>", response);
    }

    @Test
    public void testClasspathInvalidParameter() throws Exception {
        try {
            template.requestBody("xslt:org/apache/camel/itest/jetty/greeting.xsl?name=greeting.xsl", "<hello>Camel</hello>", String.class);
            fail("Should have thrown exception");
        } catch (ResolveEndpointFailedException e) {
            assertTrue(e.getMessage().endsWith("Unknown parameters=[{name=greeting.xsl}]"));
        }
    }

    @Test
    public void testHttp() throws Exception {
        String response = template.requestBody("xslt://http://localhost:" + port + "/test?name=greeting.xsl", "<hello>Camel</hello>", String.class);

        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?><goodbye>Camel</goodbye>", response);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        port = AvailablePortFinder.getNextAvailable();

        return new RouteBuilder() {
            public void configure() {
                from("jetty:http://localhost:" + port + "/test")
                    .process(new Processor() {
                        @Override
                        public void process(Exchange exchange) throws Exception {
                            String name = exchange.getIn().getHeader("name", String.class);
                            ObjectHelper.notNull(name, "name");

                            name = "org/apache/camel/itest/jetty/" + name;
                            InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(exchange.getContext(), name);
                            String xml = exchange.getContext().getTypeConverter().convertTo(String.class, is);

                            exchange.getOut().setBody(xml);
                            exchange.getOut().setHeader(Exchange.CONTENT_TYPE, "text/xml");
                        }
                    });
            }
        };
    }

}
