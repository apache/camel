package org.apache.camel.component.google.storage.unit;

import org.apache.camel.component.google.storage.GoogleCloudStorageComponent;
import org.apache.camel.component.google.storage.GoogleCloudStorageComponentConfiguration;
import org.apache.camel.component.google.storage.GoogleCloudStorageEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GoogleCloudStorageComponentConfigurationTest extends CamelTestSupport {

    @Test
    public void createEndpointWithMinimalConfiguration() throws Exception {
        final String bucketName = "myCamelBucket";
        final String serviceAccountKeyFile = "somefile.json";

        GoogleCloudStorageComponent component = context.getComponent("google-storage", GoogleCloudStorageComponent.class);
        GoogleCloudStorageEndpoint endpoint = (GoogleCloudStorageEndpoint) component.createEndpoint(
                String.format("google-storage://%s?serviceAccountKey=%s", bucketName, serviceAccountKeyFile));

        assertEquals(endpoint.getConfiguration().getBucketName(), bucketName);
        assertEquals(endpoint.getConfiguration().getServiceAccountKey(), serviceAccountKeyFile);
    }

    public void createEndpointForComplexConsumer() throws Exception {

        final String bucketName = "sourceCamelBucket";
        final String serviceAccountKeyFile = "somefile.json";
        final boolean moveAfterRead = false;
        final String destinationBucket = "destinationCamelBucket";
        final boolean autoCreateBucket = true;
        final boolean deleteAfterRead = false;
        final boolean includeBody = true;

        GoogleCloudStorageComponent component = context.getComponent("google-storage", GoogleCloudStorageComponent.class);
        GoogleCloudStorageEndpoint endpoint = (GoogleCloudStorageEndpoint) component.createEndpoint(
                String.format(
                        "google-storage://%s?serviceAccountKey=%s&moveAfterRead=%s&destinationBucket=%s&autoCreateBucket=%s&deleteAfterRead=%s&includeBody=%s",
                        bucketName, serviceAccountKeyFile, moveAfterRead, destinationBucket, autoCreateBucket,
                        deleteAfterRead, includeBody));

        GoogleCloudStorageComponentConfiguration configuration = endpoint.getConfiguration();
        assertEquals(configuration.getBucketName(), bucketName);
        assertEquals(configuration.getServiceAccountKey(), serviceAccountKeyFile);
        assertEquals(configuration.isMoveAfterRead(), moveAfterRead);
        assertEquals(configuration.getDestinationBucket(), destinationBucket);
        assertEquals(configuration.isAutoCreateBucket(), autoCreateBucket);
        assertEquals(configuration.isDeleteAfterRead(), deleteAfterRead);
        assertEquals(configuration.isIncludeBody(), includeBody);

    }

}
