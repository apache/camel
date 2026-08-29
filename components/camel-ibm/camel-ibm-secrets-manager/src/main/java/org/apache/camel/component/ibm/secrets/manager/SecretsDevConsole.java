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
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.PeriodTaskScheduler;
import org.apache.camel.spi.PropertiesFunction;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonRecordSupport;
import org.apache.camel.vault.IBMSecretsManagerVaultConfiguration;

@DevConsole(name = "ibm-secrets", displayName = "IBM Secrets", description = "IBM Secrets Manager")
public class SecretsDevConsole extends AbstractDevConsole {

    private IBMSecretsManagerPropertiesFunction propertiesFunction;
    private IBMEventStreamReloadTriggerTask secretsRefreshTask;

    public record SecretEntry(
            @Metadata(description = "The secret name") String name,
            @Metadata(description = "Epoch time in milliseconds of the last update (only present when known)") Long timestamp,
            @Metadata(description = "Relative age of the last update (only present when known)") String age) {
    }

    public record Response(
            @Metadata(description = "The IBM Secrets Manager service URL (only present when configured)") String serviceUrl,
            @Metadata(description = "The login method (only present when configured)") String login,
            @Metadata(description = "Whether secret refresh is enabled (only present when configured)") Boolean refreshEnabled,
            @Metadata(description = "The event stream topic (only present when refresh is enabled)") String eventStreamTopic,
            @Metadata(description = "The event stream bootstrap servers (only present when refresh is enabled)") String eventStreamBootstrapServers,
            @Metadata(description = "Epoch time in milliseconds of the last check (only present when known)") Long lastCheckTimestamp,
            @Metadata(description = "Relative age of the last check (only present when known)") String lastCheckAge,
            @Metadata(description = "Epoch time in milliseconds of the last reload (only present when known)") Long lastReloadTimestamp,
            @Metadata(description = "Relative age of the last reload (only present when known)") String lastReloadAge,
            @Metadata(description = "The secrets in use (only present when there are any)") List<SecretEntry> secrets) {
    }

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
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        String serviceUrl = null;
        String login = null;
        Boolean refreshEnabled = null;
        String eventStreamTopic = null;
        String eventStreamBootstrapServers = null;
        Long lastCheckTimestamp = null;
        String lastCheckAge = null;
        Long lastReloadTimestamp = null;
        String lastReloadAge = null;
        List<SecretEntry> secrets = null;

        if (propertiesFunction != null) {
            IBMSecretsManagerVaultConfiguration ibm
                    = getCamelContext().getVaultConfiguration().getIBMSecretsManagerVaultConfiguration();
            if (ibm != null) {
                serviceUrl = ibm.getServiceUrl();
                login = "IAM Token";
                refreshEnabled = ibm.isRefreshEnabled();
                if (ibm.isRefreshEnabled()) {
                    eventStreamTopic = ibm.getEventStreamTopic();
                    eventStreamBootstrapServers = ibm.getEventStreamBootstrapServers();
                }
            }
            if (secretsRefreshTask != null) {
                Instant last = secretsRefreshTask.getLastCheckTime();
                if (last != null) {
                    lastCheckTimestamp = last.toEpochMilli();
                    lastCheckAge = TimeUtils.printSince(lastCheckTimestamp);
                }
                last = secretsRefreshTask.getLastReloadTime();
                if (last != null) {
                    lastReloadTimestamp = last.toEpochMilli();
                    lastReloadAge = TimeUtils.printSince(lastReloadTimestamp);
                }
            }

            List<String> sorted = new ArrayList<>(propertiesFunction.getSecrets());
            Collections.sort(sorted);

            List<SecretEntry> arr = new ArrayList<>();
            for (String sec : sorted) {
                Long timestamp = null;
                String age = null;
                Instant last = secretsRefreshTask != null ? secretsRefreshTask.getUpdates().get(sec) : null;
                if (last != null) {
                    timestamp = last.toEpochMilli();
                    age = TimeUtils.printSince(timestamp);
                }
                arr.add(new SecretEntry(sec, timestamp, age));
            }
            secrets = arr.isEmpty() ? null : arr;
        }

        Response response = new Response(
                serviceUrl, login, refreshEnabled, eventStreamTopic, eventStreamBootstrapServers, lastCheckTimestamp,
                lastCheckAge, lastReloadTimestamp, lastReloadAge, secrets);
        return JsonRecordSupport.toJsonObject(response);
    }
}
