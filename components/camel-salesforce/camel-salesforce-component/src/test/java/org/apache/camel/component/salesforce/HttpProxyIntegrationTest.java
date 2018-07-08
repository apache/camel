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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.salesforce.api.dto.Version;
import org.apache.camel.component.salesforce.api.dto.Versions;
import org.eclipse.jetty.proxy.ConnectHandler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.B64Code;
import org.eclipse.jetty.util.StringUtil;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.eclipse.jetty.http.HttpHeader.PROXY_AUTHENTICATE;
import static org.eclipse.jetty.http.HttpHeader.PROXY_AUTHORIZATION;

/**
 * Test HTTP proxy configuration for Salesforce component.
 */
@RunWith(Parameterized.class)
public class HttpProxyIntegrationTest extends AbstractSalesforceTestBase {

    private static final Logger LOG = LoggerFactory.getLogger(HttpProxyIntegrationTest.class);
    private static final String HTTP_PROXY_HOST = "localhost";
    private static final String HTTP_PROXY_USER_NAME = "camel-user";
    private static final String HTTP_PROXY_PASSWORD = "camel-user-password";
    private static final String HTTP_PROXY_REALM = "proxy-realm";

    private static Server server;
    private static int httpProxyPort;

    private static final AtomicBoolean WENT_TORUGH_PROXY = new AtomicBoolean();

    @Parameter(0)
    public Consumer<SalesforceComponent> configurationMethod;

    @Parameters
    public static Iterable<Consumer<SalesforceComponent>> methods() {
        return Arrays.asList(HttpProxyIntegrationTest::configureProxyViaComponentProperties,
            HttpProxyIntegrationTest::configureProxyViaClientPropertiesMap);
    }

    @Test
    public void testGetVersions() throws Exception {
        doTestGetVersions("");
        doTestGetVersions("Xml");

        assertTrue("Should have gone through the test proxy", WENT_TORUGH_PROXY.get());
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
                WENT_TORUGH_PROXY.set(true);
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
    protected void createComponent() throws Exception {

        super.createComponent();
        final SalesforceComponent salesforce = (SalesforceComponent) context().getComponent("salesforce");

        // set HTTP client properties
        final HashMap<String, Object> properties = new HashMap<>();
        properties.put("timeout", "60000");
        properties.put("removeIdleDestinations", "true");
        salesforce.setHttpClientProperties(properties);

        configurationMethod.accept(salesforce);
    }

    @Override
    public void cleanupResources() throws Exception {
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

    private static void configureProxyViaComponentProperties(final SalesforceComponent salesforce) {
        salesforce.setHttpProxyHost(HTTP_PROXY_HOST);
        salesforce.setHttpProxyPort(httpProxyPort);
        salesforce.setIsHttpProxySecure(false);
        salesforce.setHttpProxyUsername(HTTP_PROXY_USER_NAME);
        salesforce.setHttpProxyPassword(HTTP_PROXY_PASSWORD);
        salesforce.setHttpProxyAuthUri(String.format("http://%s:%s", HTTP_PROXY_HOST, httpProxyPort));
        salesforce.setHttpProxyRealm(HTTP_PROXY_REALM);
    }

    private static void configureProxyViaClientPropertiesMap(final SalesforceComponent salesforce) {
        final Map<String, Object> properties = salesforce.getHttpClientProperties();
        properties.put(SalesforceComponent.HTTP_PROXY_HOST, HTTP_PROXY_HOST);
        properties.put(SalesforceComponent.HTTP_PROXY_PORT, httpProxyPort);
        properties.put(SalesforceComponent.HTTP_PROXY_IS_SECURE, false);
        properties.put(SalesforceComponent.HTTP_PROXY_USERNAME, HTTP_PROXY_USER_NAME);
        properties.put(SalesforceComponent.HTTP_PROXY_PASSWORD, HTTP_PROXY_PASSWORD);
        properties.put(SalesforceComponent.HTTP_PROXY_AUTH_URI, String.format("http://%s:%s", HTTP_PROXY_HOST, httpProxyPort));
        properties.put(SalesforceComponent.HTTP_PROXY_REALM, HTTP_PROXY_REALM);
    }

}
