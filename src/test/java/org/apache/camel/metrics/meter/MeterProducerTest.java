package org.apache.camel.metrics.meter;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
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

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;

@RunWith(MockitoJUnitRunner.class)
public class MeterProducerTest {

    private static final String METRICS_NAME = "metrics.name";
    private static final Long MARK = 9919120L;

    @Mock
    private MeterEndpoint endpoint;

    @Mock
    private MetricRegistry registry;

    @Mock
    private Meter meter;

    @Mock
    private Exchange exchange;

    private MeterProducer producer;

    private InOrder inOrder;

    @Before
    public void setUp() throws Exception {
        producer = new MeterProducer(endpoint);
        inOrder = Mockito.inOrder(endpoint, registry, meter, exchange);
        when(endpoint.getRegistry()).thenReturn(registry);
        when(endpoint.getMetricsName()).thenReturn(METRICS_NAME);
        when(registry.meter(METRICS_NAME)).thenReturn(meter);
    }

    @Test
    public void testMeterProducer() throws Exception {
        assertThat(producer, is(notNullValue()));
        assertThat(producer.getEndpoint().equals(endpoint), is(true));
    }

    @Test
    public void testProcessMarkSet() throws Exception {
        when(endpoint.getMark()).thenReturn(MARK);
        producer.process(exchange);
        inOrder.verify(endpoint, times(1)).getMetricsName();
        inOrder.verify(endpoint, times(1)).getRegistry();
        inOrder.verify(registry, times(1)).meter(METRICS_NAME);
        inOrder.verify(endpoint, times(1)).getMark();
        inOrder.verify(meter, times(1)).mark(MARK);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testProcessMarkNotSet() throws Exception {
        when(endpoint.getMark()).thenReturn(null);
        producer.process(exchange);
        inOrder.verify(endpoint, times(1)).getMetricsName();
        inOrder.verify(endpoint, times(1)).getRegistry();
        inOrder.verify(registry, times(1)).meter(METRICS_NAME);
        inOrder.verify(endpoint, times(1)).getMark();
        inOrder.verify(meter, times(1)).mark();
        inOrder.verifyNoMoreInteractions();
    }
}
