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
package org.apache.camel.dsl.jbang.core.commands.process;

import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
class ListVaultTest extends ProcessCommandTestSupport {

    @Test
    void testEmptyOutputWhenNoVaults() throws Exception {
        JsonObject root = new JsonObject();
        root.put("context", contextObj());
        writeStatusFile(TEST_PID, root);

        ListVault command = new ListVault(new CamelJBangMain().withPrinter(printer));
        command.sort = "pid";

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            assertEquals("", printer.getOutput().trim());
        }
    }

    @Test
    void testShowsAwsVault() throws Exception {
        JsonObject root = new JsonObject();
        root.put("context", contextObj());
        root.put("vaults", vaults("aws-secrets", vault("us-east-1", "aws-secret")));
        writeStatusFile(TEST_PID, root);

        ListVault command = new ListVault(new CamelJBangMain().withPrinter(printer));
        command.sort = "pid";

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            String output = printer.getOutput();
            assertTrue(output.contains("AWS"), "Should show AWS vault");
            assertTrue(output.contains("aws-secret"), "Should show AWS secret name");
        }
    }

    @Test
    void testShowsHashicorpVault() throws Exception {
        JsonObject root = new JsonObject();
        root.put("context", contextObj());
        root.put("vaults", vaults("hashicorp-secrets", hashicorpVault()));
        writeStatusFile(TEST_PID, root);

        ListVault command = new ListVault(new CamelJBangMain().withPrinter(printer));
        command.sort = "pid";

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            assertTrue(printer.getOutput().contains("Hashicorp"), "Should show Hashicorp vault");
        }
    }

    @Test
    void testShowsKubernetesConfigMapVault() throws Exception {
        JsonObject root = new JsonObject();
        root.put("context", contextObj());
        root.put("vaults", vaults("kubernetes-configmaps", configMapVault("app-config")));
        writeStatusFile(TEST_PID, root);

        ListVault command = new ListVault(new CamelJBangMain().withPrinter(printer));
        command.sort = "pid";

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            String output = printer.getOutput();
            assertTrue(output.contains("Kubernetes-cm"), "Should show Kubernetes configmap vault");
            assertTrue(output.contains("app-config"), "Should show configmap name");
        }
    }

    @Test
    void testMultipleVaultTypesEachWithOneSingleSecret() throws Exception {
        JsonObject root = new JsonObject();
        root.put("context", contextObj());
        root.put("vaults", vaults(
                "aws-secrets", vault("us-east-1", "aws-secret"),
                "gcp-secrets", vault(null, "gcp-secret"),
                "azure-secrets", vault(null, "azure-secret")));
        writeStatusFile(TEST_PID, root);

        ListVault command = new ListVault(new CamelJBangMain().withPrinter(printer));
        command.sort = "pid";

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            String output = printer.getOutput();
            // All three vault types must appear; the bug causes only the last one (Azure) to show
            assertTrue(output.contains("AWS"), "AWS row should retain its vault type");
            assertTrue(output.contains("GCP"), "GCP row should retain its vault type");
            assertTrue(output.contains("Azure"), "Azure row should retain its vault type");
        }
    }

    @Test
    void testSingleVaultWithMultipleSecrets() throws Exception {
        JsonObject root = new JsonObject();
        root.put("context", contextObj());
        root.put("vaults", vaults("aws-secrets", vault("eu-west-1", "secret-a", "secret-b", "secret-c")));
        writeStatusFile(TEST_PID, root);

        ListVault command = new ListVault(new CamelJBangMain().withPrinter(printer));
        command.sort = "pid";

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            String output = printer.getOutput();
            assertTrue(output.contains("secret-a"), "First secret should appear");
            assertTrue(output.contains("secret-b"), "Second secret should appear (requires row.copy())");
            assertTrue(output.contains("secret-c"), "Third secret should appear (requires row.copy())");
            // All three rows must carry the same vault type
            long awsCount = output.lines().filter(l -> l.contains("AWS")).count();
            assertEquals(3, awsCount, "Each secret must be associated with AWS vault");
        }
    }

    @Test
    void testJsonOutput() throws Exception {
        JsonObject root = new JsonObject();
        root.put("context", contextObj());
        root.put("vaults", vaults("aws-secrets", vault("us-east-1", "aws-secret")));
        writeStatusFile(TEST_PID, root);

        ListVault command = new ListVault(new CamelJBangMain().withPrinter(printer));
        command.sort = "pid";
        command.jsonOutput = true;

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            String output = printer.getOutput();
            assertTrue(output.startsWith("["), "JSON output should be array");
            assertTrue(output.contains("\"vault\":\"AWS\""));
            assertTrue(output.contains("aws-secret"));
        }
    }

    @Test
    void testMultipleVaultTypesEachWithOneSingleSecretJsonOutput() throws Exception {
        JsonObject root = new JsonObject();
        root.put("context", contextObj());
        root.put("vaults", vaults(
                "aws-secrets", vault("us-east-1", "aws-secret"),
                "gcp-secrets", vault(null, "gcp-secret"),
                "azure-secrets", vault(null, "azure-secret")));
        writeStatusFile(TEST_PID, root);

        ListVault command = new ListVault(new CamelJBangMain().withPrinter(printer));
        command.sort = "pid";
        command.jsonOutput = true;

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            String output = printer.getOutput();
            assertTrue(output.startsWith("["), "JSON output should be array");
            // Each vault type must appear exactly once with its own label
            assertTrue(output.contains("\"vault\":\"AWS\""), "AWS entry must retain its vault label");
            assertTrue(output.contains("\"vault\":\"GCP\""), "GCP entry must retain its vault label");
            assertTrue(output.contains("\"vault\":\"Azure\""), "Azure entry must retain its vault label");
        }
    }

    private static JsonObject contextObj() {
        JsonObject ctx = new JsonObject();
        ctx.put("name", "myApp");
        return ctx;
    }

    private static JsonObject vaults(Object... keyValues) {
        JsonObject vaults = new JsonObject();
        for (int i = 0; i < keyValues.length; i += 2) {
            vaults.put((String) keyValues[i], keyValues[i + 1]);
        }
        return vaults;
    }

    private static JsonObject vault(String region, String... secrets) {
        long now = System.currentTimeMillis();
        JsonObject vault = new JsonObject();
        if (region != null) {
            vault.put("region", region);
        }
        vault.put("lastCheckTimestamp", now - 1000);
        vault.put("lastReloadTimestamp", now - 500);
        vault.put("startCheckTimestamp", now - 1000);
        vault.put("secrets", secretArray(secrets));
        return vault;
    }

    private static JsonObject hashicorpVault() {
        long now = System.currentTimeMillis();
        JsonObject vault = new JsonObject();
        vault.put("startCheckTimestamp", now - 1000);
        vault.put("lastReloadTimestamp", now - 500);
        return vault;
    }

    private static JsonObject configMapVault(String... configMaps) {
        JsonObject vault = vault(null);
        vault.put("configmap", secretArray(configMaps));
        return vault;
    }

    private static JsonArray secretArray(String... names) {
        JsonArray arr = new JsonArray();
        for (String name : names) {
            JsonObject secret = new JsonObject();
            secret.put("name", name);
            secret.put("timestamp", System.currentTimeMillis() - 2000);
            arr.add(secret);
        }
        return arr;
    }
}
