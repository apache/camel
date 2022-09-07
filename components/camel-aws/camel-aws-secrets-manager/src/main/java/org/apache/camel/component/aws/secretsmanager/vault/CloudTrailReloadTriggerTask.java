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
import org.apache.camel.spi.ContextReloadStrategy;
import org.apache.camel.support.PatternHelper;
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
 * Period task which checks if AWS secrets has been updated and
 * can trigger Camel to be reloaded.
 */
public class CloudTrailReloadTriggerTask implements Runnable {

    // TODO: extends ServiceSupport
    // TODO: doStart to create CloudTrailClient
    // TODO: doStop to cleanup if needed
    // TODO: support ENV like SecretsManagerPropertiesFunction

    private static final Logger LOG = LoggerFactory.getLogger(CloudTrailReloadTriggerTask.class);
    private static final String SECRETSMANAGER_AMAZONAWS_COM = "secretsmanager.amazonaws.com";

    private final CamelContext context;
    private final String secrets;
    private volatile Instant lastTime;

    public CloudTrailReloadTriggerTask(CamelContext context, String secrets) {
        this.context = context;
        this.secrets = secrets;
    }

    @Override
    public void run() {
        boolean triggerReloading = false;
        CloudTrailClientBuilder cloudTrailClientBuilder;
        Region regionValue = Region.of(context.getVaultConfiguration().aws().getRegion());
        if (context.getVaultConfiguration().aws().isDefaultCredentialsProvider()) {
            cloudTrailClientBuilder = CloudTrailClient.builder()
                    .region(regionValue)
                    .credentialsProvider(ProfileCredentialsProvider.create());
        } else {
            AwsBasicCredentials cred = AwsBasicCredentials.create(context.getVaultConfiguration().aws().getAccessKey(),
                    context.getVaultConfiguration().aws().getSecretKey());
            cloudTrailClientBuilder = CloudTrailClient.builder().credentialsProvider(StaticCredentialsProvider.create(cred));
        }
        CloudTrailClient cloudTrailClient = cloudTrailClientBuilder.build();
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
            ContextReloadStrategy reload = context.hasService(ContextReloadStrategy.class);
            if (reload != null) {
                // trigger reload
                reload.onReload(context.getName());
            }
        }
    }

    protected boolean matchSecret(String name, String patterns) {
        String[] parts = patterns.split(",");
        for (String part : parts) {
            if (name.contains(part) ||  PatternHelper.matchPattern(name, part)) {
                return true;
            }
        }
        return false;
    }
}
