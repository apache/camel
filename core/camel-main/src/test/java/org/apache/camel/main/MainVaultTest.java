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
package org.apache.camel.main;

import org.apache.camel.CamelContext;
import org.apache.camel.vault.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class MainVaultTest {

    @Test
    public void testMainAws() {
        Main main = new Main();

        main.addInitialProperty("camel.vault.aws.accessKey", "myKey");
        main.addInitialProperty("camel.vault.aws.secretKey", "mySecret");
        main.addInitialProperty("camel.vault.aws.region", "myRegion");
        main.addInitialProperty("camel.vault.aws.defaultCredentialsProvider", "false");

        main.start();

        CamelContext context = main.getCamelContext();
        assertNotNull(context);

        AwsVaultConfiguration cfg = context.getVaultConfiguration().aws();
        assertNotNull(cfg);

        Assertions.assertEquals("myKey", cfg.getAccessKey());
        Assertions.assertEquals("mySecret", cfg.getSecretKey());
        Assertions.assertEquals("myRegion", cfg.getRegion());
        Assertions.assertFalse(cfg.isDefaultCredentialsProvider());

        main.stop();
    }

    @Test
    public void testMainOverrideEndpointAws() {
        Main main = new Main();

        main.addInitialProperty("camel.vault.aws.accessKey", "myKey");
        main.addInitialProperty("camel.vault.aws.secretKey", "mySecret");
        main.addInitialProperty("camel.vault.aws.region", "myRegion");
        main.addInitialProperty("camel.vault.aws.defaultCredentialsProvider", "false");
        main.addInitialProperty("camel.vault.aws.overrideEndpoint", "true");
        main.addInitialProperty("camel.vault.aws.uriEndpointOverride", "http://localhost:8080");

        main.start();

        CamelContext context = main.getCamelContext();
        assertNotNull(context);

        AwsVaultConfiguration cfg = context.getVaultConfiguration().aws();
        assertNotNull(cfg);

        Assertions.assertEquals("myKey", cfg.getAccessKey());
        Assertions.assertEquals("mySecret", cfg.getSecretKey());
        Assertions.assertEquals("myRegion", cfg.getRegion());
        Assertions.assertFalse(cfg.isDefaultCredentialsProvider());
        Assertions.assertTrue(cfg.isOverrideEndpoint());
        Assertions.assertEquals("http://localhost:8080", cfg.getUriEndpointOverride());
        main.stop();
    }

    @Test
    public void testMainProfileAws() {
        final Main main = getMain();

        CamelContext context = main.getCamelContext();
        assertNotNull(context);

        AwsVaultConfiguration cfg = context.getVaultConfiguration().aws();
        assertNotNull(cfg);

        Assertions.assertEquals("myKey", cfg.getAccessKey());
        Assertions.assertEquals("mySecret", cfg.getSecretKey());
        Assertions.assertEquals("myRegion", cfg.getRegion());
        Assertions.assertFalse(cfg.isDefaultCredentialsProvider());
        Assertions.assertTrue(cfg.isProfileCredentialsProvider());
        Assertions.assertEquals("jack", cfg.getProfileName());

        main.stop();
    }

    private static Main getMain() {
        Main main = new Main();

        main.addInitialProperty("camel.vault.aws.accessKey", "myKey");
        main.addInitialProperty("camel.vault.aws.secretKey", "mySecret");
        main.addInitialProperty("camel.vault.aws.region", "myRegion");
        main.addInitialProperty("camel.vault.aws.defaultCredentialsProvider", "false");
        main.addInitialProperty("camel.vault.aws.profileCredentialsProvider", "true");
        main.addInitialProperty("camel.vault.aws.profileName", "jack");
        main.addInitialProperty("camel.vault.aws.sqsQueueUrl", "http://sqs-2");
        main.addInitialProperty("camel.vault.aws.useSqsNotification", "true");

        main.start();
        return main;
    }

    @Test
    public void testUseSqsNotification() {
        final Main main = getMain();

        CamelContext context = main.getCamelContext();
        assertNotNull(context);

        AwsVaultConfiguration cfg = context.getVaultConfiguration().aws();
        assertNotNull(cfg);

        Assertions.assertEquals("myKey", cfg.getAccessKey());
        Assertions.assertEquals("mySecret", cfg.getSecretKey());
        Assertions.assertEquals("myRegion", cfg.getRegion());
        Assertions.assertFalse(cfg.isDefaultCredentialsProvider());
        Assertions.assertTrue(cfg.isProfileCredentialsProvider());
        Assertions.assertEquals("jack", cfg.getProfileName());
        Assertions.assertEquals("http://sqs-2", cfg.getSqsQueueUrl());
        Assertions.assertTrue(cfg.isUseSqsNotification());

        main.stop();
    }

    @Test
    public void testMainOverrideEndpointAwsFluent() {
        Main main = new Main();
        main.configure().vault().aws()
                .withAccessKey("myKey")
                .withSecretKey("mySecret")
                .withRegion("myRegion")
                .isOverrideEndpoint(true)
                .withUriEndpointOverride("http://localhost:8080")
                .withDefaultCredentialsProvider(false)
                .end();

        main.start();

        CamelContext context = main.getCamelContext();
        assertNotNull(context);

        AwsVaultConfiguration cfg = context.getVaultConfiguration().aws();
        assertNotNull(cfg);

        Assertions.assertEquals("myKey", cfg.getAccessKey());
        Assertions.assertEquals("mySecret", cfg.getSecretKey());
        Assertions.assertEquals("myRegion", cfg.getRegion());
        Assertions.assertFalse(cfg.isDefaultCredentialsProvider());
        Assertions.assertTrue(cfg.isOverrideEndpoint());
        Assertions.assertEquals("http://localhost:8080", cfg.getUriEndpointOverride());

        main.stop();
    }

    @Test
    public void testMainAwsFluent() {
        Main main = new Main();
        main.configure().vault().aws()
                .withAccessKey("myKey")
                .withSecretKey("mySecret")
                .withRegion("myRegion")
                .withDefaultCredentialsProvider(false)
                .end();

        main.start();

        CamelContext context = main.getCamelContext();
        assertNotNull(context);

        AwsVaultConfiguration cfg = context.getVaultConfiguration().aws();
        assertNotNull(cfg);

        Assertions.assertEquals("myKey", cfg.getAccessKey());
        Assertions.assertEquals("mySecret", cfg.getSecretKey());
        Assertions.assertEquals("myRegion", cfg.getRegion());
        Assertions.assertFalse(cfg.isDefaultCredentialsProvider());

        main.stop();
    }

    @Test
    public void testMainAwsProfileFluent() {
        Main main = new Main();
        main.configure().vault().aws()
                .withAccessKey("myKey")
                .withSecretKey("mySecret")
                .withRegion("myRegion")
                .withDefaultCredentialsProvider(false)
                .withProfileCredentialsProvider(true)
                .withProfileName("jack")
                .end();

        main.start();

        CamelContext context = main.getCamelContext();
        assertNotNull(context);

        AwsVaultConfiguration cfg = context.getVaultConfiguration().aws();
        assertNotNull(cfg);

        Assertions.assertEquals("myKey", cfg.getAccessKey());
        Assertions.assertEquals("mySecret", cfg.getSecretKey());
        Assertions.assertEquals("myRegion", cfg.getRegion());
        Assertions.assertFalse(cfg.isDefaultCredentialsProvider());
        Assertions.assertTrue(cfg.isProfileCredentialsProvider());
        Assertions.assertEquals("jack", cfg.getProfileName());

        main.stop();
    }

    @Test
    public void testMainGcp() {
        Main main = new Main();

        main.addInitialProperty("camel.vault.gcp.serviceAccountKey", "file:////myKey");
        main.addInitialProperty("camel.vault.gcp.projectId", "gcp-project");

        main.start();

        CamelContext context = main.getCamelContext();
        assertNotNull(context);

        GcpVaultConfiguration cfg = context.getVaultConfiguration().gcp();
        assertNotNull(cfg);

        Assertions.assertEquals("file:////myKey", cfg.getServiceAccountKey());
        Assertions.assertEquals("gcp-project", cfg.getProjectId());
        Assertions.assertFalse(cfg.isUseDefaultInstance());
        main.stop();
    }

    @Test
    public void testMainGcpFluent() {
        Main main = new Main();
        main.configure().vault().gcp()
                .withServiceAccountKey("file:////myKey")
                .withProjectId("gcp-project")
                .end();

        main.start();

        CamelContext context = main.getCamelContext();
        assertNotNull(context);

        GcpVaultConfiguration cfg = context.getVaultConfiguration().gcp();
        assertNotNull(cfg);

        Assertions.assertEquals("file:////myKey", cfg.getServiceAccountKey());
        Assertions.assertEquals("gcp-project", cfg.getProjectId());
        Assertions.assertFalse(cfg.isUseDefaultInstance());
        main.stop();
    }

    @Test
    public void testMainAzure() {
        Main main = new Main();

        main.addInitialProperty("camel.vault.azure.vaultName", "vault");
        main.addInitialProperty("camel.vault.azure.clientId", "id1");
        main.addInitialProperty("camel.vault.azure.clientSecret", "secret1");
        main.addInitialProperty("camel.vault.azure.tenantId", "tenant1");
        main.start();

        CamelContext context = main.getCamelContext();
        assertNotNull(context);

        AzureVaultConfiguration cfg = context.getVaultConfiguration().azure();
        assertNotNull(cfg);

        Assertions.assertEquals("vault", cfg.getVaultName());
        Assertions.assertEquals("id1", cfg.getClientId());
        Assertions.assertEquals("secret1", cfg.getClientSecret());
        Assertions.assertEquals("tenant1", cfg.getTenantId());
        Assertions.assertFalse(cfg.isAzureIdentityEnabled());
        main.stop();
    }

    @Test
    public void testMainAzureFluent() {
        Main main = new Main();
        main.configure().vault().azure()
                .withVaultName("vault")
                .withClientId("id1")
                .withClientSecret("secret1")
                .withTenantId("tenant1")
                .end();

        main.start();

        CamelContext context = main.getCamelContext();
        assertNotNull(context);

        AzureVaultConfiguration cfg = context.getVaultConfiguration().azure();
        assertNotNull(cfg);

        Assertions.assertEquals("vault", cfg.getVaultName());
        Assertions.assertEquals("id1", cfg.getClientId());
        Assertions.assertEquals("secret1", cfg.getClientSecret());
        Assertions.assertEquals("tenant1", cfg.getTenantId());
        Assertions.assertFalse(cfg.isAzureIdentityEnabled());
        main.stop();
    }

    @Test
    public void testMainHashicorp() {
        Main main = new Main();

        main.addInitialProperty("camel.vault.hashicorp.token", "1111");
        main.addInitialProperty("camel.vault.hashicorp.host", "localhost");
        main.addInitialProperty("camel.vault.hashicorp.port", "8200");
        main.addInitialProperty("camel.vault.hashicorp.scheme", "https");
        main.start();

        CamelContext context = main.getCamelContext();
        assertNotNull(context);

        HashicorpVaultConfiguration cfg = context.getVaultConfiguration().hashicorp();
        assertNotNull(cfg);

        Assertions.assertEquals("1111", cfg.getToken());
        Assertions.assertEquals("localhost", cfg.getHost());
        Assertions.assertEquals("8200", cfg.getPort());
        Assertions.assertEquals("https", cfg.getScheme());
        Assertions.assertFalse(cfg.isCloud());
        Assertions.assertNull(cfg.getNamespace());
        main.stop();
    }

    public void testMainHashicorpWithCloud() {
        Main main = new Main();

        main.addInitialProperty("camel.vault.hashicorp.token", "1111");
        main.addInitialProperty("camel.vault.hashicorp.host", "localhost");
        main.addInitialProperty("camel.vault.hashicorp.port", "8200");
        main.addInitialProperty("camel.vault.hashicorp.scheme", "https");
        main.addInitialProperty("camel.vault.hashicorp.cloud", "true");
        main.addInitialProperty("camel.vault.hashicorp.namespace", "admin");
        main.start();

        CamelContext context = main.getCamelContext();
        assertNotNull(context);

        HashicorpVaultConfiguration cfg = context.getVaultConfiguration().hashicorp();
        assertNotNull(cfg);

        Assertions.assertEquals("1111", cfg.getToken());
        Assertions.assertEquals("localhost", cfg.getHost());
        Assertions.assertEquals("8200", cfg.getPort());
        Assertions.assertEquals("https", cfg.getScheme());
        Assertions.assertTrue(cfg.isCloud());
        Assertions.assertEquals("admin", cfg.getNamespace());
        main.stop();
    }

    @Test
    public void testMainKubernetes() {
        Main main = new Main();

        main.addInitialProperty("camel.vault.kubernetes.refreshEnabled", "true");
        main.addInitialProperty("camel.vault.kubernetes.secrets", "xxxx");

        main.start();

        CamelContext context = main.getCamelContext();
        assertNotNull(context);

        KubernetesVaultConfiguration cfg = context.getVaultConfiguration().kubernetes();
        assertNotNull(cfg);

        Assertions.assertTrue(cfg.isRefreshEnabled());
        Assertions.assertEquals("xxxx", cfg.getSecrets());
        main.stop();
    }

    @Test
    public void testMainKubernetesFluent() {
        Main main = new Main();
        main.configure().vault().kubernetes()
                .withRefreshEnabled(true)
                .withSecrets("xxxx")
                .end();

        main.start();

        CamelContext context = main.getCamelContext();
        assertNotNull(context);

        KubernetesVaultConfiguration cfg = context.getVaultConfiguration().kubernetes();
        assertNotNull(cfg);

        Assertions.assertTrue(cfg.isRefreshEnabled());
        Assertions.assertEquals("xxxx", cfg.getSecrets());
        main.stop();
    }

    @Test
    public void testMainKubernetesConfigmaps() {
        Main main = new Main();

        main.addInitialProperty("camel.vault.kubernetescm.refreshEnabled", "true");
        main.addInitialProperty("camel.vault.kubernetescm.configmaps", "xxxx");

        main.start();

        CamelContext context = main.getCamelContext();
        assertNotNull(context);

        KubernetesConfigMapVaultConfiguration cfg = context.getVaultConfiguration().kubernetesConfigmaps();
        assertNotNull(cfg);

        Assertions.assertTrue(cfg.isRefreshEnabled());
        Assertions.assertEquals("xxxx", cfg.getConfigmaps());
        main.stop();
    }

    @Test
    public void testMainKubernetesConfigmapsFluent() {
        Main main = new Main();
        main.configure().vault().kubernetesConfigmaps()
                .withRefreshEnabled(true)
                .withConfigmaps("xxxx")
                .end();

        main.start();

        CamelContext context = main.getCamelContext();
        assertNotNull(context);

        KubernetesConfigMapVaultConfiguration cfg = context.getVaultConfiguration().kubernetesConfigmaps();
        assertNotNull(cfg);

        Assertions.assertTrue(cfg.isRefreshEnabled());
        Assertions.assertEquals("xxxx", cfg.getConfigmaps());
        main.stop();
    }

    @Test
    public void testMainIBMSecretsManager() {
        Main main = new Main();

        main.addInitialProperty("camel.vault.ibm.serviceUrl", "http://ibm.cloud.com");
        main.addInitialProperty("camel.vault.ibm.token", "token");

        main.start();

        CamelContext context = main.getCamelContext();
        assertNotNull(context);

        IBMSecretsManagerVaultConfiguration cfg = context.getVaultConfiguration().ibmSecretsManager();
        assertNotNull(cfg);

        Assertions.assertEquals("token", cfg.getToken());
        Assertions.assertEquals("http://ibm.cloud.com", cfg.getServiceUrl());
        main.stop();
    }

    @Test
    public void testMainIBMFluent() {
        Main main = new Main();
        main.configure().vault().ibmSecretsManager()
                .withToken("token")
                .withServiceUrl("http://ibm.cloud.com")
                .end();

        main.start();

        CamelContext context = main.getCamelContext();
        assertNotNull(context);

        IBMSecretsManagerVaultConfiguration cfg = context.getVaultConfiguration().ibmSecretsManager();
        assertNotNull(cfg);

        Assertions.assertEquals("token", cfg.getToken());
        Assertions.assertEquals("http://ibm.cloud.com", cfg.getServiceUrl());
        main.stop();
    }
}
