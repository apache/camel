/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.micrometer;

import java.util.function.Function;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultMessage;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import static org.apache.camel.component.micrometer.AbstractMicrometerProducer.HEADER_PATTERN;
import static org.apache.camel.component.micrometer.MicrometerConstants.HEADER_HISTOGRAM_VALUE;
import static org.apache.camel.component.micrometer.MicrometerConstants.HEADER_METRIC_NAME;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AbstractMicrometerProducerTest {

    public static final String METRIC_NAME = "a metric";

    @Mock
    private MicrometerEndpoint endpoint;

    @Mock
    private Exchange exchange;

    @Mock
    private Message in;

    @Mock
    private MeterRegistry registry;

    private AbstractMicrometerProducer<Meter> okProducer;

    private AbstractMicrometerProducer<Meter> failProducer;

    private InOrder inOrder;

    @Before
    public void setUp() {
        okProducer = new AbstractMicrometerProducer<Meter>(endpoint) {

            @Override
            protected Function<MeterRegistry, Meter> registrar(String name, Iterable<Tag> list) {
                return null;
            }

            @Override
            protected void doProcess(Exchange exchange, MicrometerEndpoint endpoint, Meter meter) {
            }
        };
        failProducer = new AbstractMicrometerProducer<Meter>(endpoint) {

            @Override
            protected Function<MeterRegistry, Meter> registrar(String name, Iterable<Tag> list) {
                return null;
            }

            @Override
            protected void doProcess(Exchange exchange, MicrometerEndpoint endpoint, Meter meter) {
                throw new RuntimeException("Muchos problemos");
            }
        };
        inOrder = Mockito.inOrder(endpoint, exchange, in, registry);
        when(exchange.getIn()).thenReturn(in);
        when(endpoint.getMetricsName()).thenReturn(METRIC_NAME);
        when(endpoint.getRegistry()).thenReturn(registry);
    }

    @Test
    public void testDoProcess() throws Exception {
        when(in.getHeader(HEADER_METRIC_NAME, String.class)).thenReturn(null);
        when(in.removeHeaders(HEADER_PATTERN)).thenReturn(true);
        okProducer.process(exchange);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(endpoint, times(1)).getMetricsName();
        inOrder.verify(in, times(1)).getHeader(HEADER_METRIC_NAME, String.class);
        inOrder.verify(endpoint, times(1)).getRegistry();
        inOrder.verify(in, times(1)).removeHeaders(HEADER_PATTERN);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testDoProcessWithException() throws Exception {
        when(in.getHeader(HEADER_METRIC_NAME, String.class)).thenReturn(null);
        when(in.removeHeaders(HEADER_PATTERN)).thenReturn(true);
        failProducer.process(exchange);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(endpoint, times(1)).getMetricsName();
        inOrder.verify(in, times(1)).getHeader(HEADER_METRIC_NAME, String.class);
        inOrder.verify(endpoint, times(1)).getRegistry();
        inOrder.verify(in, times(1)).removeHeaders(HEADER_PATTERN);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testGetMetricsName() {
        when(in.getHeader(HEADER_METRIC_NAME, String.class)).thenReturn("A");
        assertThat(okProducer.getMetricsName(in, "value"), is("A"));
        inOrder.verify(in, times(1)).getHeader(HEADER_METRIC_NAME, String.class);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testGetMetricsNameNotSet() {
        when(in.getHeader(HEADER_METRIC_NAME, String.class)).thenReturn(null);
        assertThat(okProducer.getMetricsName(in, "name"), is("name"));
        inOrder.verify(in, times(1)).getHeader(HEADER_METRIC_NAME, String.class);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testGetStringHeader() {
        when(in.getHeader(HEADER_METRIC_NAME, String.class)).thenReturn("A");
        assertThat(okProducer.getStringHeader(in, HEADER_METRIC_NAME, "value"), is("A"));
        inOrder.verify(in, times(1)).getHeader(HEADER_METRIC_NAME, String.class);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testGetStringHeaderWithNullValue() {
        when(in.getHeader(HEADER_METRIC_NAME, String.class)).thenReturn(null);
        assertThat(okProducer.getStringHeader(in, HEADER_METRIC_NAME, "value"), is("value"));
        inOrder.verify(in, times(1)).getHeader(HEADER_METRIC_NAME, String.class);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testGetStringHeaderWithWhiteSpaces() {
        when(in.getHeader(HEADER_METRIC_NAME, String.class)).thenReturn(" ");
        assertThat(okProducer.getStringHeader(in, HEADER_METRIC_NAME, "value"), is("value"));
        inOrder.verify(in, times(1)).getHeader(HEADER_METRIC_NAME, String.class);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testGetStringHeaderWithEmptyString() {
        when(in.getHeader(HEADER_METRIC_NAME, String.class)).thenReturn("");
        assertThat(okProducer.getStringHeader(in, HEADER_METRIC_NAME, "value"), is("value"));
        inOrder.verify(in, times(1)).getHeader(HEADER_METRIC_NAME, String.class);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testGetLongHeader() {
        when(in.getHeader(HEADER_HISTOGRAM_VALUE, 19L, Long.class)).thenReturn(201L);
        assertThat(okProducer.getLongHeader(in, HEADER_HISTOGRAM_VALUE, 19L), is(201L));
        inOrder.verify(in, times(1)).getHeader(HEADER_HISTOGRAM_VALUE, 19L, Long.class);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testClearMetricsHeaders() {
        when(in.removeHeaders(HEADER_PATTERN)).thenReturn(true);
        assertThat(okProducer.clearMetricsHeaders(in), is(true));
        inOrder.verify(in, times(1)).removeHeaders(HEADER_PATTERN);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testClearRealHeaders() {
        Message msg = new DefaultMessage(new DefaultCamelContext());
        Object val = new Object();
        msg.setHeader(HEADER_HISTOGRAM_VALUE, 109L);
        msg.setHeader(HEADER_METRIC_NAME, "the metric");
        msg.setHeader("notRemoved", val);
        assertThat(msg.getHeaders().size(), is(3));
        assertThat(msg.getHeader(HEADER_HISTOGRAM_VALUE, Long.class), is(109L));
        assertThat(msg.getHeader(HEADER_METRIC_NAME, String.class), is("the metric"));
        assertThat(msg.getHeader("notRemoved"), is(val));
        okProducer.clearMetricsHeaders(msg);
        assertThat(msg.getHeaders().size(), is(1));
        assertThat(msg.getHeader("notRemoved"), is(val));
    }
}
