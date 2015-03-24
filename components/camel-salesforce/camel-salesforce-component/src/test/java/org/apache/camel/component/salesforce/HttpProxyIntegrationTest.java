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
package org.apache.camel.component.salesforce;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.salesforce.api.dto.Version;
import org.apache.camel.component.salesforce.api.dto.Versions;
import org.eclipse.jetty.http.HttpHeaders;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ConnectHandler;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.util.B64Code;
import org.eclipse.jetty.util.StringUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test HTTP proxy configuration for Salesforce component.
 */
public class HttpProxyIntegrationTest extends AbstractSalesforceTestBase {

    private static final Logger LOG = LoggerFactory.getLogger(HttpProxyIntegrationTest.class);
    private static final String HTTP_PROXY_HOST = "localhost";
    private static final String HTTP_PROXY_USER_NAME = "camel-user";
    private static final String HTTP_PROXY_PASSWORD = "camel-user-password";

    private static Server server;
    private static int httpProxyPort;

    @Test
    public void testGetVersions() throws Exception {
        doTestGetVersions("");
        doTestGetVersions("Xml");
    }

    @SuppressWarnings("unchecked")
    private void doTestGetVersions(String suffix) throws Exception {
        // test getVersions doesn't need a body
        // assert expected result
        Object o = template().requestBody("direct:getVersions" + suffix, (Object) null);
        List<Version> versions = null;
        if (o instanceof Versions) {
            versions = ((Versions) o).getVersions();
        } else {
            versions = (List<Version>) o;
        }
        assertNotNull(versions);
        LOG.debug("Versions: {}", versions);
    }

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
    protected void createComponent() throws Exception {

        super.createComponent();
        final SalesforceComponent salesforce = (SalesforceComponent) context().getComponent("salesforce");

        // set HTTP proxy settings
        salesforce.setHttpProxyHost(HTTP_PROXY_HOST);
        salesforce.setHttpProxyPort(httpProxyPort);
        salesforce.setHttpProxyUsername(HTTP_PROXY_USER_NAME);
        salesforce.setHttpProxyPassword(HTTP_PROXY_PASSWORD);

        // set HTTP client properties
        final HashMap<String, Object> properties = new HashMap<String, Object>();
        properties.put("timeout", "60000");
        properties.put("removeIdleDestinations", "true");
        salesforce.setHttpClientProperties(properties);
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        AbstractSalesforceTestBase.tearDownAfterClass();
        // stop the proxy server after component
        LOG.info("Stopping proxy server...");
        server.stop();
        LOG.info("Stopped proxy server");
    }

    @Override
    protected RouteBuilder doCreateRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                // testGetVersion
                from("direct:getVersions")
                    .to("salesforce:getVersions");

                // allow overriding format per endpoint
                from("direct:getVersionsXml")
                    .to("salesforce:getVersions?format=XML");

            }
        };
    }
}
