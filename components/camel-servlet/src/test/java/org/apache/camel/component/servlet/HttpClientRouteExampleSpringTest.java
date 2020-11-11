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

import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.ContextLoaderListener;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HttpClientRouteExampleSpringTest extends ServletCamelRouterTestSupport {
    @Test
    public void testHttpRestricMethod() throws Exception {

        // Send a web get method request
        WebRequest req = new GetMethodWebRequest(contextUrl + "/services/hello");
        WebResponse response = query(req);

        assertEquals("OK", response.getResponseMessage(), "Get a wrong response message.");
        assertEquals("Add a name parameter to uri, eg ?name=foo", response.getText(), "Get a wrong response text.");

        req = new GetMethodWebRequest(contextUrl + "/services/hello?name=Willem");
        response = query(req);
        assertEquals("Hello Willem how are you?", response.getText(), "Get a wrong response text.");
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        startCamelContext = false;
        super.setUp();
    }

    @Override
    protected DeploymentInfo getDeploymentInfo() {
        return Servlets.deployment()
                .setClassLoader(getClass().getClassLoader())
                .setContextPath(CONTEXT)
                .setDeploymentName(getClass().getName())
                .addInitParameter("contextConfigLocation",
                        "classpath:org/apache/camel/component/servlet/example-camelContext.xml")
                .addListener(Servlets.listener(ContextLoaderListener.class))
                .addServlet(Servlets.servlet("CamelServlet", CamelHttpTransportServlet.class)
                        .addInitParam("matchOnUriPrefix", "true")
                        .addMapping("/services/*"));
    }

}
