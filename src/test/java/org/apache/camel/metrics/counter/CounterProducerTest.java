package org.apache.camel.metrics.counter;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import org.apache.camel.Exchange;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;

@RunWith(MockitoJUnitRunner.class)
public class CounterProducerTest {

    private static final String ENDPOINT_NAME = "endpoint.name";
    private static final Long INCREMENT = 100000L;
    private static final Long DECREMENT = 91929199L;

    @Mock
    private CounterEndpoint endpoint;

    @Mock
    private Exchange exchange;

    @Mock
    private MetricRegistry registry;

    @Mock
    private Counter counter;

    private CounterProducer producer;

    private InOrder inOrder;

    @Before
    public void setUp() throws Exception {
        producer = new CounterProducer(endpoint);
        inOrder = Mockito.inOrder(endpoint, exchange, registry, counter);
        when(endpoint.getMetricsName()).thenReturn(ENDPOINT_NAME);
        when(endpoint.getRegistry()).thenReturn(registry);
        when(registry.counter(ENDPOINT_NAME)).thenReturn(counter);
    }

    @Test
    public void testCounterProducer() throws Exception {
        assertThat(producer.getEndpoint().equals(endpoint), is(true));
    }

    @Test
    public void testProcessWithIncrementOnly() throws Exception {
        when(endpoint.getIncrement()).thenReturn(INCREMENT);
        when(endpoint.getDecrement()).thenReturn(null);
        producer.process(exchange);
        inOrder.verify(endpoint, times(1)).getMetricsName();
        inOrder.verify(endpoint, times(1)).getRegistry();
        inOrder.verify(registry, times(1)).counter(ENDPOINT_NAME);
        inOrder.verify(endpoint, times(1)).getIncrement();
        inOrder.verify(endpoint, times(1)).getDecrement();
        inOrder.verify(counter, times(1)).inc(INCREMENT);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testProcessWithDecrementOnly() throws Exception {
        when(endpoint.getIncrement()).thenReturn(null);
        when(endpoint.getDecrement()).thenReturn(DECREMENT);
        producer.process(exchange);
        inOrder.verify(endpoint, times(1)).getMetricsName();
        inOrder.verify(endpoint, times(1)).getRegistry();
        inOrder.verify(registry, times(1)).counter(ENDPOINT_NAME);
        inOrder.verify(endpoint, times(1)).getIncrement();
        inOrder.verify(endpoint, times(1)).getDecrement();
        inOrder.verify(counter, times(1)).dec(DECREMENT);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testProcessWithIncrementAndDecrement() throws Exception {
        when(endpoint.getIncrement()).thenReturn(INCREMENT);
        when(endpoint.getDecrement()).thenReturn(DECREMENT);
        producer.process(exchange);
        inOrder.verify(endpoint, times(1)).getMetricsName();
        inOrder.verify(endpoint, times(1)).getRegistry();
        inOrder.verify(registry, times(1)).counter(ENDPOINT_NAME);
        inOrder.verify(endpoint, times(1)).getIncrement();
        inOrder.verify(endpoint, times(1)).getDecrement();
        inOrder.verify(counter, times(1)).inc(INCREMENT);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testProcessWithOutIncrementAndDecrement() throws Exception {
        when(endpoint.getIncrement()).thenReturn(null);
        when(endpoint.getDecrement()).thenReturn(null);
        producer.process(exchange);
        inOrder.verify(endpoint, times(1)).getMetricsName();
        inOrder.verify(endpoint, times(1)).getRegistry();
        inOrder.verify(registry, times(1)).counter(ENDPOINT_NAME);
        inOrder.verify(endpoint, times(1)).getIncrement();
        inOrder.verify(endpoint, times(1)).getDecrement();
        inOrder.verify(counter, times(1)).inc();
        inOrder.verifyNoMoreInteractions();
    }
}
