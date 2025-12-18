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
package org.apache.camel.opentelemetry.metrics;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.PointData;
import org.apache.camel.test.junit5.CamelTestSupport;

import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.ROUTE_ID_ATTRIBUTE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AbstractOpenTelemetryTestSupport extends CamelTestSupport {

    public final CamelOpenTelemetryExtension otelExtension = CamelOpenTelemetryExtension.create();

    protected List<MetricData> getMetricData(String metricName) {
        return otelExtension.getMetrics().stream()
                .filter(d -> d.getName().equals(metricName))
                .collect(Collectors.toList());
    }

    protected PointData getPointDataForRouteId(String metricName, String routeId) {
        List<PointData> pointDataList = getAllPointDataForRouteId(metricName, routeId);
        assertEquals(1, pointDataList.size(),
                "Should have one metric for routeId " + routeId + " and metricName " + metricName);
        return pointDataList.get(0);
    }

    protected LongPointData getSingleLongPointData(String metricName, String routeId) {
        List<PointData> pdList = getAllPointDataForRouteId(metricName, routeId);
        assertEquals(1, pdList.size(), "Should have one metric for routeId " + routeId + " and metricName " + metricName);
        PointData pd = pdList.get(0);
        assertInstanceOf(LongPointData.class, pd);
        return (LongPointData) pd;
    }

    protected List<PointData> getAllPointDataForRouteId(String metricName, String routeId) {
        return otelExtension.getMetrics().stream()
                .filter(d -> d.getName().equals(metricName))
                .map(metricData -> metricData.getData().getPoints())
                .flatMap(Collection::stream)
                .filter(point -> routeId.equals(getRouteId(point)))
                .collect(Collectors.toList());
    }

    protected List<PointData> getAllPointData(String metricName) {
        return otelExtension.getMetrics().stream()
                .filter(d -> d.getName().equals(metricName))
                .map(metricData -> metricData.getData().getPoints())
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    protected String getRouteId(PointData pd) {
        Map<AttributeKey<?>, Object> m = pd.getAttributes().asMap();
        assertTrue(m.containsKey(AttributeKey.stringKey(ROUTE_ID_ATTRIBUTE)));
        return (String) m.get(AttributeKey.stringKey(ROUTE_ID_ATTRIBUTE));
    }
}
