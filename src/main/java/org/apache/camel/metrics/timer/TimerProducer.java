package org.apache.camel.metrics.timer;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.metrics.timer.TimerEndpoint.TimerAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;

public class TimerProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(TimerProducer.class);

    public TimerProducer(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        TimerEndpoint endpoint = (TimerEndpoint) getEndpoint();
        MetricRegistry registry = endpoint.getRegistry();
        String metricsName = endpoint.getMetricsName(exchange);
        TimerAction action = endpoint.getAction();
        if (action == TimerAction.start) {
            handleStart(exchange, registry, metricsName);
        }
        else if (action == TimerAction.stop) {
            handleStop(exchange, registry, metricsName);
        }
        else {
            LOG.warn("No action provided for timer \"{}\"", metricsName);
        }
    }

    void handleStart(Exchange exchange, MetricRegistry registry, String metricsName) {
        String propertyName = getPropertyName(metricsName);
        Timer.Context context = getTimerContextFromExchange(exchange, propertyName);
        if (context == null) {
            Timer timer = registry.timer(metricsName);
            context = timer.time();
            exchange.setProperty(propertyName, context);
        }
        else {
            LOG.warn("Timer \"{}\" already running", metricsName);
        }
    }

    void handleStop(Exchange exchange, MetricRegistry registry, String metricsName) {
        String propertyName = getPropertyName(metricsName);
        Timer.Context context = getTimerContextFromExchange(exchange, propertyName);
        if (context != null) {
            context.stop();
            exchange.removeProperty(propertyName);
        }
        else {
            LOG.warn("Timer \"{}\" not found", metricsName);
        }
    }

    String getPropertyName(String metricsName) {
        return new StringBuilder(TimerEndpoint.ENDPOINT_URI)
                .append(":")
                .append(metricsName)
                .toString();
    }

    Timer.Context getTimerContextFromExchange(Exchange exchange, String propertyName) {
        return exchange.getProperty(propertyName, Timer.Context.class);
    }
}
