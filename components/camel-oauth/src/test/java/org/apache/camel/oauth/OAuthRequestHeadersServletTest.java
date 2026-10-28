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
package org.apache.camel.oauth;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentManager;
import org.apache.camel.CamelContext;
import org.apache.camel.component.servlet.CamelHttpTransportServlet;

/**
 * The OAuth processors behind camel-servlet.
 */
class OAuthRequestHeadersServletTest extends AbstractOAuthRequestHeadersTest {

    // a servlet name of its own, so that other servlet tests of the module do not share the servlet registry entry
    private static final String SERVLET_NAME = "OAuthRequestHeadersServlet";

    private DeploymentManager manager;
    private Undertow server;

    @Override
    void startServer(CamelContext context, int port) throws Exception {
        var deploymentInfo = Servlets.deployment()
                .setContextPath("/")
                .setDeploymentName(SERVLET_NAME)
                .setClassLoader(OAuthRequestHeadersServletTest.class.getClassLoader())
                .addServlet(Servlets.servlet(SERVLET_NAME, CamelHttpTransportServlet.class).addMapping("/*"));
        manager = Servlets.newContainer().addDeployment(deploymentInfo);
        manager.deploy();
        var path = Handlers.path(Handlers.redirect("/")).addPrefixPath("/", manager.start());
        server = Undertow.builder().addHttpListener(port, "127.0.0.1").setHandler(path).build();
        server.start();
    }

    @Override
    void stopServer() throws Exception {
        if (manager != null) {
            manager.stop();
            manager.undeploy();
        }
        if (server != null) {
            server.stop();
        }
    }

    @Override
    String endpointUri(String path) {
        return "servlet:" + path + "?servletName=" + SERVLET_NAME;
    }
}
