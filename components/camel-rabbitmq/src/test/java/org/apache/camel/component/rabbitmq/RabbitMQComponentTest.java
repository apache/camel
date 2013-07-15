package org.apache.camel.component.rabbitmq;

import org.apache.camel.CamelContext;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * @author Stephen Samuel
 */
public class RabbitMQComponentTest {

    private CamelContext context = Mockito.mock(CamelContext.class);

    @Test
    public void testPropertiesSet() throws Exception {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("username", "coldplay");
        params.put("password", "chrism");
        params.put("autoAck", true);
        params.put("vhost", "vman");
        params.put("threadPoolSize", 515);
        params.put("portNumber", 14123);
        params.put("hostname", "special.host");
        params.put("queue", "queuey");

        String uri = "rabbitmq:special.host:14/queuey";
        String remaining = "special.host:14/queuey";

        RabbitMQEndpoint endpoint = new RabbitMQComponent(context).createEndpoint(uri, remaining, params);
        assertEquals("chrism", endpoint.getPassword());
        assertEquals("coldplay", endpoint.getUsername());
        assertEquals("queuey", endpoint.getQueue());
        assertEquals("vman", endpoint.getVhost());
        assertEquals("special.host", endpoint.getHostname());
        assertEquals(14, endpoint.getPortNumber());
        assertEquals(515, endpoint.getThreadPoolSize());
        assertEquals(true, endpoint.isAutoAck());
    }
}
