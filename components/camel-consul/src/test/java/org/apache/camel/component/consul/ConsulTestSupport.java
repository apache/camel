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

import org.apache.camel.BindToRegistry;
import org.apache.camel.test.infra.consul.services.ConsulService;
import org.apache.camel.test.infra.consul.services.ConsulServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.kiwiproject.consul.Consul;

public class ConsulTestSupport extends CamelTestSupport {
    @RegisterExtension
    public static ConsulService service = ConsulServiceFactory.createService();

    public static final String KV_PREFIX = "/camel";

    @BindToRegistry("consul")
    public ConsulComponent getConsulComponent() {
        ConsulComponent component = new ConsulComponent();
        component.getConfiguration().setUrl(service.getConsulUrl());
        return component;
    }

    protected Consul getConsul() {
        return Consul.builder().withUrl(service.getConsulUrl()).build();
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
}
