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
package org.apache.camel.maven;

import java.io.IOException;
import java.util.HashMap;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.camel.util.jsse.SSLContextParameters;
import org.eclipse.jetty.http.HttpHeaders;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ConnectHandler;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.util.B64Code;
import org.eclipse.jetty.util.StringUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpProxyMojoIntegrationTest extends CamelSalesforceMojoIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(HttpProxyMojoIntegrationTest.class);

    private static final String HTTP_PROXY_HOST = "localhost";
    private static final String HTTP_PROXY_USER_NAME = "camel-user";
    private static final String HTTP_PROXY_PASSWORD = "camel-user-password";

    private static Server server;
    private static int httpProxyPort;

    @BeforeClass
    public static void setupServer() throws Exception {
        // start a local HTTP proxy using Jetty server
        server = new Server();

        Connector connector = new SelectChannelConnector();
        connector.setHost(HTTP_PROXY_HOST);
        server.setConnectors(new Connector[]{connector});

        final String authenticationString = "Basic "
            + B64Code.encode(HTTP_PROXY_USER_NAME + ":" + HTTP_PROXY_PASSWORD, StringUtil.__ISO_8859_1);

        ConnectHandler handler = new ConnectHandler() {
            @Override
            protected boolean handleAuthentication(HttpServletRequest request, HttpServletResponse response, String address) throws ServletException, IOException {
                // validate proxy-authentication header
                final String header = request.getHeader(HttpHeaders.PROXY_AUTHORIZATION);
                if (!authenticationString.equals(header)) {
                    throw new ServletException("Missing header " + HttpHeaders.PROXY_AUTHORIZATION);
                }
                LOG.info("CONNECT exchange contains required header " + HttpHeaders.PROXY_AUTHORIZATION);
                return super.handleAuthentication(request, response, address);
            }
        };
        server.setHandler(handler);

        LOG.info("Starting proxy server...");
        server.start();

        httpProxyPort = connector.getLocalPort();
        LOG.info("Started proxy server on port {}", httpProxyPort);
    }

    @Override
    protected CamelSalesforceMojo createMojo() throws IOException {
        final CamelSalesforceMojo mojo = super.createMojo();

        // SSL context parameters
        mojo.sslContextParameters = new SSLContextParameters();

        // HTTP proxy properties
        mojo.httpProxyHost = HTTP_PROXY_HOST;
        mojo.httpProxyPort = httpProxyPort;
        mojo.httpProxyUsername = HTTP_PROXY_USER_NAME;
        mojo.httpProxyPassword = HTTP_PROXY_PASSWORD;

        // HTTP client properties
        mojo.httpClientProperties = new HashMap<String, Object>();
        mojo.httpClientProperties.put("timeout", "60000");
        mojo.httpClientProperties.put("removeIdleDestinations", "true");

        return mojo;
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        // stop the proxy server after component
        LOG.info("Stopping proxy server...");
        server.stop();
        LOG.info("Stopped proxy server");
    }
}
