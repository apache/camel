package org.apache.camel.metrics.meter;

import static org.apache.camel.metrics.MetricsComponent.HEADER_METER_MARK;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
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

    @Mock
    private Message in;

    private MeterProducer producer;

    private InOrder inOrder;

    @Before
    public void setUp() throws Exception {
        producer = new MeterProducer(endpoint);
        inOrder = Mockito.inOrder(endpoint, registry, meter, exchange, in);
        when(endpoint.getRegistry()).thenReturn(registry);
        when(registry.meter(METRICS_NAME)).thenReturn(meter);
        when(exchange.getIn()).thenReturn(in);
    }

    @Test
    public void testMeterProducer() throws Exception {
        assertThat(producer, is(notNullValue()));
        assertThat(producer.getEndpoint().equals(endpoint), is(true));
    }

    @Test
    public void testProcessMarkSet() throws Exception {
        when(endpoint.getMark()).thenReturn(MARK);
        when(in.getHeader(HEADER_METER_MARK, MARK, Long.class)).thenReturn(MARK);
        producer.doProcess(exchange, endpoint, registry, METRICS_NAME);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(registry, times(1)).meter(METRICS_NAME);
        inOrder.verify(endpoint, times(1)).getMark();
        inOrder.verify(in, times(1)).getHeader(HEADER_METER_MARK, MARK, Long.class);
        inOrder.verify(meter, times(1)).mark(MARK);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testProcessMarkSetOverrideByHeaderValue() throws Exception {
        when(endpoint.getMark()).thenReturn(MARK);
        when(in.getHeader(HEADER_METER_MARK, MARK, Long.class)).thenReturn(MARK + 101);
        producer.doProcess(exchange, endpoint, registry, METRICS_NAME);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(registry, times(1)).meter(METRICS_NAME);
        inOrder.verify(endpoint, times(1)).getMark();
        inOrder.verify(in, times(1)).getHeader(HEADER_METER_MARK, MARK, Long.class);
        inOrder.verify(meter, times(1)).mark(MARK + 101);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testProcessMarkNotSet() throws Exception {
        when(endpoint.getMark()).thenReturn(null);
        when(in.getHeader(HEADER_METER_MARK, null, Long.class)).thenReturn(null);
        producer.doProcess(exchange, endpoint, registry, METRICS_NAME);
        inOrder.verify(registry, times(1)).meter(METRICS_NAME);
        inOrder.verify(endpoint, times(1)).getMark();
        inOrder.verify(in, times(1)).getHeader(HEADER_METER_MARK, null, Long.class);
        inOrder.verify(meter, times(1)).mark();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testProcessMarkNotSetOverrideByHeaderValue() throws Exception {
        when(endpoint.getMark()).thenReturn(null);
        when(in.getHeader(HEADER_METER_MARK, null, Long.class)).thenReturn(MARK);
        producer.doProcess(exchange, endpoint, registry, METRICS_NAME);
        inOrder.verify(registry, times(1)).meter(METRICS_NAME);
        inOrder.verify(endpoint, times(1)).getMark();
        inOrder.verify(in, times(1)).getHeader(HEADER_METER_MARK, null, Long.class);
        inOrder.verify(meter, times(1)).mark(MARK);
        inOrder.verifyNoMoreInteractions();
    }
}
