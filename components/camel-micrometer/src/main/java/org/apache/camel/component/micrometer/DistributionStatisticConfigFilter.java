package org.apache.camel.component.micrometer;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;

import java.time.Duration;

/**
 * Filter for adding common distribution statistics for all Timers and Histograms.
 * Configure and add this to the {@link io.micrometer.core.instrument.MeterRegistry}
 * if desired.
 */
public class DistributionStatisticConfigFilter implements MeterFilter {

    private String prefix = MicrometerConstants.HEADER_PREFIX;
    private Long maximumExpectedValue;
    private Long minimumExpectedValue;
    private Boolean enabled;
    private Integer bufferLength;
    private Duration expiry;
    private double[] percentiles = new double[] { 0.5D, 0.75D, 0.9D, 0.99D, 0.999D };
    private long[] slas;

    @Override
    public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
        if (id.getName().startsWith(prefix)) {
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

    public void setPrefix(String prefix) {
        this.prefix = prefix;
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
