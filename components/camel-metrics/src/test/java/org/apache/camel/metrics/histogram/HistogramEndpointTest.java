package org.apache.camel.metrics.histogram;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import org.apache.camel.Producer;
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
public class HistogramEndpointTest {

    private static final String METRICS_NAME = "metrics.name";
    private static final Long VALUE = System.currentTimeMillis();

    @Mock
    private MetricRegistry registry;

    private HistogramEndpoint endpoint;

    private InOrder inOrder;

    @Before
    public void setUp() throws Exception {
        endpoint = new HistogramEndpoint(registry, METRICS_NAME);
        inOrder = Mockito.inOrder(registry);
    }

    @After
    public void tearDown() throws Exception {
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testHistogramEndpoint() throws Exception {
        assertThat(endpoint, is(notNullValue()));
        assertThat(endpoint.getRegistry(), is(registry));
        assertThat(endpoint.getMetricsName(), is(METRICS_NAME));
    }

    @Test
    public void testCreateProducer() throws Exception {
        Producer producer = endpoint.createProducer();
        assertThat(producer, is(notNullValue()));
        assertThat(producer, is(HistogramProducer.class));
    }

    @Test
    public void testGetValue() throws Exception {
        assertThat(endpoint.getValue(), is(nullValue()));
    }

    @Test
    public void testSetValue() throws Exception {
        assertThat(endpoint.getValue(), is(nullValue()));
        endpoint.setValue(VALUE);
        assertThat(endpoint.getValue(), is(VALUE));
    }

    @Test
    public void testCreateEndpointUri() throws Exception {
        assertThat(endpoint.createEndpointUri(), is(HistogramEndpoint.ENDPOINT_URI));
    }
}
