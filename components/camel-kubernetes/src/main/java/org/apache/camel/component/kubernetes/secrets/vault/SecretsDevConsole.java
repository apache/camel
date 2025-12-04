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

package org.apache.camel.component.kubernetes.secrets.vault;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.camel.component.kubernetes.properties.SecretPropertiesFunction;
import org.apache.camel.spi.PeriodTaskScheduler;
import org.apache.camel.spi.PropertiesFunction;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.vault.KubernetesVaultConfiguration;

@DevConsole(name = "kubernetes-secrets", displayName = "Kubernetes Secrets", description = "Kubernetes Cluster Secrets")
public class SecretsDevConsole extends AbstractDevConsole {

    private SecretPropertiesFunction propertiesFunction;
    private SecretsReloadTriggerTask secretsRefreshTask;

    public SecretsDevConsole() {
        super("camel", "kubernetes-secrets", "Kubernetes Secrets", "Kubernetes Cluster Secrets");
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (getCamelContext().getPropertiesComponent().hasPropertiesFunction("secret")) {
            PropertiesFunction pf = getCamelContext().getPropertiesComponent().getPropertiesFunction("secret");
            if (pf instanceof SecretPropertiesFunction) {
                propertiesFunction = (SecretPropertiesFunction) pf;
            }
        }
        KubernetesVaultConfiguration kubernetes =
                getCamelContext().getVaultConfiguration().getKubernetesVaultConfiguration();
        if (kubernetes != null && kubernetes.isRefreshEnabled()) {
            PeriodTaskScheduler scheduler = PluginHelper.getPeriodTaskScheduler(getCamelContext());
            secretsRefreshTask = scheduler.getTaskByType(SecretsReloadTriggerTask.class);
        }
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        if (propertiesFunction != null) {
            sb.append("Kubernetes Secrets Cluster:");
            KubernetesClient client = propertiesFunction.getClient();
            if (client != null && client.getMasterUrl() != null) {
                sb.append(String.format(
                        "\n    Master Url: %s", client.getMasterUrl().toString()));
                sb.append("\n    Login: OAuth Token");
            }
            KubernetesVaultConfiguration kubernetes =
                    getCamelContext().getVaultConfiguration().getKubernetesVaultConfiguration();
            if (kubernetes != null) {
                sb.append(String.format("\n    Refresh Enabled: %s", kubernetes.isRefreshEnabled()));
            }
            if (secretsRefreshTask != null) {
                Instant start = secretsRefreshTask.getStartingTime();
                String s = start != null ? TimeUtils.printSince(start.toEpochMilli()) : "none";
                sb.append(String.format("\n    Running Since: %s", s));
            }
            List<String> sorted = new ArrayList<>();
            if (kubernetes != null) {
                sb.append("\n\nSecrets in use:");

                sorted = new ArrayList<>(List.of(kubernetes.getSecrets().split(",")));
                Collections.sort(sorted);
            }

            for (String sec : sorted) {
                sb.append(String.format("\n    %s", sec));
            }
        }

        return sb.toString();
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();
        if (propertiesFunction != null) {
            KubernetesClient client = propertiesFunction.getClient();
            if (client != null && client.getMasterUrl() != null) {
                root.put("masterUrl", client.getMasterUrl().toString());
                root.put("login", "OAuth Token");
            }
        }
        KubernetesVaultConfiguration kubernetes =
                getCamelContext().getVaultConfiguration().getKubernetesVaultConfiguration();
        if (kubernetes != null) {
            root.put("refreshEnabled", kubernetes.isRefreshEnabled());
        }
        if (secretsRefreshTask != null) {
            Instant start = secretsRefreshTask.getStartingTime();
            if (start != null) {
                long timestamp = start.toEpochMilli();
                root.put("startCheckTimestamp", timestamp);
            }
        }
        JsonArray arr = new JsonArray();
        root.put("secrets", arr);

        List<String> sorted = new ArrayList<>(List.of(kubernetes.getSecrets().split(",")));
        Collections.sort(sorted);

        for (String sec : sorted) {
            JsonObject jo = new JsonObject();
            jo.put("name", sec);
            arr.add(jo);
        }
        return root;
    }
}
