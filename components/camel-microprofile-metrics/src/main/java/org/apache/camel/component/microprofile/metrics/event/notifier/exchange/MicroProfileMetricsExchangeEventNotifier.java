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
package org.apache.camel.component.microprofile.metrics.event.notifier.exchange;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.component.microprofile.metrics.MicroProfileMetricsExchangeRecorder;
import org.apache.camel.component.microprofile.metrics.MicroProfileMetricsHelper;
import org.apache.camel.component.microprofile.metrics.event.notifier.AbstractMicroProfileMetricsEventNotifier;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelEvent.ExchangeCompletedEvent;
import org.apache.camel.spi.CamelEvent.ExchangeCreatedEvent;
import org.apache.camel.spi.CamelEvent.ExchangeEvent;
import org.apache.camel.spi.CamelEvent.ExchangeSentEvent;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.Timer;
import org.eclipse.microprofile.metrics.Timer.Context;

import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.CAMEL_CONTEXT_METRIC_NAME;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.CAMEL_CONTEXT_TAG;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.EVENT_TYPE_TAG;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.EXCHANGES_EXTERNAL_REDELIVERIES_METRIC_NAME;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.EXCHANGES_FAILURES_HANDLED_METRIC_NAME;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.EXCHANGES_METRIC_PREFIX;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.PROCESSING_METRICS_SUFFIX;

public class MicroProfileMetricsExchangeEventNotifier extends AbstractMicroProfileMetricsEventNotifier<ExchangeEvent> {

    private Predicate<Exchange> ignoreExchanges = exchange -> false;
    private MicroProfileMetricsExchangeEventNotifierNamingStrategy namingStrategy = MicroProfileMetricsExchangeEventNotifierNamingStrategy.DEFAULT;
    private MicroProfileMetricsExchangeRecorder exchangeRecorder;

    public MicroProfileMetricsExchangeEventNotifier() {
        super(ExchangeEvent.class);
    }

    public Predicate<Exchange> getIgnoreExchanges() {
        return ignoreExchanges;
    }

    public MicroProfileMetricsExchangeEventNotifierNamingStrategy getNamingStrategy() {
        return namingStrategy;
    }

    public void setNamingStrategy(MicroProfileMetricsExchangeEventNotifierNamingStrategy namingStrategy) {
        this.namingStrategy = namingStrategy;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        CamelContext camelContext = getCamelContext();
        MetricRegistry metricRegistry = getMetricRegistry();
        Tag tag = new Tag(CAMEL_CONTEXT_TAG, camelContext.getName());
        exchangeRecorder = new MicroProfileMetricsExchangeRecorder(metricRegistry, CAMEL_CONTEXT_METRIC_NAME, tag);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        MicroProfileMetricsHelper.removeMetricsFromRegistry(getMetricRegistry(), (metricID, metric) -> {
            String metricName = metricID.getName();
            Map<String, String> tags = metricID.getTags();
            if (tags.containsKey(CAMEL_CONTEXT_TAG) && tags.get(CAMEL_CONTEXT_TAG).equals(getCamelContext().getName())) {
                return tags.containsKey(EVENT_TYPE_TAG)
                       || metricName.contains(EXCHANGES_METRIC_PREFIX)
                       || metricName.endsWith(EXCHANGES_EXTERNAL_REDELIVERIES_METRIC_NAME)
                       || metricName.endsWith(EXCHANGES_FAILURES_HANDLED_METRIC_NAME);
            }
            return false;
        });
    }

    @Override
    public void notify(CamelEvent event) throws Exception {
        if (!(getIgnoreExchanges().test(((ExchangeEvent) event).getExchange()))) {
            if (event instanceof ExchangeSentEvent) {
                handleSentEvent((ExchangeSentEvent) event);
            } else if (event instanceof ExchangeCreatedEvent) {
                handleCreatedEvent((ExchangeCreatedEvent) event);
            } else if (event instanceof ExchangeCompletedEvent || event instanceof CamelEvent.ExchangeFailedEvent) {
                handleDoneEvent((ExchangeEvent) event);
            }
        }
    }

    protected void handleCreatedEvent(ExchangeCreatedEvent createdEvent) {
        String name = namingStrategy.getName(createdEvent.getExchange(), createdEvent.getExchange().getFromEndpoint());
        Tag[] tags = namingStrategy.getTags(createdEvent, createdEvent.getExchange().getFromEndpoint());
        Timer timer = getMetricRegistry().timer(name + PROCESSING_METRICS_SUFFIX, tags);
        createdEvent.getExchange().setProperty("eventTimer:" + name, timer);
        createdEvent.getExchange().setProperty("eventTimerContext:" + name, timer.time());
        exchangeRecorder.recordExchangeBegin();
    }

    protected void handleSentEvent(ExchangeSentEvent sentEvent) {
        String name = namingStrategy.getName(sentEvent.getExchange(), sentEvent.getEndpoint());
        Timer timer = sentEvent.getExchange().getProperty("eventTimer:" + name, Timer.class);
        if (timer == null) {
            Tag[] tags = namingStrategy.getTags(sentEvent, sentEvent.getEndpoint());
            timer = getMetricRegistry().timer(name + PROCESSING_METRICS_SUFFIX, tags);
            sentEvent.getExchange().setProperty("eventTimer:" + name, timer);
        }
        timer.update(sentEvent.getTimeTaken(), TimeUnit.MILLISECONDS);
    }

    protected void handleDoneEvent(ExchangeEvent doneEvent) {
        Exchange exchange = doneEvent.getExchange();
        String name = namingStrategy.getName(exchange, exchange.getFromEndpoint());
        exchange.removeProperty("eventTimer:" + name);
        Context context = (Context) exchange.removeProperty("eventTimerContext:" + name);
        if (context != null) {
            context.stop();
        }
        exchangeRecorder.recordExchangeComplete(exchange);
    }
}
