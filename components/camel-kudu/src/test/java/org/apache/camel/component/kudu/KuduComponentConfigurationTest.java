package org.apache.camel.component.kudu;

import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class KuduComponentConfigurationTest extends CamelTestSupport {

    @Test
    public void createEndpoint() throws Exception {
        String host = "localhost";
        String port = "7051";
        String tableName = "TableName";
        KuduOperations operation = KuduOperations.SCAN;

        KuduComponent component = new KuduComponent(this.context());
        KuduEndpoint endpoint = (KuduEndpoint) component.createEndpoint("kudu:" + host + ":" + port + "/" + tableName + "?operation=" + operation);

        assertEquals("Host was not correctly detected. ", host, endpoint.getHost());
        assertEquals("Port was not correctly detected. ", port, endpoint.getPort());
        assertEquals("Table name was not correctly detected. ", tableName, endpoint.getTableName());
        assertEquals("Operation was not correctly detected. ", operation, endpoint.getOperation());
    }
}