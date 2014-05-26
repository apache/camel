package org.apache.camel.metrics.histogram;

import static org.apache.camel.metrics.MetricsComponent.HEADER_HISTOGRAM_VALUE;
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

import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;

@RunWith(MockitoJUnitRunner.class)
public class HistogramProducerTest {

    private static final String METRICS_NAME = "metrics.name";
    private static final Long VALUE = System.currentTimeMillis();

    @Mock
    private HistogramEndpoint endpoint;

    @Mock
    private MetricRegistry registry;

    @Mock
    private Histogram histogram;

    @Mock
    private Exchange exchange;

    private HistogramProducer producer;

    private InOrder inOrder;

    @Before
    public void setUp() throws Exception {
        producer = new HistogramProducer(endpoint);
        inOrder = Mockito.inOrder(endpoint, registry, histogram, exchange);
        when(endpoint.getRegistry()).thenReturn(registry);
        when(endpoint.getMetricsName(exchange)).thenReturn(METRICS_NAME);
        when(registry.histogram(METRICS_NAME)).thenReturn(histogram);
    }

    @Test
    public void testHistogramProducer() throws Exception {
        assertThat(producer.getEndpoint().equals(endpoint), is(true));
    }

    @Test
    public void testProcessValueSet() throws Exception {
        when(endpoint.getValue()).thenReturn(VALUE);
        when(endpoint.getLongHeader(exchange, HEADER_HISTOGRAM_VALUE, VALUE)).thenReturn(VALUE);
        producer.process(exchange);
        inOrder.verify(endpoint, times(1)).getRegistry();
        inOrder.verify(endpoint, times(1)).getMetricsName(exchange);
        inOrder.verify(registry, times(1)).histogram(METRICS_NAME);
        inOrder.verify(endpoint, times(1)).getValue();
        inOrder.verify(endpoint, times(1)).getLongHeader(exchange, HEADER_HISTOGRAM_VALUE, VALUE);
        inOrder.verify(histogram, times(1)).update(VALUE);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testProcessValueNotSet() throws Exception {
        when(endpoint.getValue()).thenReturn(null);
        when(endpoint.getLongHeader(exchange, HEADER_HISTOGRAM_VALUE, null)).thenReturn(null);
        producer.process(exchange);
        inOrder.verify(endpoint, times(1)).getRegistry();
        inOrder.verify(endpoint, times(1)).getMetricsName(exchange);
        inOrder.verify(registry, times(1)).histogram(METRICS_NAME);
        inOrder.verify(endpoint, times(1)).getValue();
        inOrder.verify(endpoint, times(1)).getLongHeader(exchange, HEADER_HISTOGRAM_VALUE, null);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testProcessOverrideValue() throws Exception {
        when(endpoint.getValue()).thenReturn(VALUE);
        when(endpoint.getLongHeader(exchange, HEADER_HISTOGRAM_VALUE, VALUE)).thenReturn(VALUE + 3);
        producer.process(exchange);
        inOrder.verify(endpoint, times(1)).getRegistry();
        inOrder.verify(endpoint, times(1)).getMetricsName(exchange);
        inOrder.verify(registry, times(1)).histogram(METRICS_NAME);
        inOrder.verify(endpoint, times(1)).getValue();
        inOrder.verify(endpoint, times(1)).getLongHeader(exchange, HEADER_HISTOGRAM_VALUE, VALUE);
        inOrder.verify(histogram, times(1)).update(VALUE + 3);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testProcessOverrideUriValueNotSet() throws Exception {
        when(endpoint.getValue()).thenReturn(null);
        when(endpoint.getLongHeader(exchange, HEADER_HISTOGRAM_VALUE, null)).thenReturn(VALUE + 2);
        producer.process(exchange);
        inOrder.verify(endpoint, times(1)).getRegistry();
        inOrder.verify(endpoint, times(1)).getMetricsName(exchange);
        inOrder.verify(registry, times(1)).histogram(METRICS_NAME);
        inOrder.verify(endpoint, times(1)).getValue();
        inOrder.verify(endpoint, times(1)).getLongHeader(exchange, HEADER_HISTOGRAM_VALUE, null);
        inOrder.verify(histogram, times(1)).update(VALUE + 2);
        inOrder.verifyNoMoreInteractions();
    }
}
