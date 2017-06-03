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
package org.apache.camel.component.restlet;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.junit.Test;

/**
 * @version 
 */
public class RestRestletComponentConfigurationTest extends RestletTestSupport {

    @Test
    public void testRestletPojoInOut() throws Exception {
        String body = "{\"id\": 123, \"name\": \"Donald Duck\"}";
        String out = template.requestBody("http://localhost:" + portNum + "/users/lives", body, String.class);

        assertNotNull(out);
        assertEquals("{\"iso\":\"EN\",\"country\":\"England\"}", out);

        // verify rest configuration was propagated to restlet component
        RestletComponent component = context.getComponent("restlet", RestletComponent.class);
        assertEquals(false, component.getControllerDaemon());
        assertEquals((Integer) 3, component.getMaxThreads());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // configure to use restlet on localhost with the given port
                // and enable auto binding mode
                restConfiguration()
                        .component("restlet")
                        .host("localhost").port(portNum)
                        .bindingMode(RestBindingMode.auto)
                        .componentProperty("controllerDaemon", "false")
                        .componentProperty("maxThreads", "3");

                // use the rest DSL to define the rest services
                rest("/users/")
                    .post("lives").type(UserPojo.class).outType(CountryPojo.class)
                        .route()
                        .bean(new UserService(), "livesWhere");
            }
        };
    }

}
