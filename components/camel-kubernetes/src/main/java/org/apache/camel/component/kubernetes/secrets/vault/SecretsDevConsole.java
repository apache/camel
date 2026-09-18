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
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.PeriodTaskScheduler;
import org.apache.camel.spi.PropertiesFunction;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonRecordSupport;
import org.apache.camel.vault.KubernetesVaultConfiguration;

@DevConsole(name = "kubernetes-secrets", displayName = "Kubernetes Secrets", description = "Kubernetes Cluster Secrets")
public class SecretsDevConsole extends AbstractDevConsole {

    private SecretPropertiesFunction propertiesFunction;
    private SecretsReloadTriggerTask secretsRefreshTask;

    public record SecretEntry(@Metadata(description = "The secret name") String name) {
    }

    public record Response(
            @Metadata(description = "The Kubernetes master URL (only present when configured)") String masterUrl,
            @Metadata(description = "The login method (only present when configured)") String login,
            @Metadata(description = "Whether secret refresh is enabled (only present when configured)") Boolean refreshEnabled,
            @Metadata(description = "Epoch time in milliseconds the refresh task started (only present when known)") Long startCheckTimestamp,
            @Metadata(description = "The secrets in use") List<SecretEntry> secrets) {
    }

    public SecretsDevConsole() {
        super("camel", "kubernetes-secrets", "Kubernetes Secrets", "Kubernetes Cluster Secrets");
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (getCamelContext().getPropertiesComponent().hasPropertiesFunction("secret")) {
            PropertiesFunction pf = getCamelContext().getPropertiesComponent().getPropertiesFunction("secret");
            if (pf instanceof SecretPropertiesFunction secretpropertiesfunction) {
                propertiesFunction = secretpropertiesfunction;
            }
        }
        KubernetesVaultConfiguration kubernetes = getCamelContext().getVaultConfiguration().getKubernetesVaultConfiguration();
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
                sb.append(String.format("%n    Master Url: %s", client.getMasterUrl().toString()));
                sb.append("\n    Login: OAuth Token");
            }
            KubernetesVaultConfiguration kubernetes
                    = getCamelContext().getVaultConfiguration().getKubernetesVaultConfiguration();
            if (kubernetes != null) {
                sb.append(String.format("%n    Refresh Enabled: %s", kubernetes.isRefreshEnabled()));
            }
            if (secretsRefreshTask != null) {
                Instant start = secretsRefreshTask.getStartingTime();
                String s = start != null ? TimeUtils.printSince(start.toEpochMilli()) : "none";
                sb.append(String.format("%n    Running Since: %s", s));
            }
            List<String> sorted = new ArrayList<>();
            if (kubernetes != null) {
                sb.append("\n\nSecrets in use:");

                sorted = new ArrayList<>(List.of(kubernetes.getSecrets().split(",")));
                Collections.sort(sorted);
            }

            for (String sec : sorted) {
                sb.append(String.format("%n    %s", sec));
            }
        }

        return sb.toString();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        String masterUrl = null;
        String login = null;
        if (propertiesFunction != null) {
            KubernetesClient client = propertiesFunction.getClient();
            if (client != null && client.getMasterUrl() != null) {
                masterUrl = client.getMasterUrl().toString();
                login = "OAuth Token";
            }
        }
        KubernetesVaultConfiguration kubernetes = getCamelContext().getVaultConfiguration().getKubernetesVaultConfiguration();
        Boolean refreshEnabled = null;
        if (kubernetes != null) {
            refreshEnabled = kubernetes.isRefreshEnabled();
        }
        Long startCheckTimestamp = null;
        if (secretsRefreshTask != null) {
            Instant start = secretsRefreshTask.getStartingTime();
            if (start != null) {
                startCheckTimestamp = start.toEpochMilli();
            }
        }

        // NOTE: kubernetes is dereferenced unconditionally here, same as the original code - preserved
        // as-is rather than fixed, since this migration is about the response contract
        List<String> sorted = new ArrayList<>(List.of(kubernetes.getSecrets().split(",")));
        Collections.sort(sorted);

        List<SecretEntry> secrets = new ArrayList<>();
        for (String sec : sorted) {
            secrets.add(new SecretEntry(sec));
        }

        Response response = new Response(masterUrl, login, refreshEnabled, startCheckTimestamp, secrets);
        return JsonRecordSupport.toJsonObject(response);
    }
}
