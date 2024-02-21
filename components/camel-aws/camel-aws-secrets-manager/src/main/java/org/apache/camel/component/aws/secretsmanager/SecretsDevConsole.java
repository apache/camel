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
package org.apache.camel.component.aws.secretsmanager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.camel.component.aws.secretsmanager.vault.CloudTrailReloadTriggerTask;
import org.apache.camel.spi.PeriodTaskScheduler;
import org.apache.camel.spi.PropertiesFunction;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.vault.AwsVaultConfiguration;

@DevConsole("aws-secrets")
public class SecretsDevConsole extends AbstractDevConsole {

    private SecretsManagerPropertiesFunction propertiesFunction;
    private CloudTrailReloadTriggerTask secretsRefreshTask;

    public SecretsDevConsole() {
        super("camel", "aws-secrets", "AWS Secrets", "AWS Secrets Manager");
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (getCamelContext().getPropertiesComponent().hasPropertiesFunction("aws")) {
            PropertiesFunction pf = getCamelContext().getPropertiesComponent().getPropertiesFunction("aws");
            if (pf instanceof SecretsManagerPropertiesFunction) {
                propertiesFunction = (SecretsManagerPropertiesFunction) pf;
            }
        }
        AwsVaultConfiguration aws = getCamelContext().getVaultConfiguration().getAwsVaultConfiguration();
        if (aws != null && aws.isRefreshEnabled()) {
            PeriodTaskScheduler scheduler = PluginHelper.getPeriodTaskScheduler(getCamelContext());
            secretsRefreshTask = scheduler.getTaskByType(CloudTrailReloadTriggerTask.class);
        }
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        if (propertiesFunction != null) {
            sb.append("AWS Secrets Manager:");
            sb.append(String.format("\n    Region: %s", propertiesFunction.getRegion()));
            if (propertiesFunction.isDefaultCredentialsProvider()) {
                sb.append("\n    Login: DefaultCredentialsProvider");
            } else if (propertiesFunction.isProfleCredentialsProvider()) {
                sb.append("\n    Login: ProfileCredentialsProvider");
            } else {
                sb.append("\n    Login: Access and Secret Keys");
            }
            AwsVaultConfiguration aws = getCamelContext().getVaultConfiguration().getAwsVaultConfiguration();
            if (aws != null) {
                sb.append(String.format("\n    Refresh Enabled: %s", aws.isRefreshEnabled()));
                sb.append(String.format("\n    Refresh Period: %s", aws.getRefreshPeriod()));
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
            root.put("region", propertiesFunction.getRegion());
            if (propertiesFunction.isDefaultCredentialsProvider()) {
                root.put("login", "DefaultCredentialsProvider");
            } else if (propertiesFunction.isProfleCredentialsProvider()) {
                root.put("login", "ProfileCredentialsProvider");
            } else {
                root.put("login", "Access and Secret Keys");
            }
            AwsVaultConfiguration aws = getCamelContext().getVaultConfiguration().getAwsVaultConfiguration();
            if (aws != null) {
                root.put("refreshEnabled", aws.isRefreshEnabled());
                root.put("refreshPeriod", aws.getRefreshPeriod());
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
