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
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.PeriodTaskScheduler;
import org.apache.camel.spi.PropertiesFunction;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonRecordSupport;
import org.apache.camel.vault.AwsVaultConfiguration;

@DevConsole(name = "aws-secrets", displayName = "AWS Secrets", description = "AWS Secrets Manager")
public class SecretsDevConsole extends AbstractDevConsole {

    private SecretsManagerPropertiesFunction propertiesFunction;
    private CloudTrailReloadTriggerTask secretsRefreshTask;

    public record SecretEntry(
            @Metadata(description = "The secret name") String name,
            @Metadata(description = "Epoch time in milliseconds of the last update (only present when known)") Long timestamp,
            @Metadata(description = "Relative age of the last update (only present when known)") String age) {
    }

    public record Response(
            @Metadata(description = "The AWS region (only present when the AWS Secrets Manager properties function is active)") String region,
            @Metadata(description = "The login method (only present when the AWS Secrets Manager properties function is active)") String login,
            @Metadata(description = "Whether secret refresh is enabled (only present when configured)") Boolean refreshEnabled,
            @Metadata(description = "The refresh period in milliseconds (only present when configured)") Long refreshPeriod,
            @Metadata(description = "Epoch time in milliseconds of the last check (only present when known)") Long lastCheckTimestamp,
            @Metadata(description = "Relative age of the last check (only present when known)") String lastCheckAge,
            @Metadata(description = "Epoch time in milliseconds of the last reload (only present when known)") Long lastReloadTimestamp,
            @Metadata(description = "Relative age of the last reload (only present when known)") String lastReloadAge,
            @Metadata(description = "The secrets in use (only present when the AWS Secrets Manager properties function is active)") List<SecretEntry> secrets) {
    }

    public SecretsDevConsole() {
        super("camel", "aws-secrets", "AWS Secrets", "AWS Secrets Manager");
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (getCamelContext().getPropertiesComponent().hasPropertiesFunction("aws")) {
            PropertiesFunction pf = getCamelContext().getPropertiesComponent().getPropertiesFunction("aws");
            if (pf instanceof SecretsManagerPropertiesFunction secretsManagerPropertiesFunction) {
                propertiesFunction = secretsManagerPropertiesFunction;
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
            sb.append(String.format("%n    Region: %s", propertiesFunction.getRegion()));
            if (propertiesFunction.isDefaultCredentialsProvider()) {
                sb.append("\n    Login: DefaultCredentialsProvider");
            } else if (propertiesFunction.isProfleCredentialsProvider()) {
                sb.append("\n    Login: ProfileCredentialsProvider");
            } else {
                sb.append("\n    Login: Access and Secret Keys");
            }
            AwsVaultConfiguration aws = getCamelContext().getVaultConfiguration().getAwsVaultConfiguration();
            if (aws != null) {
                sb.append(String.format("%n    Refresh Enabled: %s", aws.isRefreshEnabled()));
                sb.append(String.format("%n    Refresh Period: %s", aws.getRefreshPeriod()));
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
        String region = null;
        String login = null;
        Boolean refreshEnabled = null;
        Long refreshPeriod = null;
        Long lastCheckTimestamp = null;
        String lastCheckAge = null;
        Long lastReloadTimestamp = null;
        String lastReloadAge = null;
        List<SecretEntry> secrets = null;

        if (propertiesFunction != null) {
            region = propertiesFunction.getRegion();
            if (propertiesFunction.isDefaultCredentialsProvider()) {
                login = "DefaultCredentialsProvider";
            } else if (propertiesFunction.isProfleCredentialsProvider()) {
                login = "ProfileCredentialsProvider";
            } else {
                login = "Access and Secret Keys";
            }
            AwsVaultConfiguration aws = getCamelContext().getVaultConfiguration().getAwsVaultConfiguration();
            if (aws != null) {
                refreshEnabled = aws.isRefreshEnabled();
                refreshPeriod = aws.getRefreshPeriod();
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
            secrets = arr;
        }

        Response response = new Response(
                region, login, refreshEnabled, refreshPeriod, lastCheckTimestamp, lastCheckAge, lastReloadTimestamp,
                lastReloadAge, secrets);
        return JsonRecordSupport.toJsonObject(response);
    }
}
