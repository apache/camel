package org.apache.camel.metrics.counter;

import static org.apache.camel.metrics.MetricsComponent.HEADER_COUNTER_DECREMENT;
import static org.apache.camel.metrics.MetricsComponent.HEADER_COUNTER_INCREMENT;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
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

    private static final String METRICS_NAME = "metrics.name";
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

    @Mock
    private Message in;

    private CounterProducer producer;

    private InOrder inOrder;

    @Before
    public void setUp() throws Exception {
        producer = new CounterProducer(endpoint);
        inOrder = Mockito.inOrder(endpoint, exchange, registry, counter, in);
        when(endpoint.getRegistry()).thenReturn(registry);
        when(registry.counter(METRICS_NAME)).thenReturn(counter);
        when(exchange.getIn()).thenReturn(in);
    }

    @Test
    public void testCounterProducer() throws Exception {
        assertThat(producer.getEndpoint().equals(endpoint), is(true));
    }

    @Test
    public void testProcessWithIncrementOnly() throws Exception {
        when(endpoint.getIncrement()).thenReturn(INCREMENT);
        when(endpoint.getDecrement()).thenReturn(null);
        when(in.getHeader(HEADER_COUNTER_INCREMENT, INCREMENT, Long.class)).thenReturn(INCREMENT);
        when(in.getHeader(HEADER_COUNTER_DECREMENT, null, Long.class)).thenReturn(null);
        producer.doProcess(exchange, endpoint, registry, METRICS_NAME);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(registry, times(1)).counter(METRICS_NAME);
        inOrder.verify(endpoint, times(1)).getIncrement();
        inOrder.verify(endpoint, times(1)).getDecrement();
        inOrder.verify(in, times(1)).getHeader(HEADER_COUNTER_INCREMENT, INCREMENT, Long.class);
        inOrder.verify(in, times(1)).getHeader(HEADER_COUNTER_DECREMENT, null, Long.class);
        inOrder.verify(counter, times(1)).inc(INCREMENT);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testProcessWithDecrementOnly() throws Exception {
        when(endpoint.getIncrement()).thenReturn(null);
        when(endpoint.getDecrement()).thenReturn(DECREMENT);
        when(in.getHeader(HEADER_COUNTER_INCREMENT, null, Long.class)).thenReturn(null);
        when(in.getHeader(HEADER_COUNTER_DECREMENT, DECREMENT, Long.class)).thenReturn(DECREMENT);
        producer.doProcess(exchange, endpoint, registry, METRICS_NAME);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(registry, times(1)).counter(METRICS_NAME);
        inOrder.verify(endpoint, times(1)).getIncrement();
        inOrder.verify(endpoint, times(1)).getDecrement();
        inOrder.verify(in, times(1)).getHeader(HEADER_COUNTER_INCREMENT, null, Long.class);
        inOrder.verify(in, times(1)).getHeader(HEADER_COUNTER_DECREMENT, DECREMENT, Long.class);
        inOrder.verify(counter, times(1)).dec(DECREMENT);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testDoProcessWithIncrementAndDecrement() throws Exception {
        when(endpoint.getIncrement()).thenReturn(INCREMENT);
        when(endpoint.getDecrement()).thenReturn(DECREMENT);
        when(in.getHeader(HEADER_COUNTER_INCREMENT, INCREMENT, Long.class)).thenReturn(INCREMENT);
        when(in.getHeader(HEADER_COUNTER_DECREMENT, DECREMENT, Long.class)).thenReturn(DECREMENT);
        producer.doProcess(exchange, endpoint, registry, METRICS_NAME);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(registry, times(1)).counter(METRICS_NAME);
        inOrder.verify(endpoint, times(1)).getIncrement();
        inOrder.verify(endpoint, times(1)).getDecrement();
        inOrder.verify(in, times(1)).getHeader(HEADER_COUNTER_INCREMENT, INCREMENT, Long.class);
        inOrder.verify(in, times(1)).getHeader(HEADER_COUNTER_DECREMENT, DECREMENT, Long.class);
        inOrder.verify(counter, times(1)).inc(INCREMENT);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testProcessWithOutIncrementAndDecrement() throws Exception {
        when(endpoint.getIncrement()).thenReturn(null);
        when(endpoint.getDecrement()).thenReturn(null);
        when(in.getHeader(HEADER_COUNTER_INCREMENT, null, Long.class)).thenReturn(null);
        when(in.getHeader(HEADER_COUNTER_DECREMENT, null, Long.class)).thenReturn(null);
        producer.doProcess(exchange, endpoint, registry, METRICS_NAME);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(registry, times(1)).counter(METRICS_NAME);
        inOrder.verify(endpoint, times(1)).getIncrement();
        inOrder.verify(endpoint, times(1)).getDecrement();
        inOrder.verify(in, times(1)).getHeader(HEADER_COUNTER_INCREMENT, null, Long.class);
        inOrder.verify(in, times(1)).getHeader(HEADER_COUNTER_DECREMENT, null, Long.class);
        inOrder.verify(counter, times(1)).inc();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testProcessWithHeaderValuesOnly() throws Exception {
        when(endpoint.getIncrement()).thenReturn(null);
        when(endpoint.getDecrement()).thenReturn(null);
        when(in.getHeader(HEADER_COUNTER_INCREMENT, null, Long.class)).thenReturn(INCREMENT + 1);
        when(in.getHeader(HEADER_COUNTER_DECREMENT, null, Long.class)).thenReturn(DECREMENT - 1);
        producer.doProcess(exchange, endpoint, registry, METRICS_NAME);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(registry, times(1)).counter(METRICS_NAME);
        inOrder.verify(endpoint, times(1)).getIncrement();
        inOrder.verify(endpoint, times(1)).getDecrement();
        inOrder.verify(in, times(1)).getHeader(HEADER_COUNTER_INCREMENT, null, Long.class);
        inOrder.verify(in, times(1)).getHeader(HEADER_COUNTER_DECREMENT, null, Long.class);
        inOrder.verify(counter, times(1)).inc(INCREMENT + 1);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testProcessOverridingIncrement() throws Exception {
        when(endpoint.getIncrement()).thenReturn(INCREMENT);
        when(endpoint.getDecrement()).thenReturn(DECREMENT);
        when(in.getHeader(HEADER_COUNTER_INCREMENT, INCREMENT, Long.class)).thenReturn(INCREMENT + 1);
        when(in.getHeader(HEADER_COUNTER_DECREMENT, DECREMENT, Long.class)).thenReturn(DECREMENT);
        producer.doProcess(exchange, endpoint, registry, METRICS_NAME);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(registry, times(1)).counter(METRICS_NAME);
        inOrder.verify(endpoint, times(1)).getIncrement();
        inOrder.verify(endpoint, times(1)).getDecrement();
        inOrder.verify(in, times(1)).getHeader(HEADER_COUNTER_INCREMENT, INCREMENT, Long.class);
        inOrder.verify(in, times(1)).getHeader(HEADER_COUNTER_DECREMENT, DECREMENT, Long.class);
        inOrder.verify(counter, times(1)).inc(INCREMENT + 1);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testProcessOverridingDecrement() throws Exception {
        when(endpoint.getIncrement()).thenReturn(null);
        when(endpoint.getDecrement()).thenReturn(DECREMENT);
        when(in.getHeader(HEADER_COUNTER_INCREMENT, null, Long.class)).thenReturn(null);
        when(in.getHeader(HEADER_COUNTER_DECREMENT, DECREMENT, Long.class)).thenReturn(DECREMENT - 1);
        producer.doProcess(exchange, endpoint, registry, METRICS_NAME);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(registry, times(1)).counter(METRICS_NAME);
        inOrder.verify(endpoint, times(1)).getIncrement();
        inOrder.verify(endpoint, times(1)).getDecrement();
        inOrder.verify(in, times(1)).getHeader(HEADER_COUNTER_INCREMENT, null, Long.class);
        inOrder.verify(in, times(1)).getHeader(HEADER_COUNTER_DECREMENT, DECREMENT, Long.class);
        inOrder.verify(counter, times(1)).dec(DECREMENT - 1);
        inOrder.verifyNoMoreInteractions();
    }
}
