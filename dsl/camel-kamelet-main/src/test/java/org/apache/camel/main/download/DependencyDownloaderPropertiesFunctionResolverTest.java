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
package org.apache.camel.main.download;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.impl.engine.SimpleCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for CAMEL-24776: a normal run (not only export) must auto-download the JAR that backs a secret
 * properties function - e.g. camel-kubernetes for {@code {{secret:...}}} and {@code {{configmap:...}}} - so the
 * function can be resolved. Otherwise the placeholder is silently parsed as a key with a default value and resolves to
 * the wrong literal.
 */
public class DependencyDownloaderPropertiesFunctionResolverTest {

    private static final String KUBERNETES = "org.apache.camel:camel-kubernetes";
    private static final String BASE64 = "org.apache.camel:camel-base64";
    private static final String AWS = "org.apache.camel:camel-aws-secrets-manager";
    private static final String AZURE = "org.apache.camel:camel-azure-key-vault";
    private static final String GCP = "org.apache.camel:camel-google-secret-manager";
    private static final String HASHICORP = "org.apache.camel:camel-hashicorp-vault";

    @Test
    void runDownloadsKubernetesForSecretFunction() {
        assertTrue(resolveAndRecordDownloads(false, false, "secret").contains(KUBERNETES),
                "run must auto-download camel-kubernetes for the secret function");
    }

    @Test
    void runDownloadsKubernetesForConfigmapFunction() {
        assertTrue(resolveAndRecordDownloads(false, false, "configmap").contains(KUBERNETES),
                "run must auto-download camel-kubernetes for the configmap function");
    }

    @Test
    void runDownloadsCamelBase64ForBase64Function() {
        assertTrue(resolveAndRecordDownloads(false, false, "base64").contains(BASE64),
                "run must auto-download camel-base64 for the base64 function");
    }

    @Test
    void runDownloadsAwsSecretsManagerForAwsFunction() {
        assertTrue(resolveAndRecordDownloads(false, false, "aws").contains(AWS),
                "run must auto-download camel-aws-secrets-manager for the aws function");
    }

    @Test
    void runDownloadsAzureKeyVaultForAzureFunction() {
        assertTrue(resolveAndRecordDownloads(false, false, "azure").contains(AZURE),
                "run must auto-download camel-azure-key-vault for the azure function");
    }

    @Test
    void runDownloadsGoogleSecretManagerForGcpFunction() {
        assertTrue(resolveAndRecordDownloads(false, false, "gcp").contains(GCP),
                "run must auto-download camel-google-secret-manager for the gcp function");
    }

    @Test
    void runDownloadsHashicorpVaultForHashicorpFunction() {
        assertTrue(resolveAndRecordDownloads(false, false, "hashicorp").contains(HASHICORP),
                "run must auto-download camel-hashicorp-vault for the hashicorp function");
    }

    @Test
    void exportStillDownloadsKubernetesForSecretFunction() {
        assertTrue(resolveAndRecordDownloads(true, false, "secret").contains(KUBERNETES),
                "export must keep auto-downloading camel-kubernetes for the secret function");
    }

    @Test
    void transformStubsWithoutDownloading() {
        assertEquals(List.of(), resolveAndRecordDownloads(false, true, "secret"),
                "transform must stub the function without downloading any dependency");
    }

    private static List<String> resolveAndRecordDownloads(boolean export, boolean transform, String function) {
        List<String> downloaded = new ArrayList<>();
        try (SimpleCamelContext context = new SimpleCamelContext()) {
            context.addService(recordingDownloader(context, downloaded));

            DependencyDownloaderPropertiesFunctionResolver resolver
                    = new DependencyDownloaderPropertiesFunctionResolver(context, export, transform);
            // the backing dependency is not on the test classpath, so the function itself will not
            // resolve; we only assert whether the download was attempted
            resolver.resolvePropertiesFunction(function);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return downloaded;
    }

    /**
     * A {@link DependencyDownloader} test double that records {@code downloadDependency(groupId,
     * artifactId, version)} calls and reports nothing as already on the classpath, so the download branch is exercised.
     * Implemented as a dynamic proxy to avoid stubbing the whole downloader SPI.
     */
    private static DependencyDownloader recordingDownloader(SimpleCamelContext context, List<String> downloaded) {
        return (DependencyDownloader) Proxy.newProxyInstance(
                DependencyDownloaderPropertiesFunctionResolverTest.class.getClassLoader(),
                new Class[] { DependencyDownloader.class },
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "downloadDependency" -> {
                            // every overload starts with (String groupId, String artifactId, String version, ...)
                            downloaded.add(args[0] + ":" + args[1]);
                            return null;
                        }
                        case "alreadyOnClasspath" -> {
                            return false;
                        }
                        case "getCamelContext" -> {
                            return context;
                        }
                        default -> {
                            return method.getReturnType() == boolean.class ? Boolean.FALSE : null;
                        }
                    }
                });
    }
}
