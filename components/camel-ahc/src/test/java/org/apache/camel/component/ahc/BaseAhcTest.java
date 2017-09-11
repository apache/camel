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
package org.apache.camel.component.ahc;

import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.jsse.ClientAuthentication;
import org.apache.camel.util.jsse.KeyManagersParameters;
import org.apache.camel.util.jsse.KeyStoreParameters;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.apache.camel.util.jsse.SSLContextServerParameters;
import org.apache.camel.util.jsse.TrustManagersParameters;
import org.junit.BeforeClass;

public abstract class BaseAhcTest extends CamelTestSupport {
    
    protected static final String KEY_STORE_PASSWORD = "changeit";
    
    private static volatile int port;

    @BeforeClass
    public static void initPort() throws Exception {
        port = AvailablePortFinder.getNextAvailable(24000);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.addComponent("properties", new PropertiesComponent("ref:prop"));
        return context;
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();

        Properties prop = new Properties();
        prop.setProperty("port", "" + getPort());
        jndi.bind("prop", prop);
        
        if (isHttps()) {
            addSslContextParametersToRegistry(jndi);
        }

        return jndi;
    }
    
    protected void addSslContextParametersToRegistry(JndiRegistry registry) {
        registry.bind("sslContextParameters", createSSLContextParameters());
    }

    protected SSLContextParameters createSSLContextParameters() {
        KeyStoreParameters ksp = new KeyStoreParameters();
        ksp.setResource(this.getClass().getClassLoader().getResource("jsse/localhost.ks").toString());
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
        // use SSLv3 to avoid issue with (eg disable TLS)
        // Caused by: javax.net.ssl.SSLException: bad record MAC
        sslContextParameters.setSecureSocketProtocol("SSLv3");

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
        port = AvailablePortFinder.getNextAvailable(port + 1);
        return port;
    }

    protected int getPort() {
        return port;
    }
}
