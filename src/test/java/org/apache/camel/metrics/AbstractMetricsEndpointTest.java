package org.apache.camel.metrics;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import com.codahale.metrics.MetricRegistry;

@RunWith(MockitoJUnitRunner.class)
public class AbstractMetricsEndpointTest {

    private static final String METRICS_NAME = "metrics.name";
    private static final String HEADER = "header";
    private static final String STRING_VALUE = "string-value";
    private static final String DEFAULT_STRING_VALUE = "default-value";
    private static final Long LONG_VALUE = System.currentTimeMillis();
    private static final Long DEFAULT_LONG_VALUE = -10101L;

    @Mock
    private MetricRegistry registry;

    @Mock
    private Processor processor;

    @Mock
    private Exchange exchange;

    @Mock
    private Message in;

    private AbstractMetricsEndpoint endpoint;

    private InOrder inOrder;

    @Before
    public void setUp() throws Exception {
        endpoint = new AbstractMetricsEndpoint(registry, METRICS_NAME) {
            @Override
            public Producer createProducer() throws Exception {
                return null;
            }

            @Override
            protected String createEndpointUri() {
                return "not real endpoint";
            }
        };
        inOrder = Mockito.inOrder(registry, processor, exchange, in);
        when(exchange.getIn()).thenReturn(in);
    }

    @After
    public void tearDown() throws Exception {
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testAbstractMetricsEndpoint() throws Exception {
        assertThat(endpoint.getMetricsName(), is(METRICS_NAME));
        assertThat(endpoint.getRegistry(), is(registry));
    }

    @Test(expected = RuntimeCamelException.class)
    public void testCreateConsumer() throws Exception {
        endpoint.createConsumer(processor);
    }

    @Test
    public void testIsSingleton() throws Exception {
        assertThat(endpoint.isSingleton(), is(false));
    }

    @Test
    public void testGetRegistry() throws Exception {
        assertThat(endpoint.getRegistry(), is(registry));
    }

    @Test
    public void testGetMetricsName() throws Exception {
        assertThat(endpoint.getMetricsName(), is(METRICS_NAME));
    }

    @Test
    public void testGetStringHeaderValue() {
        when(in.getHeader(HEADER, String.class)).thenReturn(STRING_VALUE);
        assertThat(endpoint.getStringHeader(exchange, HEADER, DEFAULT_STRING_VALUE), is(STRING_VALUE));
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(in, times(1)).getHeader(HEADER, String.class);
    }

    @Test
    public void testGetStringHeaderValueNotFound() {
        when(in.getHeader(HEADER, String.class)).thenReturn(null);
        assertThat(endpoint.getStringHeader(exchange, HEADER, DEFAULT_STRING_VALUE), is(DEFAULT_STRING_VALUE));
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(in, times(1)).getHeader(HEADER, String.class);
    }

    @Test
    public void testGetStringHeaderValueEmptyString() {
        when(in.getHeader(HEADER, String.class)).thenReturn("");
        assertThat(endpoint.getStringHeader(exchange, HEADER, DEFAULT_STRING_VALUE), is(DEFAULT_STRING_VALUE));
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(in, times(1)).getHeader(HEADER, String.class);
    }

    @Test
    public void testGetStringHeaderValueWhiteSpaces() {
        when(in.getHeader(HEADER, String.class)).thenReturn(" \n\t\r");
        assertThat(endpoint.getStringHeader(exchange, HEADER, DEFAULT_STRING_VALUE), is(DEFAULT_STRING_VALUE));
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(in, times(1)).getHeader(HEADER, String.class);
    }

    @Test
    public void testGetLongHeaderValue() {
        when(in.getHeader(HEADER, DEFAULT_LONG_VALUE, Long.class)).thenReturn(LONG_VALUE);
        assertThat(endpoint.getLongHeader(exchange, HEADER, DEFAULT_LONG_VALUE), is(LONG_VALUE));
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(in, times(1)).getHeader(HEADER, DEFAULT_LONG_VALUE, Long.class);
    }

    @Test
    public void testGetLongHeaderValueNotFound() {
        when(in.getHeader(HEADER, DEFAULT_LONG_VALUE, Long.class)).thenReturn(DEFAULT_LONG_VALUE);
        assertThat(endpoint.getLongHeader(exchange, HEADER, DEFAULT_LONG_VALUE), is(DEFAULT_LONG_VALUE));
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(in, times(1)).getHeader(HEADER, DEFAULT_LONG_VALUE, Long.class);
    }

    @Test
    public void testGetMetricsNameFromExchange() {
        when(in.getHeader(MetricsComponent.HEADER_METRIC_NAME, String.class)).thenReturn(STRING_VALUE);
        assertThat(endpoint.getMetricsName(exchange), is(STRING_VALUE));
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(in, times(1)).getHeader(MetricsComponent.HEADER_METRIC_NAME, String.class);
    }

    @Test
    public void testGetMetricsNameFromExchangeNotFound() {
        when(in.getHeader(MetricsComponent.HEADER_METRIC_NAME, String.class)).thenReturn(null);
        assertThat(endpoint.getMetricsName(exchange), is(METRICS_NAME));
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(in, times(1)).getHeader(MetricsComponent.HEADER_METRIC_NAME, String.class);
    }
}
