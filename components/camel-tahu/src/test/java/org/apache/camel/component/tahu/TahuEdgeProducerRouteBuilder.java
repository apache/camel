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

package org.apache.camel.component.tahu;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.eclipse.tahu.edge.sim.DataSimulator;
import org.eclipse.tahu.edge.sim.RandomDataSimulator;
import org.eclipse.tahu.message.model.DeviceDescriptor;
import org.eclipse.tahu.message.model.EdgeNodeDescriptor;
import org.eclipse.tahu.message.model.MetricDataType;
import org.eclipse.tahu.message.model.SparkplugBPayload;
import org.eclipse.tahu.message.model.SparkplugBPayloadMap;
import org.testcontainers.shaded.org.bouncycastle.util.Arrays;

public class TahuEdgeProducerRouteBuilder extends RouteBuilder {

    static final String NODE_DATA_TEST_ROUTE_ID = "node-data-test-route";
    static final String NODE_DATA_URI = "direct:node-data";

    static final String DEVICE_DATA_TEST_ROUTE_ID = "device-data-test-route";
    static final String DEVICE_DATA_URI = "direct:device-data";

    protected DataSimulator dataSimulator;

    protected EdgeNodeDescriptor edgeNodeDescriptor;
    protected DeviceDescriptor deviceDescriptor;

    protected TahuEdgeEndpoint tahuEdgeNodeEndpoint;
    protected TahuEdgeEndpoint tahuDeviceEndpoint;

    public TahuEdgeProducerRouteBuilder(CamelContext context) {
        super(context);
    }

    @Override
    public void configure() throws Exception {

        CamelContext context = getContext();

        tahuEdgeNodeEndpoint = context.getEndpoint(
                TahuConstants.EDGE_NODE_SCHEME + ":G2/E2?deviceIds=D2&primaryHostId=IamHost", TahuEdgeEndpoint.class);

        edgeNodeDescriptor =
                new EdgeNodeDescriptor(tahuEdgeNodeEndpoint.getGroupId(), tahuEdgeNodeEndpoint.getEdgeNode());

        tahuDeviceEndpoint = context.getEndpoint(TahuConstants.EDGE_NODE_SCHEME + ":G2/E2/D2", TahuEdgeEndpoint.class);

        deviceDescriptor = new DeviceDescriptor(edgeNodeDescriptor, tahuDeviceEndpoint.getDeviceId());

        dataSimulator = new RandomDataSimulator(10, Map.of(deviceDescriptor, 50));

        tahuEdgeNodeEndpoint.setMetricDataTypePayloadMap(dataSimulator.getNodeBirthPayload(edgeNodeDescriptor));

        SparkplugBPayloadMap deviceMetricPayloadMap = new SparkplugBPayloadMap.SparkplugBPayloadMapBuilder()
                .addMetrics(
                        dataSimulator.getDeviceBirthPayload(deviceDescriptor).getMetrics())
                .createPayload();
        tahuDeviceEndpoint.setMetricDataTypePayloadMap(deviceMetricPayloadMap);

        from(NODE_DATA_URI)
                .id(NODE_DATA_TEST_ROUTE_ID)
                .process(populateNodeDataPayload)
                .to(tahuEdgeNodeEndpoint);

        from(DEVICE_DATA_URI)
                .id(DEVICE_DATA_TEST_ROUTE_ID)
                .process(populateDeviceDataPayload)
                .to(tahuDeviceEndpoint);
    }

    private static final int[] COMPLEX_METRIC_DATA_TYPE_INTS = new int[] {
        MetricDataType.DataSet.toIntValue(), MetricDataType.Template.toIntValue(), MetricDataType.Unknown.toIntValue()
    };

    private void populateTestMessage(Exchange exch, SparkplugBPayload payload, EdgeNodeDescriptor edgeNodeDescriptor) {
        Message message = exch.getMessage();

        byte[] payloadBody = payload.getBody();

        message.setBody(payloadBody, byte[].class);

        payload.getMetrics().stream().forEach(m -> {
            // Default to the Metric's value
            Object headerValue = m.getValue();

            // If the Metric is a complex type, use the whole Metric instead
            if (Arrays.contains(COMPLEX_METRIC_DATA_TYPE_INTS, m.getDataType().toIntValue())) {
                headerValue = m;
            }

            message.setHeader(TahuConstants.METRIC_HEADER_PREFIX + m.getName(), headerValue);
        });
    }

    protected Processor populateNodeDataPayload = (exch) -> {
        populateTestMessage(exch, dataSimulator.getNodeDataPayload(edgeNodeDescriptor), edgeNodeDescriptor);
    };

    protected Processor populateDeviceDataPayload = (exch) -> {
        populateTestMessage(exch, dataSimulator.getDeviceDataPayload(deviceDescriptor), deviceDescriptor);
    };
}
