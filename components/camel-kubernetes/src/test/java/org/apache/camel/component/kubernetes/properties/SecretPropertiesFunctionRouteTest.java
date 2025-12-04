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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
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
    @EnabledIfSystemProperty(
            named = "kubernetes.test.host.k8s",
            matches = "true",
            disabledReason = "Requires kubernetes"),
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SecretPropertiesFunctionRouteTest extends KubernetesTestSupport {

    private KubernetesClient client;
    private Secret sec;

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .transform()
                        .simple("Connect with {{secret:mysecret/myuser}}:{{secret:mysecret/mypass}}");
                from("direct:binary").transform().simple("File saved to {{secret-binary:mysecret/binary.dat}}");
            }
        };
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        ConfigBuilder builder = new ConfigBuilder();
        builder.withOauthToken(authToken);
        builder.withMasterUrl(host);
        client = new KubernetesClientBuilder().withConfig(builder.build()).build();
        context.getRegistry().bind("KubernetesClient", client);

        Map<String, String> data = Map.of(
                "myuser",
                Base64.getEncoder().encodeToString("scott".getBytes(StandardCharsets.UTF_8)),
                "mypass",
                Base64.getEncoder().encodeToString("tiger".getBytes(StandardCharsets.UTF_8)),
                "binary.dat",
                Base64.getEncoder().encodeToString(readExampleBinaryFile()));
        Secret sec = new SecretBuilder()
                .editOrNewMetadata()
                .withName("mysecret")
                .endMetadata()
                .withData(data)
                .build();
        this.sec = client.resource(sec).serverSideApply();

        return context;
    }

    @Override
    public void doPostTearDown() {
        if (client != null && sec != null) {
            try {
                client.resource(sec).delete();
            } catch (Exception e) {
                // ignore
            }
        }
    }

    @Test
    @Order(1)
    public void secretPropertiesFunction() {
        String out = template.requestBody("direct:start", null, String.class);
        Assertions.assertEquals("Connect with scott:tiger", out);
    }

    @Test
    @Order(2)
    public void binarySecretPropertiesFunction() throws IOException {
        String out = template.requestBody("direct:binary", null, String.class);
        Assertions.assertTrue(out.matches("File saved to .*binary.dat"));
        Path filePath = Path.of(out.substring("File saved to ".length()));
        Assertions.assertArrayEquals(readExampleBinaryFile(), Files.readAllBytes(filePath));
        Files.deleteIfExists(filePath);
    }
}
