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

import java.text.MessageFormat;

import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.ContextLoaderListener;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ServletAsyncTest extends ServletCamelRouterTestSupport {

    @Test
    public void testHello() throws Exception {
        final String name = "Arnaud";

        WebRequest req = new GetMethodWebRequest(contextUrl + "/services/hello");
        req.setParameter("name", name);
        WebResponse response = query(req);

        assertEquals(200, response.getResponseCode());
        assertEquals(MessageFormat.format("Hello {0} how are you?", name), response.getText(),
                "The response message is wrong");
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
                        .addInitParam("async", "true")
                        .setLoadOnStartup(1)
                        .setAsyncSupported(true)
                        .addMapping("/services/*"));
    }

}
