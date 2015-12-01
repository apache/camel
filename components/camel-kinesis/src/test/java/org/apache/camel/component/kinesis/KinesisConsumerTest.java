package org.apache.camel.component.kinesis;

import org.apache.camel.Processor;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.concurrent.ThreadPoolExecutor;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Created by alina on 03.11.15.
 */
public class KinesisConsumerTest {

    private KinesisEndpoint endpoint = mock(KinesisEndpoint.class);
    private Processor processor = mock(Processor.class);

    @Test(expected = IllegalArgumentException.class)
    public void consumerRequiresStreamName() throws Exception {
        Mockito.when(endpoint.getStreamName()).thenReturn("test");
        new KinesisConsumer(endpoint, processor);
    }

    @Test(expected = IllegalArgumentException.class)
    public void consumerRequiresRegion() throws Exception {
        Mockito.when(endpoint.getRegion()).thenReturn("us-west-2");
        new KinesisConsumer(endpoint, processor);
    }

    @Test
    public void testStoppingConsumerShutsdownExecutor() throws Exception {

        when(endpoint.getStreamName()).thenReturn("test");
        when(endpoint.getRegion()).thenReturn("us-west-2");
        when(endpoint.getApplicationName()).thenReturn("CommonKinesisConsumer");

        KinesisConsumer consumer = new KinesisConsumer(endpoint, processor);

        ThreadPoolExecutor e = mock(ThreadPoolExecutor.class);
        consumer.executor = e;
        consumer.doStop();
        verify(e).shutdown();
    }
}
