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
package org.apache.camel.component.ibm.secrets.manager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.camel.component.ibm.secrets.manager.vault.IBMEventStreamReloadTriggerTask;
import org.apache.camel.spi.PeriodTaskScheduler;
import org.apache.camel.spi.PropertiesFunction;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.vault.IBMSecretsManagerVaultConfiguration;

@DevConsole(name = "ibm-secrets", displayName = "IBM Secrets", description = "IBM Secrets Manager")
public class SecretsDevConsole extends AbstractDevConsole {

    private IBMSecretsManagerPropertiesFunction propertiesFunction;
    private IBMEventStreamReloadTriggerTask secretsRefreshTask;

    public SecretsDevConsole() {
        super("camel", "ibm-secrets", "IBM Secrets", "IBM Secrets Manager");
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (getCamelContext().getPropertiesComponent().hasPropertiesFunction("ibm")) {
            PropertiesFunction pf = getCamelContext().getPropertiesComponent().getPropertiesFunction("ibm");
            if (pf instanceof IBMSecretsManagerPropertiesFunction ibmPropertiesFunction) {
                propertiesFunction = ibmPropertiesFunction;
            }
        }
        IBMSecretsManagerVaultConfiguration ibm
                = getCamelContext().getVaultConfiguration().getIBMSecretsManagerVaultConfiguration();
        if (ibm != null && ibm.isRefreshEnabled()) {
            PeriodTaskScheduler scheduler = PluginHelper.getPeriodTaskScheduler(getCamelContext());
            secretsRefreshTask = scheduler.getTaskByType(IBMEventStreamReloadTriggerTask.class);
        }
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        if (propertiesFunction != null) {
            IBMSecretsManagerVaultConfiguration ibm
                    = getCamelContext().getVaultConfiguration().getIBMSecretsManagerVaultConfiguration();
            sb.append("IBM Secrets Manager:");
            if (ibm != null) {
                sb.append(String.format("%n    Service URL: %s", ibm.getServiceUrl()));
                sb.append("\n    Login: IAM Token");
                sb.append(String.format("%n    Refresh Enabled: %s", ibm.isRefreshEnabled()));
                if (ibm.isRefreshEnabled()) {
                    sb.append(String.format("%n    Event Stream Topic: %s", ibm.getEventStreamTopic()));
                    sb.append(String.format("%n    Event Stream Bootstrap Servers: %s",
                            ibm.getEventStreamBootstrapServers()));
                }
            }
            if (secretsRefreshTask != null) {
                Instant last = secretsRefreshTask.getLastCheckTime();
                String s = last != null ? TimeUtils.printSince(last.toEpochMilli()) : "none";
                sb.append(String.format("%n    Last Check: %s", s));
                last = secretsRefreshTask.getLastReloadTime();
                s = last != null ? TimeUtils.printSince(last.toEpochMilli()) : "none";
                sb.append(String.format("%n    Last Reload: %s", s));
            }
            sb.append("\n\nSecrets in use:");

            List<String> sorted = new ArrayList<>(propertiesFunction.getSecrets());
            Collections.sort(sorted);

            for (String sec : sorted) {
                Instant last = secretsRefreshTask != null ? secretsRefreshTask.getUpdates().get(sec) : null;
                String age = last != null ? TimeUtils.printSince(last.toEpochMilli()) : null;
                if (age != null) {
                    sb.append(String.format("%n    %s (age: %s)", sec, age));
                } else {
                    sb.append(String.format("%n    %s", sec));
                }
            }
        }

        return sb.toString();
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();

        if (propertiesFunction != null) {
            IBMSecretsManagerVaultConfiguration ibm
                    = getCamelContext().getVaultConfiguration().getIBMSecretsManagerVaultConfiguration();
            if (ibm != null) {
                root.put("serviceUrl", ibm.getServiceUrl());
                root.put("login", "IAM Token");
                root.put("refreshEnabled", ibm.isRefreshEnabled());
                if (ibm.isRefreshEnabled()) {
                    root.put("eventStreamTopic", ibm.getEventStreamTopic());
                    root.put("eventStreamBootstrapServers", ibm.getEventStreamBootstrapServers());
                }
            }
            if (secretsRefreshTask != null) {
                Instant last = secretsRefreshTask.getLastCheckTime();
                if (last != null) {
                    root.put("lastCheckTimestamp", last.toEpochMilli());
                    root.put("lastCheckAge", TimeUtils.printSince(last.toEpochMilli()));
                }
                last = secretsRefreshTask.getLastReloadTime();
                if (last != null) {
                    root.put("lastReloadTimestamp", last.toEpochMilli());
                    root.put("lastReloadAge", TimeUtils.printSince(last.toEpochMilli()));
                }
            }

            JsonArray arr = new JsonArray();
            List<String> sorted = new ArrayList<>(propertiesFunction.getSecrets());
            Collections.sort(sorted);

            for (String sec : sorted) {
                JsonObject jo = new JsonObject();
                jo.put("name", sec);
                Instant last = secretsRefreshTask != null ? secretsRefreshTask.getUpdates().get(sec) : null;
                if (last != null) {
                    jo.put("timestamp", last.toEpochMilli());
                    jo.put("age", TimeUtils.printSince(last.toEpochMilli()));
                }
                arr.add(jo);
            }
            if (!arr.isEmpty()) {
                root.put("secrets", arr);
            }
        }

        return root;
    }
}
