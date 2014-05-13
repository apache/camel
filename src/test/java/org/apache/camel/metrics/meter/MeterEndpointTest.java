package org.apache.camel.metrics.meter;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
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
public class MeterEndpointTest {

    private static final String METRICS_NAME = "metrics.name";
    private static final Long VALUE = System.currentTimeMillis();

    @Mock
    private MetricRegistry registry;

    private MeterEndpoint endpoint;

    private InOrder inOrder;

    @Before
    public void setUp() throws Exception {
        endpoint = new MeterEndpoint(registry, METRICS_NAME);
        inOrder = Mockito.inOrder(registry);
    }

    @After
    public void tearDown() throws Exception {
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testMeterEndpoint() throws Exception {
        assertThat(endpoint, is(notNullValue()));
        assertThat(endpoint.getRegistry(), is(registry));
        assertThat(endpoint.getMetricsName(), is(METRICS_NAME));
    }

    @Test
    public void testCreateProducer() throws Exception {
        Producer producer = endpoint.createProducer();
        assertThat(producer, is(notNullValue()));
        assertThat(producer, is(instanceOf(MeterProducer.class)));
    }

    @Test
    public void testGetMark() throws Exception {
        assertThat(endpoint.getMark(), is(nullValue()));
    }

    @Test
    public void testSetMark() throws Exception {
        assertThat(endpoint.getMark(), is(nullValue()));
        endpoint.setMark(VALUE);
        assertThat(endpoint.getMark(), is(VALUE));
    }

    @Test
    public void testCreateEndpointUri() throws Exception {
        assertThat(endpoint.createEndpointUri(), is(MeterEndpoint.ENDPOINT_URI));
    }
}
