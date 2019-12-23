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
package org.apache.camel.component.microprofile.metrics;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.smallrye.metrics.MetricRegistries;
import io.smallrye.metrics.exporters.JsonExporter;
import org.apache.camel.BindToRegistry;
import org.apache.camel.component.microprofile.metrics.gauge.AtomicIntegerGauge;
import org.apache.camel.component.microprofile.metrics.gauge.SimpleGauge;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.eclipse.microprofile.metrics.ConcurrentGauge;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.Timer;

import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsHelper.findMetric;
import static org.eclipse.microprofile.metrics.MetricRegistry.Type;

public class MicroProfileMetricsTestSupport extends CamelTestSupport {

    @BindToRegistry(MicroProfileMetricsConstants.METRIC_REGISTRY_NAME)
    protected MetricRegistry metricRegistry;

    private MetricRegistries registries = new MetricRegistries();

    @Override
    public void setUp() throws Exception {
        metricRegistry = registries.getApplicationRegistry();
        super.setUp();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        registries.cleanUp();
    }

    protected Counter getCounter(String metricName) {
        return findMetric(metricRegistry, metricName, Counter.class);
    }

    protected Counter getCounter(String metricName, Tag[] tags) {
        return findMetric(metricRegistry, metricName, Counter.class, Arrays.asList(tags));
    }

    protected AtomicIntegerGauge getAtomicIntegerGauge(String metricName, Tag[] tags) {
        return findMetric(metricRegistry, metricName, AtomicIntegerGauge.class, Arrays.asList(tags));
    }

    protected ConcurrentGauge getConcurrentGauge(String metricName) {
        return findMetric(metricRegistry, metricName, ConcurrentGauge.class);
    }

    protected SimpleGauge getSimpleGauge(String metricName) {
        return findMetric(metricRegistry, metricName, SimpleGauge.class);
    }

    protected Histogram getHistogram(String metricName) {
        return findMetric(metricRegistry, metricName, Histogram.class);
    }

    protected Meter getMeter(String metricName) {
        return findMetric(metricRegistry, metricName, Meter.class);
    }

    protected Timer getTimer(String metricName) {
        return findMetric(metricRegistry, metricName, Timer.class);
    }

    protected Timer getTimer(String metricName, Tag[] tags) {
        return findMetric(metricRegistry, metricName, Timer.class, Arrays.asList(tags));
    }

    protected Metadata getMetricMetadata(String metricName) {
        Map<String, Metadata> metadataMap = metricRegistry.getMetadata();
        for (Map.Entry<String, Metadata> entry : metadataMap.entrySet()) {
            Metadata metadata = entry.getValue();
            if (metadata.getName().equals(metricName)) {
                return metadata;
            }
        }
        return null;
    }

    protected List<Tag> getMetricTags(String metricName) {
        Map<MetricID, Metric> metrics = metricRegistry.getMetrics();
        for (Map.Entry<MetricID, Metric> entry : metrics.entrySet()) {
            if (entry.getKey().getName().equals(metricName)) {
                return entry.getKey().getTagsAsList();
            }
        }
        return Collections.emptyList();
    }

    protected void dumpMetrics() {
        System.out.println(new JsonExporter().exportOneScope(Type.APPLICATION));
    }
}
