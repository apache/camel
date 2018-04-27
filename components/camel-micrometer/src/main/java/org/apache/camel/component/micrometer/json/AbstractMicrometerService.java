package org.apache.camel.component.micrometer.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.component.micrometer.MicrometerComponent;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ObjectHelper;

import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

/**
 * @author Christian Ohr
 */
public class AbstractMicrometerService extends ServiceSupport {

    private CamelContext camelContext;
    private MeterRegistry meterRegistry;
    private boolean prettyPrint = true;
    private boolean supportAggregablePercentiles;
    private TimeUnit durationUnit = TimeUnit.MILLISECONDS;
    private transient ObjectMapper mapper;
    private transient ObjectMapper secondsMapper;

    public MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }

    public void setMeterRegistry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    public void setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    public TimeUnit getDurationUnit() {
        return durationUnit;
    }

    public void setDurationUnit(TimeUnit durationUnit) {
        this.durationUnit = durationUnit;
    }

    public boolean isSupportAggregablePercentiles() {
        return supportAggregablePercentiles;
    }

    public void setSupportAggregablePercentiles(boolean supportAggregablePercentiles) {
        this.supportAggregablePercentiles = supportAggregablePercentiles;
    }

    public String dumpStatisticsAsJson() {
        ObjectWriter writer = mapper.writer();
        if (isPrettyPrint()) {
            writer = writer.withDefaultPrettyPrinter();
        }
        try {
            return writer.writeValueAsString(getMeterRegistry());
        } catch (JsonProcessingException e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
    }

    public String dumpStatisticsAsJsonTimeUnitSeconds() {
        ObjectWriter writer = secondsMapper.writer();
        if (isPrettyPrint()) {
            writer = writer.withDefaultPrettyPrinter();
        }
        try {
            return writer.writeValueAsString(getMeterRegistry());
        } catch (JsonProcessingException e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
    }

    @Override
    protected void doStart() throws Exception {
        if (meterRegistry == null) {
            Registry camelRegistry = getCamelContext().getRegistry();
            meterRegistry = camelRegistry.lookupByNameAndType(MicrometerComponent.METRICS_REGISTRY, MeterRegistry.class);
            // create a new metricsRegistry by default
            if (meterRegistry == null) {
                meterRegistry = new SimpleMeterRegistry();
            }
        }

        // json mapper
        this.mapper = new ObjectMapper().registerModule(new MicrometerModule(getDurationUnit(), supportAggregablePercentiles));
        this.secondsMapper = getDurationUnit() == TimeUnit.SECONDS ?
                this.mapper :
                new ObjectMapper().registerModule(new MicrometerModule(TimeUnit.SECONDS, supportAggregablePercentiles));
    }

    @Override
    protected void doStop() throws Exception {

    }
}
