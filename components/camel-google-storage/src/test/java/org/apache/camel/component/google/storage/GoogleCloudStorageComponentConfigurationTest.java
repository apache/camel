package org.apache.camel.component.google.storage;

import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GoogleCloudStorageComponentConfigurationTest extends CamelTestSupport {

    @Test
    public void createEndpointWithMinimalConfiguration() throws Exception {
        GoogleCloudStorageComponent component = context.getComponent("google-storage", GoogleCloudStorageComponent.class);
        GoogleCloudStorageEndpoint endpoint = (GoogleCloudStorageEndpoint) component.createEndpoint(
                "google-storage://rafa_test_bucket?serviceAccountCredentials=somefile.json");

        assertEquals(endpoint.getConfiguration().getServiceAccountCredentials(), "somefile.json");
    }

}
