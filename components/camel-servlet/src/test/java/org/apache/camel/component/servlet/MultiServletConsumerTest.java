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
package org.apache.camel.component.servlet;

import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.HttpNotFoundException;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;
import com.meterware.servletunit.ServletUnitClient;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class MultiServletConsumerTest extends ServletCamelRouterTestSupport {

    @Override
    protected String getConfiguration() {
        return "/org/apache/camel/component/servlet/multiServletWeb.xml";
    }

    @Test
    public void testMultiServletsConsumers() throws Exception {
        String result = getService("/services1/hello?name=Camel");
        assertEquals("Hello Camel", result);

        result = getService("/services2/echo?name=Camel");
        assertEquals("Camel Camel", result);
    }

    @Test
    public void testMultiServletsConsumersCannotAccessEachOther() throws Exception {
        try {
            getService("/services2/hello?name=Camel");
            fail("Should have thrown an exception");
        } catch (HttpNotFoundException e) {
            assertEquals(404, e.getResponseCode());
        }

        try {
            getService("/services1/echo?name=Camel");
            fail("Should have thrown an exception");
        } catch (HttpNotFoundException e) {
            assertEquals(404, e.getResponseCode());
        }
    }

    public String getService(String path) throws Exception {
        WebRequest req = new GetMethodWebRequest(CONTEXT_URL + path);
        ServletUnitClient client = newClient();
        WebResponse response = client.getResponse(req);

        return response.getText();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("servlet:/hello?servletName=CamelServlet1").transform(simple("Hello ${header.name}"));

                from("servlet:/echo?servletName=CamelServlet2").transform(simple("${header.name} ${header.name}"));
            }
        };
    }

}
