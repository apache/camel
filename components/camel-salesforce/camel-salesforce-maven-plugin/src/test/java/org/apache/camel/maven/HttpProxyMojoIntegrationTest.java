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

import org.apache.camel.test.AvailablePortFinder;
import org.junit.After;
import org.junit.Before;
import org.littleshoot.proxy.HttpProxyServer;
import org.littleshoot.proxy.ProxyAuthenticator;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;

public class HttpProxyMojoIntegrationTest extends CamelSalesforceMojoIntegrationTest {

    private static final String HTTP_PROXY_PASSWORD = "camel-user-password";

    private static final String HTTP_PROXY_REALM = "proxy-realm";

    private static final String HTTP_PROXY_USER_NAME = "camel-user";

    private int httpProxyPort;

    private HttpProxyServer proxy;

    @Before
    public void startProxy() {
        httpProxyPort = AvailablePortFinder.getNextAvailable();

        proxy = DefaultHttpProxyServer.bootstrap().withPort(httpProxyPort)
            .withProxyAuthenticator(new ProxyAuthenticator() {
                @Override
                public String getRealm() {
                    return HTTP_PROXY_REALM;
                }

                @Override
                public boolean authenticate(String userName, String password) {
                    return HTTP_PROXY_USER_NAME.equals(userName) && HTTP_PROXY_PASSWORD.equals(password);
                }
            }).start();
    }

    @After
    public void stopProxy() {
        proxy.stop();
    }

    @Override
    protected GenerateMojo createMojo() throws IOException {
        final GenerateMojo mojo = super.createMojo();

        // HTTP proxy properties
        mojo.httpProxyHost = "localhost";
        mojo.httpProxyPort = httpProxyPort;
        mojo.httpProxyUsername = HTTP_PROXY_USER_NAME;
        mojo.httpProxyPassword = HTTP_PROXY_PASSWORD;
        mojo.httpProxyRealm = HTTP_PROXY_REALM;
        mojo.isHttpProxySecure = false;
        mojo.httpProxyAuthUri = String.format("http://localhost:%s", httpProxyPort);

        // HTTP client properties
        mojo.httpClientProperties = new HashMap<>();
        mojo.httpClientProperties.put("timeout", "60000");
        mojo.httpClientProperties.put("removeIdleDestinations", "true");

        return mojo;
    }
}
