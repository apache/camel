package org.apache.camel.component.azure.storage.integration;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assume.assumeNotNull;

/**
 * These tests will only be run if the AZURE_STORAGE_ACCOUNT and
 * AZURE_STORAGE_KEY environment variables are set.
 */
public class StorageComponentsIntegrationTest extends CamelTestSupport {

    @BeforeClass
    public static void checkCredentials() {
        assumeNotNull(System.getenv("AZURE_STORAGE_ACCOUNT"));
        assumeNotNull(System.getenv("AZURE_STORAGE_KEY"));
    }

    @Test
    public void testAzureStorageQueue() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);       
        
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("azure-storage-queue:foo?account=${env:AZURE_STORAGE_ACCOUNT}&key=${env:AZURE_STORAGE_KEY}")
                  .to("azure-storage-queue:bar?account=${env:AZURE_STORAGE_ACCOUNT}&key=${env:AZURE_STORAGE_KEY}")
                  .to("mock:result");
            }
        };
    }
}
