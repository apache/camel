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
package org.apache.camel.component.micrometer;

import java.time.Duration;
import java.util.function.Predicate;
import java.util.stream.LongStream;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;

import static org.apache.camel.component.micrometer.MicrometerConstants.ALWAYS;
import static org.apache.camel.component.micrometer.MicrometerConstants.CAMEL_METERS;

/**
 * Filter for adding distribution statistics to Timers and Distribution Summaries. Configure and add this to the
 * {@link io.micrometer.core.instrument.MeterRegistry} if desired:
 *
 * <pre>
 *     DistributionStatisticConfigFilter filter = new DistributionStatisticConfigFilter()
 *     // filter.set...
 *     meterRegistry.config().meterFilter(filter)
 * </pre>
 */
public class DistributionStatisticConfigFilter implements MeterFilter {

    private Predicate<Meter.Id> appliesTo = ALWAYS;
    private Long maximumExpectedValue;
    private Long minimumExpectedValue;
    private Boolean publishPercentileHistogram = true;
    private Integer percentilePrecision;
    private Integer bufferLength;
    private Duration expiry;
    private double[] percentiles;
    private long[] slas;

    @Override
    public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
        if (CAMEL_METERS.and(appliesTo).test(id)) {
            return DistributionStatisticConfig.builder()
                    .percentilesHistogram(publishPercentileHistogram)
                    .percentiles(percentiles)
                    .percentilePrecision(percentilePrecision)
                    .maximumExpectedValue((double) maximumExpectedValue)
                    .minimumExpectedValue((double) minimumExpectedValue)
                    .serviceLevelObjectives(LongStream.of(slas).asDoubleStream().toArray())
                    .bufferLength(bufferLength)
                    .expiry(expiry)
                    .build()
                    .merge(config);
        }
        return config;
    }

    /**
     * Restrict a condition under which this config applies to a Camel meter
     *
     * @param appliesTo predicate that must return true so that this config applies
     */
    public DistributionStatisticConfigFilter andAppliesTo(Predicate<Meter.Id> appliesTo) {
        this.appliesTo = this.appliesTo.and(appliesTo);
        return this;
    }

    /**
     * Add a condition under which this config applies to a Camel meter
     *
     * @param appliesTo predicate that must return true so that this config applies
     */
    public DistributionStatisticConfigFilter orAppliesTo(Predicate<Meter.Id> appliesTo) {
        this.appliesTo = this.appliesTo.or(appliesTo);
        return this;
    }

    /**
     * Sets the maximum expected value for a distribution summary value. Controls the number of buckets shipped by
     * publishPercentileHistogram as well as controlling the accuracy and memory footprint of the underlying
     * HdrHistogram structure.
     *
     * @param maximumExpectedValue the maximum expected value for a distribution summary value
     */
    public DistributionStatisticConfigFilter setMaximumExpectedValue(Long maximumExpectedValue) {
        this.maximumExpectedValue = maximumExpectedValue;
        return this;
    }

    /**
     * Sets the minimum expected value for a distribution summary value. Controls the number of buckets shipped by
     * publishPercentileHistogram as well as controlling the accuracy and memory footprint of the underlying
     * HdrHistogram structure.
     *
     * @param minimumExpectedValue the minimum expected value for a distribution summary value
     */
    public DistributionStatisticConfigFilter setMinimumExpectedValue(Long minimumExpectedValue) {
        this.minimumExpectedValue = minimumExpectedValue;
        return this;
    }

    /**
     * Sets the maximum expected duration for a timer value Controls the number of buckets shipped by
     * publishPercentileHistogram as well as controlling the accuracy and memory footprint of the underlying
     * HdrHistogram structure.
     *
     * @param maximumExpectedDuration the maximum expected duration for a timer value
     */
    public DistributionStatisticConfigFilter setMaximumExpectedDuration(Duration maximumExpectedDuration) {
        this.maximumExpectedValue = maximumExpectedDuration.toNanos();
        return this;
    }

    /**
     * Sets the minimum expected duration for a timer value Controls the number of buckets shipped by
     * publishPercentileHistogram as well as controlling the accuracy and memory footprint of the underlying
     * HdrHistogram structure.
     *
     * @param minimumExpectedDuration the minimum expected duration for a timer value
     */
    public DistributionStatisticConfigFilter setMinimumExpectedDuration(Duration minimumExpectedDuration) {
        this.minimumExpectedValue = minimumExpectedDuration.toNanos();
        return this;
    }

    /**
     * Whether to publish aggregatable percentile approximations for Prometheus or Atlas. Has no effect on systems that
     * do not support aggregatable percentile approximations. This defaults to true.
     *
     * @param publishPercentileHistogram Whether to publish aggregatable percentile approximations.
     */
    public DistributionStatisticConfigFilter setPublishPercentileHistogram(Boolean publishPercentileHistogram) {
        this.publishPercentileHistogram = publishPercentileHistogram;
        return this;
    }

    public DistributionStatisticConfigFilter setBufferLength(Integer bufferLength) {
        this.bufferLength = bufferLength;
        return this;
    }

    public DistributionStatisticConfigFilter setExpiry(Duration expiry) {
        this.expiry = expiry;
        return this;
    }

    /**
     * Calculate and publish percentile values. These values are non-aggregatable across dimensions.
     *
     * @param percentiles array of percentiles to be published
     */
    public DistributionStatisticConfigFilter setPercentiles(double[] percentiles) {
        this.percentiles = percentiles;
        return this;
    }

    public DistributionStatisticConfigFilter setPercentilePrecision(Integer percentilePrecision) {
        this.percentilePrecision = percentilePrecision;
        return this;
    }

    /**
     * Publish a cumulative histogram with buckets defined by your SLAs. Used together with publishPercentileHistogram
     * on a monitoring system that supports aggregatable percentiles, this setting adds additional buckets to the
     * published histogram. Used on a system that does not support aggregatable percentiles, this setting causes a
     * histogram to be published with only these buckets.
     *
     * @param slas array of percentiles to be published
     */
    public DistributionStatisticConfigFilter setSlas(long[] slas) {
        this.slas = slas;
        return this;
    }
}
