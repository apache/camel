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
package org.apache.camel.component.azure.key.vault;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.camel.spi.PeriodTaskScheduler;
import org.apache.camel.spi.PropertiesFunction;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.vault.AzureVaultConfiguration;

@DevConsole("azure-secrets")
public class AzureKeyVaultManagerDevConsole extends AbstractDevConsole {

    private KeyVaultPropertiesFunction propertiesFunction;
    private EventhubsReloadTriggerTask secretsRefreshTask;

    public AzureKeyVaultManagerDevConsole() {
        super("camel", "azure-secrets", "Azure Key Vault Secrets", "Azure Key Vault Secret Manager");
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (getCamelContext().getPropertiesComponent().hasPropertiesFunction("azure")) {
            PropertiesFunction pf = getCamelContext().getPropertiesComponent().getPropertiesFunction("azure");
            if (pf instanceof KeyVaultPropertiesFunction) {
                propertiesFunction = (KeyVaultPropertiesFunction) pf;
            }
        }
        AzureVaultConfiguration azure = getCamelContext().getVaultConfiguration().getAzureVaultConfiguration();
        if (azure != null && azure.isRefreshEnabled()) {
            PeriodTaskScheduler scheduler = PluginHelper.getPeriodTaskScheduler(getCamelContext());
            secretsRefreshTask = scheduler.getTaskByType(EventhubsReloadTriggerTask.class);
        }
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        if (propertiesFunction != null) {
            sb.append("Azure Key Vault Secret Manager:");
            sb.append("\n    Login: Client Id/Client Secret");
            AzureVaultConfiguration azure = getCamelContext().getVaultConfiguration().getAzureVaultConfiguration();
            if (azure != null) {
                sb.append(String.format("\n    Refresh Enabled: %s", azure.isRefreshEnabled()));
                sb.append(String.format("\n    Refresh Period: %s", azure.getRefreshPeriod()));
            }
            if (secretsRefreshTask != null) {
                Instant last = secretsRefreshTask.getLastCheckTime();
                String s = last != null ? TimeUtils.printSince(last.toEpochMilli()) : "none";
                sb.append(String.format("\n    Last Check: %s", s));
                last = secretsRefreshTask.getLastReloadTime();
                s = last != null ? TimeUtils.printSince(last.toEpochMilli()) : "none";
                sb.append(String.format("\n    Last Reload: %s", s));
            }
            sb.append("\n\nSecrets in use:");

            List<String> sorted = new ArrayList<>(propertiesFunction.getSecrets());
            Collections.sort(sorted);

            for (String sec : sorted) {
                Instant last = secretsRefreshTask != null ? secretsRefreshTask.getUpdates().get(sec) : null;
                String age = last != null ? TimeUtils.printSince(last.toEpochMilli()) : null;
                if (age != null) {
                    sb.append(String.format("\n    %s (age: %s)", sec, age));
                } else {
                    sb.append(String.format("\n    %s", sec));
                }
            }
        }

        return sb.toString();
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();
        if (propertiesFunction != null) {
            root.put("login", "Client Id/Client Secret");
            AzureVaultConfiguration azure = getCamelContext().getVaultConfiguration().getAzureVaultConfiguration();
            if (azure != null) {
                root.put("refreshEnabled", azure.isRefreshEnabled());
                root.put("refreshPeriod", azure.getRefreshPeriod());
            }
            if (secretsRefreshTask != null) {
                Instant last = secretsRefreshTask.getLastCheckTime();
                if (last != null) {
                    long timestamp = last.toEpochMilli();
                    root.put("lastCheckTimestamp", timestamp);
                    root.put("lastCheckAge", TimeUtils.printSince(timestamp));
                }
                last = secretsRefreshTask.getLastReloadTime();
                if (last != null) {
                    long timestamp = last.toEpochMilli();
                    root.put("lastReloadTimestamp", timestamp);
                    root.put("lastReloadAge", TimeUtils.printSince(timestamp));
                }
            }
            JsonArray arr = new JsonArray();
            root.put("secrets", arr);

            List<String> sorted = new ArrayList<>(propertiesFunction.getSecrets());
            Collections.sort(sorted);

            for (String sec : sorted) {
                JsonObject jo = new JsonObject();
                jo.put("name", sec);
                Instant last = secretsRefreshTask != null ? secretsRefreshTask.getUpdates().get(sec) : null;
                if (last != null) {
                    long timestamp = last.toEpochMilli();
                    jo.put("timestamp", timestamp);
                    jo.put("age", TimeUtils.printSince(timestamp));
                }
                arr.add(jo);
            }
        }
        return root;
    }
}
