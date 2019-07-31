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
package org.apache.camel.component.servlet.rest;

import java.io.ByteArrayInputStream;

import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;
import com.meterware.servletunit.ServletUnitClient;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.servlet.ServletCamelRouterTestSupport;
import org.apache.camel.model.rest.RestBindingMode;
import org.junit.Test;

public class RestServletPojoInOutTest extends ServletCamelRouterTestSupport {

    @Test
    public void testServletPojoInOut() throws Exception {
        String body = "{\"id\": 123, \"name\": \"Donald Duck\"}";
        WebRequest req = new PostMethodWebRequest(CONTEXT_URL + "/services/users/lives", new ByteArrayInputStream(body.getBytes()), "application/json");
        ServletUnitClient client = newClient();
        client.setExceptionsThrownOnErrorStatus(false);
        WebResponse response = client.getResponse(req);

        assertEquals(200, response.getResponseCode());

        String out = response.getText();

        assertNotNull(out);
        assertEquals("{\"iso\":\"EN\",\"country\":\"England\"}", out);
    }
    
    @Test
    public void testServletPojoGet() throws Exception {
        
        WebRequest req = new GetMethodWebRequest(CONTEXT_URL + "/services/users/lives");
        ServletUnitClient client = newClient();
        client.setExceptionsThrownOnErrorStatus(false);
        WebResponse response = client.getResponse(req);

        assertEquals(200, response.getResponseCode());

        String out = response.getText();

        assertNotNull(out);
        assertEquals("{\"iso\":\"EN\",\"country\":\"England\"}", out);
    }   

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // configure to use servlet and enable auto binding mode
                restConfiguration().component("servlet").bindingMode(RestBindingMode.auto);

                // use the rest DSL to define the rest services
                rest("/users/")
                    // just return the default country here
                    .get("lives").to("direct:start")
                    .post("lives").type(UserPojo.class).outType(CountryPojo.class)
                        .route()
                        .bean(new UserService(), "livesWhere");
                
                CountryPojo country = new CountryPojo();
                country.setIso("EN");
                country.setCountry("England");
                from("direct:start").transform().constant(country);
            }
        };
    }

}
