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

import java.time.Duration;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;

/**
 * Example filter for adding common distribution statistics for all Timers and Distribution
 * Summaries.
 * Configure and add this to the {@link io.micrometer.core.instrument.MeterRegistry}
 * if desired.
 */
public class DistributionStatisticConfigFilter implements MeterFilter {

    private Long maximumExpectedValue;
    private Long minimumExpectedValue;
    private Boolean enabled;
    private Integer bufferLength;
    private Duration expiry;
    private double[] percentiles = new double[] {0.5D, 0.75D, 0.9D, 0.99D, 0.999D };
    private long[] slas;

    @Override
    public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
        if (id.getTag(MicrometerConstants.CAMEL_CONTEXT_TAG) != null) {
            return DistributionStatisticConfig.builder()
                    .percentilesHistogram(enabled)
                    .percentiles(percentiles)
                    .maximumExpectedValue(maximumExpectedValue)
                    .minimumExpectedValue(minimumExpectedValue)
                    .sla(slas)
                    .bufferLength(bufferLength)
                    .expiry(expiry)
                    .build()
                    .merge(config);
        }
        return config;
    }

    public void setMaximumExpectedValue(Long maximumExpectedValue) {
        this.maximumExpectedValue = maximumExpectedValue;
    }

    public void setMinimumExpectedValue(Long minimumExpectedValue) {
        this.minimumExpectedValue = minimumExpectedValue;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public void setBufferLength(Integer bufferLength) {
        this.bufferLength = bufferLength;
    }

    public void setExpiry(Duration expiry) {
        this.expiry = expiry;
    }

    public void setPercentiles(double[] percentiles) {
        this.percentiles = percentiles;
    }

    public void setSlas(long[] slas) {
        this.slas = slas;
    }
}
