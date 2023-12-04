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

import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.xmpp.common.XmppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XmppLocalContainerService implements XmppService, ContainerService<XmppServerContainer> {
    private static final Logger LOG = LoggerFactory.getLogger(XmppLocalContainerService.class);

    private XmppServerContainer container;

    public XmppLocalContainerService() {
        this(LocalPropertyResolver.getProperty(XmppServerContainer.class, XmppProperties.XMPP_CONTAINER));
    }

    public XmppLocalContainerService(String imageName) {
        container = initContainer(imageName);
    }

    public XmppLocalContainerService(XmppServerContainer container) {
        this.container = container;
    }

    protected XmppServerContainer initContainer(String imageName) {
        return new XmppServerContainer();
    }

    @Override
    public void registerProperties() {
        System.setProperty(XmppProperties.XMPP_URL, getUrl());
        System.setProperty(XmppProperties.XMPP_HOST, container.getHost());
        System.setProperty(XmppProperties.XMPP_PORT, String.valueOf(container.getPortDefault()));
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the Xmpp container");
        container.start();

        registerProperties();
        LOG.info("Xmpp instance running at {}", getUrl());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the Xmpp container");
        container.stop();
    }

    @Override
    public XmppServerContainer getContainer() {
        return container;
    }

    @Override
    public String getUrl() {
        return container.getUrl();
    }

    @Override
    public String host() {
        return container.getHost();
    }

    @Override
    public int port() {
        return container.getPortDefault();
    }
}
