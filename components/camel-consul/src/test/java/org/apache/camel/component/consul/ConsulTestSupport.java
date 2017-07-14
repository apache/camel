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
package org.apache.camel.component.consul;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.orbitz.consul.Consul;
import com.orbitz.consul.KeyValueClient;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Rule;
import org.junit.rules.TestName;

public class ConsulTestSupport extends CamelTestSupport {
    public static final String CONSUL_HOST = System.getProperty("camel.consul.host", Consul.DEFAULT_HTTP_HOST);
    public static final int CONSUL_PORT = Integer.getInteger("camel.consul.port", Consul.DEFAULT_HTTP_PORT);
    public static final String CONSUL_URL = String.format("http://%s:%d", CONSUL_HOST, CONSUL_PORT);
    public static final String KV_PREFIX = "/camel";

    @Rule
    public final TestName testName = new TestName();

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();

        ConsulComponent component = new ConsulComponent();
        component.setUrl(CONSUL_URL);

        registry.bind("consul", component);

        return registry;
    }

    protected Consul getConsul() {
        return Consul.builder().withUrl(CONSUL_URL).build();
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
        return KV_PREFIX + "/" + testName.getMethodName() + "/" + generateRandomString();
    }
}
