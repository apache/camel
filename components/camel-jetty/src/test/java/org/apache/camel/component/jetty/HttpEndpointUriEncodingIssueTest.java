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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
class HttpEndpointUriEncodingIssueTest extends BaseJettyTest {

    @Test
    public void testEndpointUriEncodingIssue() {
        String uri = "http://localhost:{{port}}/myapp/mytest?columns=totalsens,upsens&username=apiuser";
        String out = template.requestBody(uri, null, String.class);

        assertEquals("We got totalsens,upsens columns", out);
    }

    @Test
    public void testEndpointUriWithDanishCharEncodingIssue() {
        String uri = "http://localhost:{{port}}/myapp/mytest?columns=claus,s\u00F8ren&username=apiuser";
        String out = template.requestBody(uri, null, String.class);

        assertEquals("We got claus,s\u00F8ren columns", out);
    }

    @Test
    void testEndpointHeaderUriEncodingIssue() {
        // the uri is supplied via the CamelHttpUri header, which is message content and therefore not
        // subject to property placeholder resolution, so the port must be resolved up-front here
        String uri = "http://localhost:" + getPort() + "/myapp/mytest?columns=totalsens,upsens&username=apiuser";
        String out = template.requestBodyAndHeader("http://localhost/dummy", null, Exchange.HTTP_URI, uri, String.class);

        assertThat(out).isEqualTo("We got totalsens,upsens columns");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("jetty:http://localhost:{{port}}/myapp/mytest").process(new Processor() {
                    public void process(Exchange exchange) {
                        String columns = exchange.getIn().getHeader("columns", String.class);
                        exchange.getMessage().setBody("We got " + columns + " columns");
                    }
                });
            }
        };
    }

}
