package org.apache.camel.component.kafka;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;

/**
 * @author Stephen Samuel
 */
public class KafkaComponentTest {

    private CamelContext context = Mockito.mock(CamelContext.class);

    @Test
    public void testPropertiesSet() throws Exception {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("zookeeperHost", "somehost");
        params.put("zookeeperPort", 2987);
        params.put("portNumber", 14123);
        params.put("consumerStreams", "3");
        params.put("topic", "mytopic");
        params.put("partitioner", "com.class.Party");

        String uri = "kafka:broker1:12345,broker2:12566";
        String remaining = "broker1:12345,broker2:12566";

        KafkaEndpoint endpoint = new KafkaComponent(context).createEndpoint(uri, remaining, params);
        assertEquals("somehost", endpoint.getZookeeperHost());
        assertEquals(2987, endpoint.getZookeeperPort());
        assertEquals("broker1:12345,broker2:12566", endpoint.getBrokers());
        assertEquals("mytopic", endpoint.getTopic());
        assertEquals(3, endpoint.getConsumerStreams());
        assertEquals("com.class.Party", endpoint.getPartitioner());
    }
}
