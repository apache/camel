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
package org.apache.camel.component.kubernetes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class KubernetesConfigurationTest {

    @Test
    public void toStringShouldNotLeakCredentials() {
        KubernetesConfiguration configuration = new KubernetesConfiguration();
        configuration.setMasterUrl("https://localhost:6443");
        configuration.setUsername("someone");
        configuration.setPassword("s3cret");
        configuration.setOauthToken("0authT0ken");
        configuration.setCaCertData("caCertD4ta");
        configuration.setClientCertData("clientCertD4ta");
        configuration.setClientKeyData("clientKeyD4ta");
        configuration.setClientKeyPassphrase("passphr4se");
        configuration.setClientKeyFile("/etc/kubernetes/client.key");

        String actual = configuration.toString();

        assertFalse(actual.contains("s3cret"), actual);
        assertFalse(actual.contains("0authT0ken"), actual);
        assertFalse(actual.contains("caCertD4ta"), actual);
        assertFalse(actual.contains("clientCertD4ta"), actual);
        assertFalse(actual.contains("clientKeyD4ta"), actual);
        assertFalse(actual.contains("passphr4se"), actual);

        assertTrue(actual.contains("password=xxxxxx"), actual);
        assertTrue(actual.contains("oauthToken=xxxxxx"), actual);
        assertTrue(actual.contains("clientKeyData=xxxxxx"), actual);
        // the paths that point at the material, and the cluster being talked to, stay readable
        assertTrue(actual.contains("clientKeyFile=/etc/kubernetes/client.key"), actual);
        assertTrue(actual.contains("masterUrl=https://localhost:6443"), actual);
    }

}
