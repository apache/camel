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

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.Registry;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricFilter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.Tag;

import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.METRIC_REGISTRY_NAME;

public final class MicroProfileMetricsHelper {

    private static final MetricType DEFAULT_METRIC_TYPE = MetricType.COUNTER;

    private MicroProfileMetricsHelper() {
    }

    public static String getMetricsName(String remaining) {
        String name = StringHelper.after(remaining, ":");
        return name == null ? remaining : name;
    }

    public static MetricType getMetricsType(String remaining) {
        String type = StringHelper.before(remaining, ":");
        return type == null ? DEFAULT_METRIC_TYPE : MetricType.from(type.toLowerCase(Locale.US));
    }

    public static List<Tag> getMetricsTag(String rawTags) {
        if (rawTags != null && !rawTags.isEmpty()) {
            String[] tagStrings = rawTags.split("\\s*,\\s*");
            return Stream.of(tagStrings)
                .map(tag -> parseTag(tag))
                .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    public static <T extends Metric> T findMetric(MetricRegistry metricRegistry, String metricName, Class<T> metricType) {
        return findMetric(metricRegistry, metricName, metricType, Collections.emptyList());
    }

    public static <T extends Metric> T findMetric(MetricRegistry metricRegistry, String metricName, Class<T> metricType, List<Tag> tags) {
        Map<MetricID, Metric> metrics = metricRegistry.getMetrics();
        for (Map.Entry<MetricID, Metric> entry : metrics.entrySet()) {
            if (metricTypeMatches(entry.getValue(), metricType)) {
                MetricID metricID = entry.getKey();
                if (metricID.getName().equals(metricName)) {
                    if (tags.isEmpty() || metricID.getTagsAsList().equals(tags)) {
                        return metricType.cast(entry.getValue());
                    }
                }
            }
        }
        return null;
    }

    public static Tag parseTag(String tagString) {
        if (ObjectHelper.isEmpty(tagString) || !tagString.contains("=")) {
            throw new IllegalArgumentException("Tag must be in the format: key=value");
        }
        String[] tagElements = tagString.split("=");
        if (tagElements.length != 2) {
            throw new IllegalArgumentException("Tag must be in the format: key=value");
        }
        return new Tag(tagElements[0], tagElements[1]);
    }

    public static Tag[] parseTagArray(String[] tagStrings) {
        Tag[] tags = new Tag[tagStrings.length];
        int i = 0;
        for (String tagString : tagStrings) {
            tags[i] = parseTag(tagString);
            i++;
        }
        return tags;
    }

    public static MetricRegistry getMetricRegistry(CamelContext camelContext) {
        Registry camelRegistry = camelContext.getRegistry();
        MetricRegistry metricRegistry = camelRegistry.lookupByNameAndType(METRIC_REGISTRY_NAME, MetricRegistry.class);

        if (metricRegistry == null) {
            throw new IllegalStateException("No usable MetricRegistry has been configured");
        }

        return metricRegistry;
    }

    public static synchronized void removeMetricsFromRegistry(MetricRegistry metricRegistry, MetricFilter filter) {
        if (metricRegistry != null) {
            metricRegistry.removeMatching(filter);
        }
    }

    private static boolean metricTypeMatches(Metric metric, Class<? extends Metric> metricType) {
        return metricType.isInstance(metric);
    }
}
