/*
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
package org.apache.camel.component.ganglia;

import java.util.HashMap;
import java.util.Map;

import info.ganglia.gmetric4j.Publisher;
import info.ganglia.gmetric4j.gmetric.GMetricSlope;
import info.ganglia.gmetric4j.gmetric.GMetricType;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class GangliaProducerTest {

    private static final String BODY = "2.0";
    private static final String PREFIX = "prefix";

    private static final String GROUP_NAME = "groupName";
    private static final String METRIC_NAME = "wheelbase";
    private static final GMetricType TYPE = GMetricType.DOUBLE;
    private static final GMetricSlope SLOPE = GMetricSlope.POSITIVE;
    private static final String UNITS = "meter";
    private static final int T_MAX = 1;
    private static final int D_MAX = 2;

    private static final String CONF_GROUP_NAME = "confGroupName";
    private static final String CONF_METRIC_NAME = "confWheelbase";
    private static final GMetricType CONF_TYPE = GMetricType.INT8;
    private static final GMetricSlope CONF_SLOPE = GMetricSlope.NEGATIVE;
    private static final String CONF_UNITS = "kelvin";
    private static final int CONF_T_MAX = 3;
    private static final int CONF_D_MAX = 4;

    @Mock
    private Publisher mockPublisher;
    @Mock
    private GangliaEndpoint mockEndpoint;
    @Mock
    private Exchange mockExchange;
    @Mock(lenient = true)
    private Message mockMessage;
    @Mock
    private GangliaConfiguration mockConf;
    private Map<String, Object> mockHeaders;

    private GangliaProducer gangliaProducer;

    @BeforeEach
    public void setup() {
        when(mockEndpoint.getConfiguration()).thenReturn(mockConf);

        when(mockConf.getGroupName()).thenReturn(CONF_GROUP_NAME);
        when(mockConf.getMetricName()).thenReturn(CONF_METRIC_NAME);
        when(mockConf.getType()).thenReturn(CONF_TYPE);
        when(mockConf.getSlope()).thenReturn(CONF_SLOPE);
        when(mockConf.getUnits()).thenReturn(CONF_UNITS);
        when(mockConf.getTmax()).thenReturn(CONF_T_MAX);
        when(mockConf.getDmax()).thenReturn(CONF_D_MAX);

        when(mockExchange.getIn()).thenReturn(mockMessage);

        mockHeaders = new HashMap<>();
        mockHeaders.put(GangliaConstants.GROUP_NAME, GROUP_NAME);
        mockHeaders.put(GangliaConstants.METRIC_NAME, METRIC_NAME);
        mockHeaders.put(GangliaConstants.METRIC_TYPE, TYPE);
        mockHeaders.put(GangliaConstants.METRIC_SLOPE, SLOPE);
        mockHeaders.put(GangliaConstants.METRIC_UNITS, UNITS);
        mockHeaders.put(GangliaConstants.METRIC_TMAX, T_MAX);
        mockHeaders.put(GangliaConstants.METRIC_DMAX, D_MAX);

        when(mockMessage.getBody(String.class)).thenReturn(BODY);
        when(mockMessage.getHeaders()).thenReturn(mockHeaders);

        when(mockMessage.getHeader(GangliaConstants.GROUP_NAME, String.class)).thenReturn(GROUP_NAME);
        when(mockMessage.getHeader(GangliaConstants.METRIC_NAME, String.class)).thenReturn(METRIC_NAME);
        when(mockMessage.getHeader(GangliaConstants.METRIC_TYPE, GMetricType.class)).thenReturn(TYPE);
        when(mockMessage.getHeader(GangliaConstants.METRIC_SLOPE, GMetricSlope.class)).thenReturn(SLOPE);
        when(mockMessage.getHeader(GangliaConstants.METRIC_UNITS, String.class)).thenReturn(UNITS);
        when(mockMessage.getHeader(GangliaConstants.METRIC_TMAX, Integer.class)).thenReturn(T_MAX);
        when(mockMessage.getHeader(GangliaConstants.METRIC_DMAX, Integer.class)).thenReturn(D_MAX);

        gangliaProducer = new GangliaProducer(mockEndpoint, mockPublisher);
    }

    @Test
    public void processMessageHeadersShouldSucceed() throws Exception {
        gangliaProducer.process(mockExchange);
        verify(mockPublisher).publish(GROUP_NAME, METRIC_NAME, BODY, TYPE, SLOPE, T_MAX, D_MAX, UNITS);
    }

    @Test
    public void processEmptyDoubleShouldPublishNan() throws Exception {
        when(mockMessage.getBody(String.class)).thenReturn("");
        gangliaProducer.process(mockExchange);
        verify(mockPublisher).publish(GROUP_NAME, METRIC_NAME, "NaN", TYPE, SLOPE, T_MAX, D_MAX, UNITS);
    }

    @Test
    public void processWithPrefixShouldPublishPrefix() throws Exception {
        when(mockConf.getPrefix()).thenReturn(PREFIX);
        gangliaProducer.process(mockExchange);
        verify(mockPublisher).publish(GROUP_NAME, PREFIX + "_" + METRIC_NAME, BODY, TYPE, SLOPE, T_MAX, D_MAX, UNITS);
    }

    @Test
    public void processMessageWithoutGroupNameShouldPublishEndpointLevelConfiguration() throws Exception {
        mockHeaders.remove(GangliaConstants.GROUP_NAME);
        gangliaProducer.process(mockExchange);
        verify(mockPublisher).publish(CONF_GROUP_NAME, METRIC_NAME, BODY, TYPE, SLOPE, T_MAX, D_MAX, UNITS);
    }

    @Test
    public void processMessageWithoutMetricNameShouldPublishEndpointLevelConfiguration() throws Exception {
        mockHeaders.remove(GangliaConstants.METRIC_NAME);
        gangliaProducer.process(mockExchange);
        verify(mockPublisher).publish(GROUP_NAME, CONF_METRIC_NAME, BODY, TYPE, SLOPE, T_MAX, D_MAX, UNITS);
    }

    @Test
    public void processMessageWithoutMetricTypeShouldPublishEndpointLevelConfiguration() throws Exception {
        mockHeaders.remove(GangliaConstants.METRIC_TYPE);
        gangliaProducer.process(mockExchange);
        verify(mockPublisher).publish(GROUP_NAME, METRIC_NAME, BODY, CONF_TYPE, SLOPE, T_MAX, D_MAX, UNITS);
    }

    @Test
    public void processMessageWithoutMetricSlopeShouldPublishEndpointLevelConfiguration() throws Exception {
        mockHeaders.remove(GangliaConstants.METRIC_SLOPE);
        gangliaProducer.process(mockExchange);
        verify(mockPublisher).publish(GROUP_NAME, METRIC_NAME, BODY, TYPE, CONF_SLOPE, T_MAX, D_MAX, UNITS);
    }

    @Test
    public void processMessageWithoutMetricUnitsShouldPublishEndpointLevelConfiguration() throws Exception {
        mockHeaders.remove(GangliaConstants.METRIC_UNITS);
        gangliaProducer.process(mockExchange);
        verify(mockPublisher).publish(GROUP_NAME, METRIC_NAME, BODY, TYPE, SLOPE, T_MAX, D_MAX, CONF_UNITS);
    }

    @Test
    public void processMessageWithoutMetricTMaxShouldPublishEndpointLevelConfiguration() throws Exception {
        mockHeaders.remove(GangliaConstants.METRIC_TMAX);
        gangliaProducer.process(mockExchange);
        verify(mockPublisher).publish(GROUP_NAME, METRIC_NAME, BODY, TYPE, SLOPE, CONF_T_MAX, D_MAX, UNITS);
    }

    @Test
    public void processMessageWithoutMetricDMaxShouldPublishEndpointLevelConfiguration() throws Exception {
        mockHeaders.remove(GangliaConstants.METRIC_DMAX);
        gangliaProducer.process(mockExchange);
        verify(mockPublisher).publish(GROUP_NAME, METRIC_NAME, BODY, TYPE, SLOPE, T_MAX, CONF_D_MAX, UNITS);
    }

}
