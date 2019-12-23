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
package org.apache.camel.component.jetty;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.http.common.HttpHeaderFilterStrategy;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.eclipse.jetty.server.Server;
import org.junit.BeforeClass;

public abstract class BaseJettyTest extends CamelTestSupport {

    private static volatile int port;

    private static volatile int port2;

    private final AtomicInteger counter = new AtomicInteger(1);

    @BeforeClass
    public static void initPort() throws Exception {
        port = AvailablePortFinder.getNextAvailable();
        // find another ports for proxy route test
        port2 = AvailablePortFinder.getNextAvailable();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getPropertiesComponent().setLocation("ref:prop");
        return context;
    }

    @BindToRegistry("prop")
    public Properties loadProp() throws Exception {

        Properties prop = new Properties();
        prop.setProperty("port", "" + getPort());
        prop.setProperty("port2", "" + getPort2());
        return prop;
    }

    protected int getNextPort() {
        return AvailablePortFinder.getNextAvailable();
    }

    public void setSSLProps(JettyHttpComponent jetty, String path, String keyStorePasswd, String keyPasswd) {
        if (jettyVersion() == 9) {
            jetty.addSslSocketConnectorProperty("protocol", "TLSv1.2");
            jetty.addSslSocketConnectorProperty("keyStorePassword", keyStorePasswd);
            jetty.addSslSocketConnectorProperty("keyManagerPassword", keyPasswd);
            jetty.addSslSocketConnectorProperty("keyStorePath", path);
            jetty.addSslSocketConnectorProperty("trustStoreType", "JKS");
        } else {
            jetty.addSslSocketConnectorProperty("protocol", "TLSv1.2");
            jetty.addSslSocketConnectorProperty("password", keyStorePasswd);
            jetty.addSslSocketConnectorProperty("keyPassword", keyPasswd);
            jetty.addSslSocketConnectorProperty("keystore", path);
            jetty.addSslSocketConnectorProperty("truststoreType", "JKS");
        }
    }

    protected static int getPort() {
        return port;
    }

    protected static int getPort2() {
        return port2;
    }

    public int jettyVersion() {
        try {
            this.getClass().getClassLoader().loadClass("org.eclipse.jetty.server.ssl.SslSelectChannelConnector");
            return 8;
        } catch (ClassNotFoundException e) {
            return 9;
        }
    }

    protected void allowNullHeaders() {
        JettyHttpComponent jetty = (JettyHttpComponent)context.getComponent("jetty");
        HttpHeaderFilterStrategy filterStrat = new HttpHeaderFilterStrategy();
        filterStrat.setAllowNullValues(true);
        jetty.setHeaderFilterStrategy(filterStrat);
    }

    protected boolean isJetty8() {
        String majorVersion = Server.getVersion().split("\\.")[0];
        return "8".equals(majorVersion);
    }

}
