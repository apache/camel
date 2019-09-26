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
package org.apache.camel.component.ahc;

import java.util.Properties;

import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.support.jsse.ClientAuthentication;
import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.SSLContextServerParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeAll;

public abstract class BaseAhcTest extends CamelTestSupport {

    protected static final String KEY_STORE_PASSWORD = "changeit";

    private static volatile int port;

    @BeforeAll
    public static void initPort() throws Exception {
        port = AvailablePortFinder.getNextAvailable();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getPropertiesComponent().setLocation("ref:prop");
        return context;
    }
    
    @BindToRegistry("prop")
    public Properties addProperties() {
        Properties prop = new Properties();
        prop.setProperty("port", "" + getPort());
        return prop;
    }

    @BindToRegistry("sslContextParameters") 
    public SSLContextParameters createSSLContextParameters() {
        KeyStoreParameters ksp = new KeyStoreParameters();
        ksp.setResource(this.getClass().getClassLoader().getResource("jsse/localhost.p12").toString());
        ksp.setPassword(KEY_STORE_PASSWORD);

        KeyManagersParameters kmp = new KeyManagersParameters();
        kmp.setKeyPassword(KEY_STORE_PASSWORD);
        kmp.setKeyStore(ksp);

        TrustManagersParameters tmp = new TrustManagersParameters();
        tmp.setKeyStore(ksp);

        // NOTE: Needed since the client uses a loose trust configuration when no ssl context
        // is provided.  We turn on WANT client-auth to prefer using authentication
        SSLContextServerParameters scsp = new SSLContextServerParameters();
        scsp.setClientAuthentication(ClientAuthentication.WANT.name());

        SSLContextParameters sslContextParameters = new SSLContextParameters();
        sslContextParameters.setKeyManagers(kmp);
        sslContextParameters.setTrustManagers(tmp);
        sslContextParameters.setServerParameters(scsp);

        return sslContextParameters;
    }

    /**
     * Indicates if the URIs returned from {@link #getTestServerEndpointUri()} and
     * {@link #getAhcEndpointUri()} should use the HTTPS protocol instead of
     * the HTTP protocol.
     *
     * If true, an {@link SSLContextParameters} is also placed in the registry under the
     * key {@code sslContextParameters}.  The parameters are not added to the endpoint URIs
     * as that is test specific.
     *
     * @return false by default
     */
    protected boolean isHttps() {
        return false;
    }

    protected String getProtocol() {
        String protocol = "http";
        if (isHttps()) {
            protocol = protocol + "s";
        }

        return protocol;
    }

    protected String getTestServerEndpointUrl() {
        return getProtocol() + "://localhost:{{port}}/foo";
    }

    protected String getTestServerEndpointUri() {
        return "jetty:" + getTestServerEndpointUrl();
    }

    protected String getTestServerEndpointTwoUrl() {
        // Don't use the property placeholder here since we use the value outside of a
        // field that supports the placeholders.
        return getProtocol() + "://localhost:" + getPort() + "/bar";
    }

    protected String getTestServerEndpointTwoUri() {
        return "jetty:" + getTestServerEndpointTwoUrl();
    }

    protected String getAhcEndpointUri() {
        return "ahc:" + getProtocol() + "://localhost:{{port}}/foo";
    }

    protected synchronized int getNextPort() {
        port = AvailablePortFinder.getNextAvailable();
        return port;
    }

    protected int getPort() {
        return port;
    }
}
