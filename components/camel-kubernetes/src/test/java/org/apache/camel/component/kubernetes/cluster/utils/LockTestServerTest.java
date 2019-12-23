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
package org.apache.camel.component.kubernetes.cluster.utils;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Basic tests on the lock test server.
 */
public class LockTestServerTest {

    @Test
    public void test() {
        ConfigMapLockSimulator lock = new ConfigMapLockSimulator("xxx");
        LockTestServer server = new LockTestServer(lock);
        KubernetesClient client = server.createClient();

        assertNull(client.configMaps().withName("xxx").get());

        client.configMaps().withName("xxx").createNew().withNewMetadata().withName("xxx").and().done();

        try {
            client.configMaps().withName("xxx").createNew().withNewMetadata().withName("xxx").and().done();
            Assert.fail("Should have failed for duplicate insert");
        } catch (Exception e) {
        }

        client.configMaps().withName("xxx").createOrReplaceWithNew().editOrNewMetadata().withName("xxx").addToLabels("a", "b").and().done();

        ConfigMap map = client.configMaps().withName("xxx").get();
        assertEquals("b", map.getMetadata().getLabels().get("a"));

        client.configMaps().withName("xxx").lockResourceVersion(map.getMetadata().getResourceVersion())
            .replace(new ConfigMapBuilder(map).editOrNewMetadata().withName("xxx").addToLabels("c", "d").and().build());

        ConfigMap newMap = client.configMaps().withName("xxx").get();
        assertEquals("d", newMap.getMetadata().getLabels().get("c"));

        try {
            client.configMaps().withName("xxx").lockResourceVersion(map.getMetadata().getResourceVersion())
                .replace(new ConfigMapBuilder(map).editOrNewMetadata().withName("xxx").addToLabels("e", "f").and().build());
            Assert.fail("Should have failed for wrong version");
        } catch (Exception ex) {
        }

        ConfigMap newMap2 = client.configMaps().withName("xxx").get();
        assertNull(newMap2.getMetadata().getLabels().get("e"));

    }

}
