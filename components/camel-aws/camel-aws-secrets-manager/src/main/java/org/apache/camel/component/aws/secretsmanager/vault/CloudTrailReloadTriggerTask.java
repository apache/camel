package org.apache.camel.component.aws.secretsmanager.vault;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.ContextReloadStrategy;
import org.apache.camel.vault.AwsVaultConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudtrail.CloudTrailClient;
import software.amazon.awssdk.services.cloudtrail.CloudTrailClientBuilder;
import software.amazon.awssdk.services.cloudtrail.model.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

public class CloudTrailReloadTriggerTask implements Runnable {
    private CamelContext context;
    private String secretNameList;
    private static Instant lastTime = null;
    private static String eventSourceSecrets = "secretsmanager.amazonaws.com";

    private static final Logger LOG = LoggerFactory.getLogger(CloudTrailReloadTriggerTask.class);

    public CloudTrailReloadTriggerTask(CamelContext context, String secretName) {
        this.context = context;
        this.secretNameList = secretName;
    }

    @Override
    public void run() {
        List<String> secretNames = Arrays.asList(secretNameList.split(","));
        boolean triggerReloading = false;
        CloudTrailClientBuilder cloudTrailClientBuilder;
        Region regionValue = Region.of(context.getVaultConfiguration().aws().getRegion());
        if (context.getVaultConfiguration().aws().isDefaultCredentialsProvider()) {
            cloudTrailClientBuilder = CloudTrailClient.builder()
                    .region(regionValue)
                    .credentialsProvider(ProfileCredentialsProvider.create());
        } else {
            AwsBasicCredentials cred = AwsBasicCredentials.create(context.getVaultConfiguration().aws().getAccessKey(), context.getVaultConfiguration().aws().getSecretKey());
            cloudTrailClientBuilder = CloudTrailClient.builder().credentialsProvider(StaticCredentialsProvider.create(cred));
        }
        CloudTrailClient cloudTrailClient = cloudTrailClientBuilder.build();
        try {
            LookupEventsRequest.Builder eventsRequestBuilder = LookupEventsRequest.builder()
                    .maxResults(100).lookupAttributes(LookupAttribute.builder().attributeKey(LookupAttributeKey.EVENT_SOURCE).attributeValue(eventSourceSecrets).build());

            if (lastTime != null) {
                eventsRequestBuilder.startTime(lastTime.plusMillis(1000));
            }

            LookupEventsRequest lookupEventsRequest = eventsRequestBuilder.build();

            LookupEventsResponse response = cloudTrailClient.lookupEvents(lookupEventsRequest);
            List<Event> events = response.events();

            if (events.size() > 0) {
                lastTime = events.get(0).eventTime();
            }

            LOG.info("Found " + events.size() + " events");
            for (Event event : events) {
                if (event.eventSource().equalsIgnoreCase(eventSourceSecrets)) {
                    if (event.eventName().equalsIgnoreCase("PutSecretValue")) {
                        List<Resource> a = event.resources();
                        for (Resource res : a) {
                            for (String secretNameElem : secretNames) {
                                if (res.resourceName().contains(secretNameElem)) {
                                    LOG.info("Update for secret " + secretNameElem + " detected, triggering a context reload");
                                    triggerReloading = true;
                                    break;
                                }
                            }
                        }
                    }
                }
            }

        } catch (CloudTrailException e) {
            throw e;
        }
        if (triggerReloading) {
            if (context != null) {
                ContextReloadStrategy reload = context.hasService(ContextReloadStrategy.class);
                if (reload != null) {
                    // trigger reload
                    reload.onReload(context.getName());
                }
            }
        }
    }
}
