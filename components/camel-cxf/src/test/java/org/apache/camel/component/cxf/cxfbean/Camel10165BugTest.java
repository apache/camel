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
package org.apache.camel.component.cxf.cxfbean;

import javax.ws.rs.POST;
import javax.ws.rs.Path;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.CXFTestSupport;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class Camel10165BugTest extends CamelTestSupport {

    private static final int PORT1 = CXFTestSupport.getPort("Camel10165BugTest.1");

    @Test
    public void testCallServiceWithBasePath() throws Exception {
        String request = "abc";
        String response = template.requestBody("http://localhost:" + PORT1 + "/basePath/echo", request, String.class);
        assertEquals(request, response);
    }

    @Test
    public void testCallServiceWithoutBasePath() throws Exception {
        String request = "abc";
        String response = template.requestBody("http://localhost:" + PORT1 + "/echo", request, String.class);
        assertEquals(request, response);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("jetty:http://localhost:" + PORT1 + "/?matchOnUriPrefix=true")
                        .to("cxfbean:echoService");

                from("jetty:http://localhost:" + PORT1 + "/basePath/?matchOnUriPrefix=true")
                        .to("cxfbean:echoService");
            }
        };
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        registry.bind("echoService", new EchoService());
        return registry;
    }

    @Path("echo")
    public class EchoService {
        @POST
        public String echo(String request) {
            return request;
        }
    }
}
