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
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConsulTestSupport extends CamelTestSupport {
    public static final Logger LOGGER = LoggerFactory.getLogger(ConsulTestSupport.class);
    public static final String KV_PREFIX = "/camel";

    @Rule
    public final TestName testName = new TestName();

    protected Consul getConsul() {
        return Consul.builder().build();
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
