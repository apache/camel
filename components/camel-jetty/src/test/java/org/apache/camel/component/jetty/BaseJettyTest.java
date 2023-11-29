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
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.http.common.HttpHeaderFilterStrategy;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.eclipse.jetty.server.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class BaseJettyTest extends CamelTestSupport {

    public static final String SSL_SYSPROPS = "SslSystemProperties";

    static final CopyOnWriteArrayList<String> runningTests = new CopyOnWriteArrayList<>();

    @RegisterExtension
    protected AvailablePortFinder.Port port1 = AvailablePortFinder.find();

    @RegisterExtension
    protected AvailablePortFinder.Port port2 = AvailablePortFinder.find();

    @BeforeEach
    void addRunningTest() {
        runningTests.add(getClass().getName());
    }

    @AfterEach
    void remRunningTest() {
        runningTests.remove(getClass().getName());
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getPropertiesComponent().setLocation("ref:prop");
        return context;
    }

    @BindToRegistry("prop")
    public Properties loadProp() {
        Properties prop = new Properties();
        prop.setProperty("port", "" + getPort());
        prop.setProperty("port2", "" + getPort2());
        return prop;
    }

    public void setSSLProps(JettyHttpComponent jetty, String path, String keyStorePasswd, String keyPasswd) {
        jetty.addSslSocketConnectorProperty("protocol", "TLSv1.3");
        jetty.addSslSocketConnectorProperty("keyStorePassword", keyStorePasswd);
        jetty.addSslSocketConnectorProperty("keyManagerPassword", keyPasswd);
        jetty.addSslSocketConnectorProperty("keyStorePath", path);
        jetty.addSslSocketConnectorProperty("trustStoreType", "JKS");
    }

    protected int getPort() {
        return port1.getPort();
    }

    protected int getPort2() {
        return port2.getPort();
    }

    protected void allowNullHeaders() {
        JettyHttpComponent jetty = (JettyHttpComponent) context.getComponent("jetty");
        HttpHeaderFilterStrategy filterStrat = new HttpHeaderFilterStrategy();
        filterStrat.setAllowNullValues(true);
        jetty.setHeaderFilterStrategy(filterStrat);
    }

    protected boolean isJetty8() {
        String majorVersion = Server.getVersion().split("\\.")[0];
        return "8".equals(majorVersion);
    }

}
