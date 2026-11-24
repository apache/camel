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

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

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

    private static final String CAMEL_AWS_VAULT_SQS_QUEUE_URL_ENV
            = "CAMEL_AWS_VAULT_SQS_QUEUE_URL";

    private static final String CAMEL_AWS_VAULT_USE_SQS_NOTIFICATION_ENV
            = "CAMEL_AWS_VAULT_USE_SQS_NOTIFICATION";

    private static final String CAMEL_AWS_VAULT_IS_OVERRIDE_ENDPOINT
            = "CAMEL_AWS_VAULT_IS_OVERRIDE_ENDPOINT";

    private static final String CAMEL_AWS_VAULT_URI_ENDPOINT_OVERRIDE = "CAMEL_AWS_VAULT_URI_ENDPOINT_OVERRIDE";

    private static final Logger LOG = LoggerFactory.getLogger(CloudTrailReloadTriggerTask.class);
    private static final ObjectMapper MAPPER = JsonMapper.builderWithJackson2Defaults().build();
    private static final String SECRETSMANAGER_AMAZONAWS_COM = "secretsmanager.amazonaws.com";

    private static final String SECRETSMANAGER_UPDATE_EVENT = "PutSecretValue";

    private static final String SECRETSMANAGER_UPDATE_SECRET_EVENT = "UpdateSecret";

    // the largest page that LookupEvents accepts
    private static final int LOOKUP_MAX_RESULTS = 50;
    // the events that change the value of a secret, looked up one by one
    private static final List<String> UPDATE_EVENT_NAMES
            = List.of(SECRETSMANAGER_UPDATE_EVENT, SECRETSMANAGER_UPDATE_SECRET_EVENT);
    // how far before the newest event seen each check looks again: CloudTrail can make an event visible after a newer
    // one (on average within about 5 minutes, not guaranteed), the default lookBackWindow of the aws-cloudtrail consumer
    private static final Duration LOOK_BACK = Duration.ofMinutes(10);
    // how many reloads in a row an update triggers when they fail, one per run, before it is marked as seen anyway
    static final int MAX_RELOAD_ATTEMPTS = 10;

    private CamelContext camelContext;
    private boolean reloadEnabled = true;
    private String secrets;
    private CloudTrailClient cloudTrailClient;
    private SqsClient sqsClient;
    private SecretsManagerPropertiesFunction propertiesFunction;
    private volatile Instant lastTime;
    // the time the task started to look for updates: no check looks up events before it
    private volatile Instant startedAt;
    // the ids of the events seen with their event time, which the next lookups return again (they start LOOK_BACK
    // before lastTime, inclusive); pruned once the event time is before the start of the next lookup
    private final Map<String, Instant> seenEventIds = new HashMap<>();
    private volatile Instant lastCheckTime;
    private volatile Instant lastReloadTime;
    private final Map<String, Instant> updates = new HashMap<>();
    private boolean useSqsNotification;
    private String queueUrl;
    // the reloads in a row that failed for the updates not marked as seen yet
    private int failedReloads;

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
        if (pf instanceof SecretsManagerPropertiesFunction secretsManagerPropertiesFunction) {
            propertiesFunction = secretsManagerPropertiesFunction;
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
        boolean isOverrideEndpoint = Boolean.parseBoolean(System.getenv(CAMEL_AWS_VAULT_IS_OVERRIDE_ENDPOINT));
        String uriEndpointOverride = System.getenv(CAMEL_AWS_VAULT_URI_ENDPOINT_OVERRIDE);
        boolean useDefaultCredentialsProvider
                = Boolean.parseBoolean(System.getenv(CAMEL_AWS_VAULT_USE_DEFAULT_CREDENTIALS_PROVIDER_ENV));
        boolean useProfileCredentialsProvider
                = Boolean.parseBoolean(System.getenv(CAMEL_AWS_VAULT_USE_PROFILE_CREDENTIALS_PROVIDER_ENV));
        useSqsNotification
                = Boolean.parseBoolean(System.getenv(CAMEL_AWS_VAULT_USE_SQS_NOTIFICATION_ENV));
        String profileName = System.getenv(CAMEL_AWS_VAULT_PROFILE_NAME_ENV);
        queueUrl = System.getenv(CAMEL_AWS_VAULT_SQS_QUEUE_URL_ENV);
        if (ObjectHelper.isEmpty(accessKey) && ObjectHelper.isEmpty(secretKey) && ObjectHelper.isEmpty(region)) {
            AwsVaultConfiguration awsVaultConfiguration = getCamelContext().getVaultConfiguration().aws();
            if (ObjectHelper.isNotEmpty(awsVaultConfiguration)) {
                accessKey = awsVaultConfiguration.getAccessKey();
                secretKey = awsVaultConfiguration.getSecretKey();
                region = awsVaultConfiguration.getRegion();
                isOverrideEndpoint = awsVaultConfiguration.isOverrideEndpoint();
                uriEndpointOverride = awsVaultConfiguration.getUriEndpointOverride();
                useDefaultCredentialsProvider = awsVaultConfiguration.isDefaultCredentialsProvider();
                useProfileCredentialsProvider = awsVaultConfiguration.isProfileCredentialsProvider();
                profileName = awsVaultConfiguration.getProfileName();
                useSqsNotification = awsVaultConfiguration.isUseSqsNotification();
                queueUrl = awsVaultConfiguration.getSqsQueueUrl();
            }
        }
        if (!useSqsNotification) {
            CloudTrailClientBuilder clientBuilder = CloudTrailClient.builder();
            if (ObjectHelper.isNotEmpty(accessKey) && ObjectHelper.isNotEmpty(secretKey) && ObjectHelper.isNotEmpty(region)) {

                AwsBasicCredentials cred = AwsBasicCredentials.create(accessKey, secretKey);
                clientBuilder = clientBuilder.credentialsProvider(StaticCredentialsProvider.create(cred));
                clientBuilder.region(Region.of(region));
            } else if (useDefaultCredentialsProvider && ObjectHelper.isNotEmpty(region)) {
                clientBuilder.region(Region.of(region));
            } else if (useProfileCredentialsProvider && ObjectHelper.isNotEmpty(profileName)) {
                clientBuilder.credentialsProvider(ProfileCredentialsProvider.create(profileName));
                clientBuilder.region(Region.of(region));
            } else {
                throw new RuntimeCamelException(
                        "Using the AWS Secrets Refresh Task requires setting AWS credentials as application properties or environment variables");
            }
            cloudTrailClient = isOverrideEndpoint
                    ? clientBuilder.endpointOverride(URI.create(uriEndpointOverride)).build() : clientBuilder.build();
            if (lastTime == null) {
                // look for the updates made from now on, not in the event history of the last 90 days
                lastTime = Instant.now();
                startedAt = lastTime;
            }
        } else {
            SqsClientBuilder clientBuilder = SqsClient.builder();
            if (ObjectHelper.isNotEmpty(accessKey) && ObjectHelper.isNotEmpty(secretKey) && ObjectHelper.isNotEmpty(region)) {
                AwsBasicCredentials cred = AwsBasicCredentials.create(accessKey, secretKey);
                clientBuilder = clientBuilder.credentialsProvider(StaticCredentialsProvider.create(cred));
                clientBuilder.region(Region.of(region));
            } else if (useDefaultCredentialsProvider && ObjectHelper.isNotEmpty(region)) {
                clientBuilder.region(Region.of(region));
            } else if (useProfileCredentialsProvider && ObjectHelper.isNotEmpty(profileName)) {
                clientBuilder.credentialsProvider(ProfileCredentialsProvider.create(profileName));
                clientBuilder.region(Region.of(region));
            } else {
                throw new RuntimeCamelException(
                        "Using the AWS Secrets Refresh Task requires setting AWS credentials as application properties or environment variables");
            }
            sqsClient = isOverrideEndpoint
                    ? clientBuilder.endpointOverride(URI.create(uriEndpointOverride)).build() : clientBuilder.build();
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

        if (sqsClient != null) {
            try {
                sqsClient.close();
            } catch (Exception e) {
                // ignore
            }
            sqsClient = null;
        }

        updates.clear();
        lastTime = null;
        startedAt = null;
        seenEventIds.clear();
        failedReloads = 0;
    }

    /**
     * The start time of the lookups of a check whose newest event seen is at the given time: LOOK_BACK before it, as
     * CloudTrail can make an event visible after a newer one, but not before the task started.
     */
    private static Instant lookupStart(Instant newest, Instant startedAt) {
        if (newest == null) {
            return null;
        }
        Instant start = newest.minus(LOOK_BACK);
        return startedAt != null && start.isBefore(startedAt) ? startedAt : start;
    }

    /**
     * Adds every event with the given event name since the given time to the list, reading all pages.
     *
     * @param since the start time of the lookup, the same for every page and every event name of one check; null to
     *              look up without a start time
     */
    private void lookupEvents(String eventName, Instant since, List<Event> events) {
        String nextToken = null;
        do {
            LookupEventsRequest.Builder eventsRequestBuilder = LookupEventsRequest.builder()
                    .maxResults(LOOKUP_MAX_RESULTS)
                    .lookupAttributes(LookupAttribute.builder().attributeKey(LookupAttributeKey.EVENT_NAME)
                            .attributeValue(eventName).build());
            if (since != null) {
                // inclusive, so an event in the same second as the newest event seen is not skipped
                eventsRequestBuilder.startTime(since);
            }
            if (nextToken != null) {
                eventsRequestBuilder.nextToken(nextToken);
            }
            LookupEventsResponse response = cloudTrailClient.lookupEvents(eventsRequestBuilder.build());
            events.addAll(response.events());
            nextToken = response.nextToken();
        } while (ObjectHelper.isNotEmpty(nextToken));
    }

    @Override
    public void run() {
        lastCheckTime = Instant.now();
        boolean triggerReloading = false;
        // marks what this check found as seen: the events, or the SQS notifications (deleted). When the check triggers
        // a reload, it runs only once the reload succeeded, so that after a failed reload a later run sees the update
        // again and reloads again
        Runnable markSeen = null;

        if (!useSqsNotification) {

            try {
                // read every page of the update events in the range of the check. LookupEvents takes a single lookup
                // attribute, so there is one lookup per event name: filtering on the event source instead would also
                // return every read (GetSecretValue, DescribeSecret), and the pages would grow with the read traffic
                // read lastTime once: every page of both lookups of this check uses the same start time. The lookups
                // start LOOK_BACK before the newest event seen, for the events that CloudTrail made visible late
                Instant since = lastTime;
                Instant started = startedAt;
                Instant start = lookupStart(since, started);
                List<Event> events = new ArrayList<>();
                for (String eventName : UPDATE_EVENT_NAMES) {
                    lookupEvents(eventName, start, events);
                }

                LOG.debug("Found {} events", events.size());
                Instant newest = since;
                // the events this check sees first, added to seenEventIds when they are marked as seen
                Map<String, Instant> newEventIds = new HashMap<>();
                for (Event event : events) {
                    Instant eventTime = event.eventTime();
                    if (seenEventIds.containsKey(event.eventId())
                            || newEventIds.putIfAbsent(event.eventId(), eventTime) != null) {
                        // already seen by an earlier check (or returned by both lookups)
                        continue;
                    }
                    if (newest == null || eventTime.isAfter(newest)) {
                        newest = eventTime;
                    }
                    // the lookups are filtered by event name (PutSecretValue or UpdateSecret), but event names are not
                    // unique across AWS services, so the event source is still checked
                    if (!SECRETSMANAGER_AMAZONAWS_COM.equalsIgnoreCase(event.eventSource())) {
                        continue;
                    }
                    List<Resource> a = event.resources();
                    for (Resource res : a) {
                        String name = res.resourceName();
                        if (matchSecret(name)) {
                            // an event made visible late can be older than an update of the secret seen before
                            updates.merge(name, eventTime, (seen, late) -> seen.isAfter(late) ? seen : late);
                            if (isReloadEnabled()) {
                                LOG.info("Update for AWS secret: {} detected, triggering CamelContext reload", name);
                                triggerReloading = true;
                            }
                            break;
                        }
                    }
                }
                Instant checked = newest;
                markSeen = () -> {
                    seenEventIds.putAll(newEventIds);
                    lastTime = checked;
                    // the next lookups do not return the events before their start time again
                    Instant nextStart = lookupStart(checked, started);
                    if (nextStart != null) {
                        seenEventIds.values().removeIf(t -> t.isBefore(nextStart));
                    }
                };
            } catch (Exception e) {
                LOG.warn(
                        "Error during AWS Secrets Refresh Task due to {}. This exception is ignored. Will try again on next run.",
                        e.getMessage(), e);
            }
        } else {
            // the receipt handles of the notifications of the updates found, deleted when they are marked as seen
            List<String> updateMessages = new ArrayList<>();
            markSeen = () -> updateMessages.forEach(this::deleteMessage);
            try {
                ReceiveMessageRequest.Builder request = ReceiveMessageRequest.builder().queueUrl(queueUrl);

                LOG.trace("Receiving messages with request [{}]...", request);

                ReceiveMessageResponse messageResult = null;
                ReceiveMessageRequest requestBuild = request.build();
                try {
                    messageResult = sqsClient.receiveMessage(requestBuild);
                } catch (QueueDoesNotExistException e) {
                    LOG.info("Queue does not exist.");
                }

                if (messageResult != null) {
                    for (Message message : messageResult.messages()) {
                        boolean update = false;
                        try {
                            JsonNode event = MAPPER.readTree(message.body());
                            JsonNode innerDetail = event.get("detail");
                            if (ObjectHelper.isNotEmpty(innerDetail)
                                    && innerDetail.get("eventSource").asString("")
                                            .equalsIgnoreCase(SECRETSMANAGER_AMAZONAWS_COM)
                                    && (innerDetail.get("eventName").asString("").equalsIgnoreCase(SECRETSMANAGER_UPDATE_EVENT)
                                            || innerDetail.get("eventName").asString("")
                                                    .equalsIgnoreCase(SECRETSMANAGER_UPDATE_SECRET_EVENT))) {
                                String name = innerDetail.get("requestParameters").get("secretId").asString("");
                                if (matchSecret(name)) {
                                    updates.put(name, Instant.parse(innerDetail.get("eventTime").asString("")));
                                    update = true;
                                    if (isReloadEnabled()) {
                                        LOG.info("Update for AWS secret: {} detected, triggering CamelContext reload", name);
                                        triggerReloading = true;
                                    }
                                }
                            }
                        } catch (JacksonException e) {
                            LOG.warn("Unable to parse SQS message body, discarding message: {}", e.getMessage());
                        }
                        if (update) {
                            // deleted once the reload succeeded: after a failed reload the queue delivers the message
                            // again (once its visibility timeout is over), and that run reloads again
                            updateMessages.add(message.receiptHandle());
                        } else {
                            // delete the other messages now, so the queue is drained rather than redelivering them
                            // on every poll (unmatched or malformed -> discarded)
                            deleteMessage(message.receiptHandle());
                        }
                    }
                }
            } catch (Exception e) {
                LOG.warn(
                        "Error during AWS Secrets Refresh Task due to {}. This exception is ignored. Will try again on next run.",
                        e.getMessage(), e);
            }
        }

        if (triggerReloading) {
            ContextReloadStrategy reload = camelContext.hasService(ContextReloadStrategy.class);
            if (reload != null) {
                // trigger reload
                lastReloadTime = Instant.now();
                if (!reload(reload)) {
                    failedReloads++;
                    if (failedReloads < MAX_RELOAD_ATTEMPTS) {
                        LOG.warn("Reloading CamelContext after an update of AWS secrets failed (attempt {} of {})."
                                 + " The update is not marked as seen, so a later run sees it and reloads again.",
                                failedReloads, MAX_RELOAD_ATTEMPTS);
                        return;
                    }
                    LOG.warn("Reloading CamelContext after an update of AWS secrets failed {} times in a row."
                             + " The update is marked as seen: no reload is tried again until the next update.",
                            failedReloads);
                }
                failedReloads = 0;
            }
        }
        if (markSeen != null) {
            markSeen.run();
        }
    }

    /**
     * Reloads the CamelContext, and tells whether the reload succeeded: the reload strategy logs and counts a failed
     * reload, it does not throw it.
     */
    private boolean reload(ContextReloadStrategy reload) {
        int failed = reload.getFailedCounter();
        try {
            reload.onReload(this);
        } catch (Exception e) {
            LOG.warn("Error reloading CamelContext due to {}", e.getMessage(), e);
            return false;
        }
        return reload.getFailedCounter() == failed;
    }

    private void deleteMessage(String receiptHandle) {
        LOG.trace("Deleting message with receipt handle {}...", receiptHandle);
        try {
            sqsClient.deleteMessage(
                    DeleteMessageRequest.builder().queueUrl(queueUrl).receiptHandle(receiptHandle).build());
        } catch (Exception e) {
            LOG.warn("Error deleting SQS message due to {}. The queue delivers it again.", e.getMessage(), e);
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
