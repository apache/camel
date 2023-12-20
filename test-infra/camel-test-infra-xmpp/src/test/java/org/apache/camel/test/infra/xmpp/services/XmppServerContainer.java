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
package org.apache.camel.test.infra.xmpp.services;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.xmpp.common.XmppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

public class XmppServerContainer extends GenericContainer {

    private static final Logger LOGGER = LoggerFactory.getLogger(XmppServerContainer.class);
    private static final String CONTAINER_NAME = "vysper-wrapper";
    private static final Integer PORT_REST = 8080;

    public XmppServerContainer() {
        super(LocalPropertyResolver.getProperty(XmppServerContainer.class, XmppProperties.XMPP_CONTAINER));
        setWaitStrategy(Wait.forListeningPort());
        withExposedPorts(XmppProperties.PORT_DEFAULT, PORT_REST);
        withNetworkAliases(CONTAINER_NAME);
        withLogConsumer(new Slf4jLogConsumer(LOGGER));
        waitingFor(Wait.forLogMessage(".*Started Application in.*", 1));
    }

    public String getUrl() {
        return String.format("%s:%d", this.getHost(), this.getMappedPort(XmppProperties.PORT_DEFAULT));
    }

    public void stopXmppEndpoint() throws IOException {
        get("stop-tcp");
    }

    public void startXmppEndpoint() throws IOException {
        get("start-tcp");
    }

    private void get(String urlAppendix) throws IOException {
        URL url = new URL(
                String.format("http://%s:%d/%s", this.getHost(), getMappedPort(PORT_REST), urlAppendix));
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.getInputStream();
        conn.disconnect();
    }

    public int getPortDefault() {
        return getMappedPort(XmppProperties.PORT_DEFAULT);
    }
}
