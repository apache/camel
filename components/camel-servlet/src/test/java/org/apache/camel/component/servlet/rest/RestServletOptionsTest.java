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
package org.apache.camel.component.servlet.rest;

import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;
import com.meterware.servletunit.ServletUnitClient;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.servlet.ServletCamelRouterTestSupport;
import org.apache.camel.component.servlet.ServletRestHttpBinding;
import org.apache.camel.impl.JndiRegistry;
import org.junit.Test;

public class RestServletOptionsTest extends ServletCamelRouterTestSupport {
    
    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("myBinding", new ServletRestHttpBinding());
        return jndi;
    }

    @Test
    public void testServletOptions() throws Exception {
        WebRequest req = new OptionsMethodWebRequest(CONTEXT_URL + "/services/users/v1/customers");
        ServletUnitClient client = newClient();
        client.setExceptionsThrownOnErrorStatus(false);
        WebResponse response = client.getResponse(req);

        assertEquals(200, response.getResponseCode());
        assertEquals("OPTIONS,GET", response.getHeaderField("ALLOW"));
        assertEquals("", response.getText());

        req = new OptionsMethodWebRequest(CONTEXT_URL + "/services/users/v1/123");
        response = client.getResponse(req);

        assertEquals(200, response.getResponseCode());
        assertEquals("OPTIONS,PUT", response.getHeaderField("ALLOW"));
        assertEquals("", response.getText());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // configure to use servlet on localhost
                restConfiguration().component("servlet").host("localhost").endpointProperty("httpBinding", "#myBinding");
                
                // use the rest DSL to define the rest services
                rest("/users/")
                    .get("v1/customers")
                        .to("mock:customers")
                    .put("v1/{id}")
                        .to("mock:id");
            }
        };
    }

}
