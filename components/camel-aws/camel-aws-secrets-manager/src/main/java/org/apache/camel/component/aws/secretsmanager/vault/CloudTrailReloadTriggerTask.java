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
package org.apache.camel.component.aws.secretsmanager.vault;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.aws.secretsmanager.SecretsManagerPropertiesFunction;
import org.apache.camel.spi.ContextReloadStrategy;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.PropertiesFunction;
import org.apache.camel.spi.annotations.PeriodicTask;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.vault.AwsVaultConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudtrail.CloudTrailClient;
import software.amazon.awssdk.services.cloudtrail.CloudTrailClientBuilder;
import software.amazon.awssdk.services.cloudtrail.model.Event;
import software.amazon.awssdk.services.cloudtrail.model.LookupAttribute;
import software.amazon.awssdk.services.cloudtrail.model.LookupAttributeKey;
import software.amazon.awssdk.services.cloudtrail.model.LookupEventsRequest;
import software.amazon.awssdk.services.cloudtrail.model.LookupEventsResponse;
import software.amazon.awssdk.services.cloudtrail.model.Resource;

/**
 * Period task which checks if AWS secrets has been updated and can trigger Camel to be reloaded.
 */
@PeriodicTask("aws-secret-refresh")
public class CloudTrailReloadTriggerTask extends ServiceSupport implements CamelContextAware, Runnable {

    private static final String CAMEL_AWS_VAULT_ACCESS_KEY_ENV = "CAMEL_VAULT_AWS_ACCESS_KEY";
    private static final String CAMEL_AWS_VAULT_SECRET_KEY_ENV = "CAMEL_VAULT_AWS_SECRET_KEY";
    private static final String CAMEL_AWS_VAULT_REGION_ENV = "CAMEL_VAULT_AWS_REGION";
    private static final String CAMEL_AWS_VAULT_USE_DEFAULT_CREDENTIALS_PROVIDER_ENV
            = "CAMEL_VAULT_AWS_USE_DEFAULT_CREDENTIALS_PROVIDER";

    private static final String CAMEL_AWS_VAULT_USE_PROFILE_CREDENTIALS_PROVIDER_ENV
            = "CAMEL_VAULT_AWS_USE_PROFILE_CREDENTIALS_PROVIDER";

    private static final String CAMEL_AWS_VAULT_PROFILE_NAME_ENV
            = "CAMEL_AWS_VAULT_PROFILE_NAME";

    private static final Logger LOG = LoggerFactory.getLogger(CloudTrailReloadTriggerTask.class);
    private static final String SECRETSMANAGER_AMAZONAWS_COM = "secretsmanager.amazonaws.com";

    private static final String SECRETSMANAGER_UPDATE_EVENT = "PutSecretValue";

    private CamelContext camelContext;
    private boolean reloadEnabled = true;
    private String secrets;
    private CloudTrailClient cloudTrailClient;
    private SecretsManagerPropertiesFunction propertiesFunction;
    private volatile Instant lastTime;
    private volatile Instant lastCheckTime;
    private volatile Instant lastReloadTime;
    private final Map<String, Instant> updates = new HashMap<>();

    public CloudTrailReloadTriggerTask() {
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public boolean isReloadEnabled() {
        return reloadEnabled;
    }

    /**
     * Whether Camel should be reloaded on AWS secret updated
     */
    public void setReloadEnabled(boolean reloadEnabled) {
        this.reloadEnabled = reloadEnabled;
    }

    /**
     * A map of the updated secrets with the latest updated time.
     */
    public Map<String, Instant> getUpdates() {
        return Collections.unmodifiableMap(updates);
    }

    /**
     * Last time this task checked AWS for updated secrets.
     */
    public Instant getLastCheckTime() {
        return lastCheckTime;
    }

    /**
     * Last time AWS secrets update triggered reload.
     */
    public Instant getLastReloadTime() {
        return lastReloadTime;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // auto-detect secrets in-use
        PropertiesComponent pc = camelContext.getPropertiesComponent();
        PropertiesFunction pf = pc.getPropertiesFunction("aws");
        if (pf instanceof SecretsManagerPropertiesFunction) {
            propertiesFunction = (SecretsManagerPropertiesFunction) pf;
            LOG.debug("Auto-detecting secrets from properties-function: {}", pf.getName());
        }
        // specific secrets
        secrets = camelContext.getVaultConfiguration().aws().getSecrets();
        if (ObjectHelper.isEmpty(secrets) && propertiesFunction == null) {
            throw new IllegalArgumentException("Secrets must be configured on AWS vault configuration");
        }

        String accessKey = System.getenv(CAMEL_AWS_VAULT_ACCESS_KEY_ENV);
        String secretKey = System.getenv(CAMEL_AWS_VAULT_SECRET_KEY_ENV);
        String region = System.getenv(CAMEL_AWS_VAULT_REGION_ENV);
        boolean useDefaultCredentialsProvider
                = Boolean.parseBoolean(System.getenv(CAMEL_AWS_VAULT_USE_DEFAULT_CREDENTIALS_PROVIDER_ENV));
        boolean useProfileCredentialsProvider
                = Boolean.parseBoolean(System.getenv(CAMEL_AWS_VAULT_USE_PROFILE_CREDENTIALS_PROVIDER_ENV));
        String profileName = System.getenv(CAMEL_AWS_VAULT_PROFILE_NAME_ENV);
        if (ObjectHelper.isEmpty(accessKey) && ObjectHelper.isEmpty(secretKey) && ObjectHelper.isEmpty(region)) {
            AwsVaultConfiguration awsVaultConfiguration = getCamelContext().getVaultConfiguration().aws();
            if (ObjectHelper.isNotEmpty(awsVaultConfiguration)) {
                accessKey = awsVaultConfiguration.getAccessKey();
                secretKey = awsVaultConfiguration.getSecretKey();
                region = awsVaultConfiguration.getRegion();
                useDefaultCredentialsProvider = awsVaultConfiguration.isDefaultCredentialsProvider();
                useProfileCredentialsProvider = awsVaultConfiguration.isProfileCredentialsProvider();
                profileName = awsVaultConfiguration.getProfileName();
            }
        }
        if (ObjectHelper.isNotEmpty(accessKey) && ObjectHelper.isNotEmpty(secretKey) && ObjectHelper.isNotEmpty(region)) {
            CloudTrailClientBuilder clientBuilder = CloudTrailClient.builder();
            AwsBasicCredentials cred = AwsBasicCredentials.create(accessKey, secretKey);
            clientBuilder = clientBuilder.credentialsProvider(StaticCredentialsProvider.create(cred));
            clientBuilder.region(Region.of(region));
            cloudTrailClient = clientBuilder.build();
        } else if (useDefaultCredentialsProvider && ObjectHelper.isNotEmpty(region)) {
            CloudTrailClientBuilder clientBuilder = CloudTrailClient.builder();
            clientBuilder.region(Region.of(region));
            cloudTrailClient = clientBuilder.build();
        } else if (useProfileCredentialsProvider && ObjectHelper.isNotEmpty(profileName)) {
            CloudTrailClientBuilder clientBuilder = CloudTrailClient.builder();
            clientBuilder.credentialsProvider(ProfileCredentialsProvider.create(profileName));
            clientBuilder.region(Region.of(region));
            cloudTrailClient = clientBuilder.build();
        } else {
            throw new RuntimeCamelException(
                    "Using the AWS Secrets Refresh Task requires setting AWS credentials as application properties or environment variables");
        }
    }

    @Override
    protected void doShutdown() throws Exception {
        super.doShutdown();

        if (cloudTrailClient != null) {
            try {
                cloudTrailClient.close();
            } catch (Exception e) {
                // ignore
            }
            cloudTrailClient = null;
        }

        updates.clear();
    }

    @Override
    public void run() {
        lastCheckTime = Instant.now();
        boolean triggerReloading = false;

        try {
            LookupEventsRequest.Builder eventsRequestBuilder = LookupEventsRequest.builder()
                    .maxResults(100).lookupAttributes(LookupAttribute.builder().attributeKey(LookupAttributeKey.EVENT_SOURCE)
                            .attributeValue(SECRETSMANAGER_AMAZONAWS_COM).build());

            if (lastTime != null) {
                eventsRequestBuilder.startTime(lastTime.plusMillis(1000));
            }

            LookupEventsRequest lookupEventsRequest = eventsRequestBuilder.build();

            LookupEventsResponse response = cloudTrailClient.lookupEvents(lookupEventsRequest);
            List<Event> events = response.events();

            if (!events.isEmpty()) {
                lastTime = events.get(0).eventTime();
            }

            LOG.debug("Found {} events", events.size());
            for (Event event : events) {
                if (event.eventSource().equalsIgnoreCase(SECRETSMANAGER_AMAZONAWS_COM)) {
                    if (event.eventName().equalsIgnoreCase(SECRETSMANAGER_UPDATE_EVENT)) {
                        List<Resource> a = event.resources();
                        for (Resource res : a) {
                            String name = res.resourceName();
                            if (matchSecret(name)) {
                                updates.put(name, event.eventTime());
                                if (isReloadEnabled()) {
                                    LOG.info("Update for AWS secret: {} detected, triggering CamelContext reload", name);
                                    triggerReloading = true;
                                }
                                break;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("Error during AWS Secrets Refresh Task due to {}. This exception is ignored. Will try again on next run.",
                    e.getMessage(), e);
        }

        if (triggerReloading) {
            ContextReloadStrategy reload = camelContext.hasService(ContextReloadStrategy.class);
            if (reload != null) {
                // trigger reload
                lastReloadTime = Instant.now();
                reload.onReload(this);
            }
        }
    }

    protected boolean matchSecret(String name) {
        Set<String> set = new HashSet<>();
        if (secrets != null) {
            Collections.addAll(set, secrets.split(","));
        }
        if (propertiesFunction != null) {
            set.addAll(propertiesFunction.getSecrets());
        }

        for (String part : set) {
            boolean result = name.contains(part) || PatternHelper.matchPattern(name, part);
            LOG.trace("Matching secret id: {}={} -> {}", name, part, result);
            if (result) {
                return true;
            }
        }

        return false;
    }

    @Override
    public String toString() {
        return "AWS Secrets Refresh Task";
    }
}
