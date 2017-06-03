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
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.camel.util.jsse.SSLContextParameters;
import org.eclipse.jetty.proxy.ConnectHandler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.B64Code;
import org.eclipse.jetty.util.StringUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.eclipse.jetty.http.HttpHeader.PROXY_AUTHENTICATE;
import static org.eclipse.jetty.http.HttpHeader.PROXY_AUTHORIZATION;

@Ignore("Bug in Jetty9 causes java.lang.IllegalArgumentException: Invalid protocol login.salesforce.com")
public class HttpProxyMojoIntegrationTest extends CamelSalesforceMojoIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(HttpProxyMojoIntegrationTest.class);

    private static final String HTTP_PROXY_HOST = "localhost";
    private static final String HTTP_PROXY_USER_NAME = "camel-user";
    private static final String HTTP_PROXY_PASSWORD = "camel-user-password";
    private static final String HTTP_PROXY_REALM = "proxy-realm";

    private static Server server;
    private static int httpProxyPort;

    @BeforeClass
    public static void setupServer() throws Exception {
        // start a local HTTP proxy using Jetty server
        server = new Server();

/*
        final SSLContextParameters contextParameters = new SSLContextParameters();
        final SslContextFactory sslContextFactory = new SslContextFactory();
        sslContextFactory.setSslContext(contextParameters.createSSLContext());
        ServerConnector connector = new ServerConnector(server, sslContextFactory);
*/
        ServerConnector connector = new ServerConnector(server);

        connector.setHost(HTTP_PROXY_HOST);
        server.addConnector(connector);

        final String authenticationString = "Basic "
            + B64Code.encode(HTTP_PROXY_USER_NAME + ":" + HTTP_PROXY_PASSWORD, StringUtil.__ISO_8859_1);

        ConnectHandler connectHandler = new ConnectHandler() {
            @Override
            protected boolean handleAuthentication(HttpServletRequest request, HttpServletResponse response, String address) {
                // validate proxy-authentication header
                final String header = request.getHeader(PROXY_AUTHORIZATION.toString());
                if (!authenticationString.equals(header)) {
                    LOG.warn("Missing header " + PROXY_AUTHORIZATION);
                    // ask for authentication header
                    response.setHeader(PROXY_AUTHENTICATE.toString(), String.format("Basic realm=\"%s\"", HTTP_PROXY_REALM));
                    return false;
                }
                LOG.info("Request contains required header " + PROXY_AUTHORIZATION);
                return true;
            }
        };
        server.setHandler(connectHandler);

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
        mojo.httpProxyRealm = HTTP_PROXY_REALM;
        mojo.httpProxyAuthUri = String.format("https://%s:%s", HTTP_PROXY_HOST, httpProxyPort);

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
