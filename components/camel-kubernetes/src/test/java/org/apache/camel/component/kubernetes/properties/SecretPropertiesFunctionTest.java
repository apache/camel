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
package org.apache.camel.component.kubernetes.properties;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.apache.camel.component.kubernetes.KubernetesTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "kubernetes.test.auth", matches = ".*", disabledReason = "Requires kubernetes"),
        @EnabledIfSystemProperty(named = "kubernetes.test.host", matches = ".*", disabledReason = "Requires kubernetes"),
        @EnabledIfSystemProperty(named = "kubernetes.test.host.k8s", matches = "true", disabledReason = "Requires kubernetes"),
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SecretPropertiesFunctionTest extends KubernetesTestSupport {

    @Test
    @Order(1)
    public void secretPropertiesFunction() throws Exception {
        ConfigBuilder builder = new ConfigBuilder();
        builder.withOauthToken(authToken);
        builder.withMasterUrl(host);

        KubernetesClient client = new KubernetesClientBuilder().withConfig(builder.build()).build();

        Map<String, String> data
                = Map.of("myuser", Base64.getEncoder().encodeToString("scott".getBytes(StandardCharsets.UTF_8)),
                        "mypass", Base64.getEncoder().encodeToString("tiger".getBytes(StandardCharsets.UTF_8)));
        Secret sec = new SecretBuilder().editOrNewMetadata().withName("mysecret").endMetadata().withData(data).build();
        client.resource(sec).serverSideApply();

        try (SecretPropertiesFunction cmf = new SecretPropertiesFunction()) {
            cmf.setClient(client);
            cmf.setCamelContext(context);
            cmf.start();

            String out = cmf.apply("mysecret/myuser");
            Assertions.assertEquals("scott", out);

            out = cmf.apply("mysecret/unknown");
            Assertions.assertNull(out);

            out = cmf.apply("mysecret/unknown:444");
            Assertions.assertEquals("444", out);

            out = cmf.apply("mysecret/mypass");
            Assertions.assertEquals("tiger", out);
        } finally {
            client.resource(sec).delete();
        }
    }

}
