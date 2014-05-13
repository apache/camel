package org.apache.camel.metrics;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Endpoint;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.metrics.counter.CounterEndpoint;
import org.apache.camel.metrics.histogram.HistogramEndpoint;
import org.apache.camel.metrics.meter.MeterEndpoint;
import org.apache.camel.spi.Registry;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Slf4jReporter;

/**
 * Represents the component that manages {@link MetricsEndpoint}.
 */
public class MetricsComponent extends DefaultComponent {

    public static final String METRIC_REGISTRY_NAME = "metricRegistry";
    public static final MetricsType DEFAULT_METRICS_TYPE = MetricsType.METER;
    public static final long DEFAULT_REPORTING_INTERVAL_SECONDS = 60L;

    private static final Logger LOG = LoggerFactory.getLogger(MetricsComponent.class);

    private MetricRegistry metricRegistry;

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        if (metricRegistry == null) {
            Registry camelRegistry = getCamelContext().getRegistry();
            metricRegistry = getOrCreateMetricRegistry(camelRegistry, METRIC_REGISTRY_NAME);
        }
        String metricsName = getMetricsName(remaining);
        MetricsType metricsType = getMetricsType(remaining);
        LOG.info("Metrics type: {}; name: {}", metricsType, metricsName);
        Endpoint endpoint = createNewEndpoint(metricRegistry, metricsType, metricsName);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    String getMetricsName(String remaining) {
        String name = ObjectHelper.after(remaining, ":");
        return name == null ? remaining : name;
    }

    Endpoint createNewEndpoint(MetricRegistry registry, MetricsType type, String metricsName) {
        Endpoint endpoint;
        switch (type) {
            case COUNTER:
                endpoint = new CounterEndpoint(registry, metricsName);
                break;
            case METER:
                endpoint = new MeterEndpoint(registry, metricsName);
                break;
            case HISTOGRAM:
                endpoint = new HistogramEndpoint(registry, metricsName);
                break;
            default:
                throw new RuntimeCamelException("Metrics type \"" + type.toString() + "\" not supported");
        }
        return endpoint;
    }

    MetricsType getMetricsType(String remaining) {
        String name = ObjectHelper.before(remaining, ":");
        MetricsType type;
        if (name == null) {
            type = DEFAULT_METRICS_TYPE;
        }
        else {
            type = MetricsType.getByName(name);
        }
        if (type == null) {
            throw new RuntimeCamelException("Unknow metrics type \"" + name + "\"");
        }
        return type;
    }

    MetricRegistry getOrCreateMetricRegistry(Registry camelRegistry, String registryName) {
        LOG.debug("Looking up MetricRegistry from Camel Registry for name \"{}\"", registryName);
        MetricRegistry result = getMetricRegistryFromCamelRegistry(camelRegistry, registryName);
        if (result == null) {
            LOG.debug("MetricRegistry not found from Camel Registry for name \"{}\"", registryName);
            LOG.info("Creating new default MetricRegistry");
            result = createMetricRegistry();
        }
        return result;
    }

    MetricRegistry getMetricRegistryFromCamelRegistry(Registry camelRegistry, String registryName) {
        return camelRegistry.lookupByNameAndType(registryName, MetricRegistry.class);
    }

    MetricRegistry createMetricRegistry() {
        MetricRegistry registry = new MetricRegistry();
        final Slf4jReporter reporter = Slf4jReporter.forRegistry(registry)
                .outputTo(LOG)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build();
        reporter.start(DEFAULT_REPORTING_INTERVAL_SECONDS, TimeUnit.SECONDS);
        return registry;
    }
}
