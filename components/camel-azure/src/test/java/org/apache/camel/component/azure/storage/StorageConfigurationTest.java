package org.apache.camel.component.azure.storage;

import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class StorageConfigurationTest extends CamelTestSupport {

    @Test
    public void createQueueConfiguration() throws Exception {
        StorageQueueComponent component = new StorageQueueComponent(context);
        StorageQueueEndpoint endpoint = (StorageQueueEndpoint) component.createEndpoint("azure-storage-queue:foo?account=fooAcc&key=MTIzNDU2Nzg5Cg==");

        assertEquals("foo", endpoint.getConfiguration().getResource());
        assertEquals("fooAcc", endpoint.getConfiguration().getAccount());
        assertEquals("MTIzNDU2Nzg5Cg==", endpoint.getConfiguration().getKey());
        assertNotNull(endpoint.getConfiguration().getConnectionString());
        assertNull(endpoint.getConfiguration().getQueueClient());
    }
}
