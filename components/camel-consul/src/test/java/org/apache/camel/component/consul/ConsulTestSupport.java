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
package org.apache.camel.component.consul;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.orbitz.consul.Consul;
import com.orbitz.consul.KeyValueClient;
import org.apache.camel.BindToRegistry;
import org.apache.camel.test.testcontainers.junit5.ContainerAwareTestSupport;
import org.apache.camel.test.testcontainers.junit5.Wait;
import org.testcontainers.containers.GenericContainer;

public class ConsulTestSupport extends ContainerAwareTestSupport {
    public static final String CONTAINER_IMAGE = "consul:1.6.2";
    public static final String CONTAINER_NAME = "consul";
    public static final String KV_PREFIX = "/camel";

    @BindToRegistry("consul")
    public ConsulComponent getConsulComponent() {
        ConsulComponent component = new ConsulComponent();
        component.getConfiguration().setUrl(consulUrl());
        return component;
    }

    protected Consul getConsul() {
        return Consul.builder().withUrl(consulUrl()).build();
    }

    protected KeyValueClient getKeyValueClient() {
        return getConsul().keyValueClient();
    }

    protected String generateRandomString() {
        return UUID.randomUUID().toString();
    }

    protected String[] generateRandomArrayOfStrings(int size) {
        String[] array = new String[size];
        Arrays.setAll(array, i -> generateRandomString());

        return array;
    }

    protected List<String> generateRandomListOfStrings(int size) {
        return Arrays.asList(generateRandomArrayOfStrings(size));
    }

    protected String generateKey() {
        return KV_PREFIX + "/" + getCurrentTestName() + "/" + generateRandomString();
    }

    protected String consulUrl() {
        return String.format("http://%s:%d", getContainerHost(CONTAINER_NAME), getContainerPort(CONTAINER_NAME, Consul.DEFAULT_HTTP_PORT));
    }

    @Override
    protected GenericContainer<?> createContainer() {
        return consulContainer();
    }

    public static GenericContainer consulContainer() {
        return new GenericContainer(CONTAINER_IMAGE).withNetworkAliases(CONTAINER_NAME).withExposedPorts(Consul.DEFAULT_HTTP_PORT)
            .waitingFor(Wait.forLogMessageContaining("Synced node info", 1)).withCommand("agent", "-dev", "-server", "-bootstrap", "-client", "0.0.0.0", "-log-level", "trace");
    }
}
