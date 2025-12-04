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

package org.apache.camel.component.jetty.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jetty.BaseJettyTest;
import org.apache.camel.component.mock.MockEndpoint;
import org.eclipse.jetty.server.CustomRequestLog;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.server.Slf4jRequestLogWriter;
import org.junit.jupiter.api.Test;

public class RestRequestLogTest extends BaseJettyTest {

    @Test
    public void testJettyRequestLog() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        String out = template.requestBody("http://localhost:" + getPort() + "/api/123/", null, String.class);
        assertEquals("Bye 123", out);

        MockEndpoint.assertIsSatisfied(context);
    }

    @BindToRegistry("myRequestLog")
    public RequestLog loadRequestLog() {
        String format = "%t %{client}a %H %s %m %U%q - %{ms}T %I %O";
        Slf4jRequestLogWriter logWriter = new Slf4jRequestLogWriter();
        logWriter.setLoggerName("accesslog");
        CustomRequestLog requestLog = new CustomRequestLog(logWriter, format);
        return requestLog;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // configure to use jetty on localhost with the given port using custom request logger
                restConfiguration()
                        .component("jetty")
                        .host("localhost")
                        .port(getPort())
                        .componentProperty("requestLog", "#myRequestLog");

                rest("/api/").get("/{id}/").to("direct:foo");

                from("direct:foo").removeHeaders("CamelHttp*").to("http://localhost:" + getPort2());

                from("jetty:http://localhost:" + getPort2() + "?matchOnUriPrefix=true")
                        .to("mock:result")
                        .transform()
                        .simple("Bye ${header.id}");
            }
        };
    }
}
