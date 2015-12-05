package org.apache.camel.component.kinesis;

import org.apache.camel.CamelContext;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * Created by alina on 03.11.15.
 */

public class KinesisComponentTest {

    private CamelContext context = Mockito.mock(CamelContext.class);

    @Test
    public void testPropertiesSet() throws Exception {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("region", "us-west-2");
        params.put("streamName", "test");
        params.put("partitionKey", "test_partition_key");
        params.put("applicationName", "CommonKinesisConsumer");


        String uri = "kinesis:console.aws.amazon.com?region=us-west-2&streamName=test&partitionKey=test_partition_key&applicationName=CommonKinesisConsumer";
        String remaining = "region=us-west-2&streamName=test&partitionKey=test_partition_key&applicationName=CommonKinesisConsumer";

        KinesisEndpoint endpoint = new KinesisComponent(context).createEndpoint(uri, remaining, params);
        assertEquals("us-west-2", endpoint.getRegion());
        assertEquals("CommonKinesisConsumer", endpoint.getApplicationName());
        assertEquals("test_partition_key", endpoint.getPartitionKey());
        assertEquals("test", endpoint.getStreamName());
    }
}
