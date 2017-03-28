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
package org.apache.camel.component.cxf.transport;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class CxfRsCamelTransportTest extends CamelSpringTestSupport {

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("/org/apache/camel/component/cxf/transport/CxfRsCamelTransport.xml");
    }

    @Test
    public void testCamelTransport() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("Hello, Test!");

        template.sendBody("direct:input", "Test");
        assertMockEndpointsSatisfied();
    }

    @Path("/greeting")
    public interface GreetingResource {

        @GET
        @Path("/hello/{name}")
        @Consumes("text/plain")
        @Produces("text/plain")
        String hello(@PathParam("name") String name);
    }

    @Path("/greeting")
    public static class GreetingResourceBean implements GreetingResource {

        @GET
        @Path("/hello/{name}")
        @Consumes("text/plain")
        @Produces("text/plain")
        public String hello(@PathParam("name") String name) {
            return String.format("Hello, %s!", name);
        }
    }

}
