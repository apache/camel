package org.apache.camel.component.kinesis;

import com.amazonaws.services.kinesis.model.Record;
import org.apache.camel.Exchange;
import org.junit.Before;
import org.junit.Test;

import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by alina on 03.11.15.
 */

public class KinesisEnpointTest {
    public static final String TEST_MESSAGE = "test message";
    private KinesisEndpoint endpoint;

    @Before
    public void setUp() {
        endpoint = new KinesisEndpoint(
                "kinesis:console.aws.amazon.com?region=us-west-2&streamName=test&partitionKey=test_partition_key&applicationName=CommonKinesisConsumer",
                "console.aws.amazon.com?region=us-west-2&streamName=test&partitionKey=test_partition_key&applicationName=CommonKinesisConsumer",
                new KinesisComponent());
    }

    @Test
    public void testCreatingKinesisExchangeSetsHeaders() throws Exception {
        Record record = new Record();
        record.setData(ByteBuffer.wrap(TEST_MESSAGE.getBytes()));

        Exchange exchange = endpoint.createKinesisExchange(record);
        assertEquals(TEST_MESSAGE, new String((byte[]) exchange.getIn().getBody()));
    }

    @Test
    public void creatingExecutorUsesThreadPoolSettings() throws Exception {
        ThreadPoolExecutor executor = endpoint.createExecutor();
        assertEquals(KinesisConstants.CONSUMER_WORKER_NUMBER, executor.getCorePoolSize());
    }

    @Test
    public void assertSingleton() throws URISyntaxException {
        assertTrue(endpoint.isSingleton());
    }
}

