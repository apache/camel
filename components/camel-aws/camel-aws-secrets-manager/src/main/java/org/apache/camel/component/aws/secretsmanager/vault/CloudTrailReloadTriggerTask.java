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
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.spi.ContextReloadStrategy;
import org.apache.camel.spi.annotations.PeriodicTask;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudtrail.CloudTrailClient;
import software.amazon.awssdk.services.cloudtrail.CloudTrailClientBuilder;
import software.amazon.awssdk.services.cloudtrail.model.CloudTrailException;
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

    // TODO: support ENV like SecretsManagerPropertiesFunction

    private static final Logger LOG = LoggerFactory.getLogger(CloudTrailReloadTriggerTask.class);
    private static final String SECRETSMANAGER_AMAZONAWS_COM = "secretsmanager.amazonaws.com";

    private CamelContext camelContext;
    private CloudTrailClient cloudTrailClient;
    private String secrets;
    private volatile Instant lastTime;

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

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        secrets = camelContext.getVaultConfiguration().aws().getSecrets();
        if (ObjectHelper.isEmpty(secrets)) {
            throw new IllegalArgumentException("Secrets must be configured on AWS vault configuration");
        }

        CloudTrailClientBuilder cloudTrailClientBuilder;
        Region regionValue = Region.of(camelContext.getVaultConfiguration().aws().getRegion());
        if (camelContext.getVaultConfiguration().aws().isDefaultCredentialsProvider()) {
            cloudTrailClientBuilder = CloudTrailClient.builder()
                    .region(regionValue)
                    .credentialsProvider(ProfileCredentialsProvider.create());
        } else {
            AwsBasicCredentials cred = AwsBasicCredentials.create(camelContext.getVaultConfiguration().aws().getAccessKey(),
                    camelContext.getVaultConfiguration().aws().getSecretKey());
            cloudTrailClientBuilder = CloudTrailClient.builder().credentialsProvider(StaticCredentialsProvider.create(cred));
        }
        cloudTrailClient = cloudTrailClientBuilder.build();
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
    }

    @Override
    public void run() {
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

            if (events.size() > 0) {
                lastTime = events.get(0).eventTime();
            }

            LOG.debug("Found {} events", events.size());
            for (Event event : events) {
                if (event.eventSource().equalsIgnoreCase(SECRETSMANAGER_AMAZONAWS_COM)) {
                    if (event.eventName().equalsIgnoreCase("PutSecretValue")) {
                        List<Resource> a = event.resources();
                        for (Resource res : a) {
                            String name = res.resourceName();
                            if (matchSecret(name, secrets)) {
                                LOG.info("Update for secret: {} detected, triggering a CamelContext reload", name);
                                triggerReloading = true;
                                break;
                            }
                        }
                    }
                }
            }
        } catch (CloudTrailException e) {
            throw e;
        }

        if (triggerReloading) {
            ContextReloadStrategy reload = camelContext.hasService(ContextReloadStrategy.class);
            if (reload != null) {
                // trigger reload
                reload.onReload(camelContext.getName());
            }
        }
    }

    protected boolean matchSecret(String name, String patterns) {
        String[] parts = patterns.split(",");
        for (String part : parts) {
            if (name.contains(part) || PatternHelper.matchPattern(name, part)) {
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
