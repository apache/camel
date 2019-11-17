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
package org.apache.camel.component.microprofile.metrics.event.notifier.context;

import org.apache.camel.CamelContext;
import org.eclipse.microprofile.metrics.Tag;

import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.CAMEL_CONTEXT_STATUS_METRIC_NAME;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.CAMEL_CONTEXT_TAG;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.CAMEL_CONTEXT_UPTIME_METRIC_NAME;

public interface MicroProfileMetricsCamelContextEventNotifierNamingStrategy {

    MicroProfileMetricsCamelContextEventNotifierNamingStrategy DEFAULT = new MicroProfileMetricsCamelContextEventNotifierNamingStrategy() {
        @Override
        public String getCamelContextUptimeName() {
            return CAMEL_CONTEXT_UPTIME_METRIC_NAME;
        }

        @Override
        public String getCamelContextStatusName() {
            return CAMEL_CONTEXT_STATUS_METRIC_NAME;
        }
    };

    String getCamelContextUptimeName();
    String getCamelContextStatusName();

    default Tag[] getTags(CamelContext camelContext) {
        return new Tag[] {
            new Tag(CAMEL_CONTEXT_TAG, camelContext.getName()),
        };
    }
}
