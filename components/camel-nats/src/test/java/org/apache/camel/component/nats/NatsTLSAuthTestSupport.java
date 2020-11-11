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
package org.apache.camel.component.nats;

import org.apache.camel.CamelContext;
import org.apache.camel.test.testcontainers.junit5.ContainerAwareTestSupport;
import org.apache.camel.test.testcontainers.junit5.Wait;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;

/*Certificates used for tests with TLS authentication come from:
 *https://github.com/nats-io/jnats/tree/master/src/test/resources */
public class NatsTLSAuthTestSupport extends ContainerAwareTestSupport {

    public static final String CONTAINER_IMAGE = "nats:2.1.8";
    public static final String CONTAINER_NAME = "nats-tls";

    @Override
    protected GenericContainer<?> createContainer() {
        return natsContainer();
    }

    public static GenericContainer natsContainer() {
        return new GenericContainer(CONTAINER_IMAGE)
                .withNetworkAliases(CONTAINER_NAME)
                .withClasspathResourceMapping("org/apache/camel/component/nats", "/nats", BindMode.READ_ONLY)
                .waitingFor(Wait.forLogMessageContaining("Server is ready", 1))
                .withCommand(
                        "--tls",
                        "--tlscert=/nats/server.pem",
                        "--tlskey=/nats/key.pem",
                        "--tlsverify",
                        "--tlscacert=/nats/ca.pem");
    }

    public String getNatsBrokerUrl() {
        return String.format(
                "%s:%d",
                getContainerHost(CONTAINER_NAME),
                getContainerPort(CONTAINER_NAME, 4222));
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        NatsComponent nats = context.getComponent("nats", NatsComponent.class);
        nats.setServers(getNatsBrokerUrl());
        return context;
    }

}
