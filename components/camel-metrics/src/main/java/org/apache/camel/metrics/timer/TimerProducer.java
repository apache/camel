package org.apache.camel.metrics.timer;

import static org.apache.camel.metrics.MetricsComponent.HEADER_TIMER_ACTION;
import static org.apache.camel.metrics.timer.TimerEndpoint.ENDPOINT_URI;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.metrics.AbstractMetricsProducer;
import org.apache.camel.metrics.timer.TimerEndpoint.TimerAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;

public class TimerProducer extends AbstractMetricsProducer<TimerEndpoint> {

    private static final Logger LOG = LoggerFactory.getLogger(TimerProducer.class);

    public TimerProducer(TimerEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    protected void doProcess(Exchange exchange, TimerEndpoint endpoint, MetricRegistry registry, String metricsName) throws Exception {
        Message in = exchange.getIn();
        TimerAction action = endpoint.getAction();
        TimerAction finalAction = in.getHeader(HEADER_TIMER_ACTION, action, TimerAction.class);
        if (finalAction == TimerAction.start) {
            handleStart(exchange, registry, metricsName);
        }
        else if (finalAction == TimerAction.stop) {
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
        return new StringBuilder(ENDPOINT_URI)
                .append(":")
                .append(metricsName)
                .toString();
    }

    Timer.Context getTimerContextFromExchange(Exchange exchange, String propertyName) {
        return exchange.getProperty(propertyName, Timer.Context.class);
    }
}
