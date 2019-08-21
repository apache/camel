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

import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.apache.camel.Exchange;
import org.apache.camel.component.microprofile.metrics.event.notifier.AbstractMicroProfileMetricsEventNotifier;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelEvent.ExchangeCompletedEvent;
import org.apache.camel.spi.CamelEvent.ExchangeCreatedEvent;
import org.apache.camel.spi.CamelEvent.ExchangeEvent;
import org.apache.camel.spi.CamelEvent.ExchangeSentEvent;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.Timer;
import org.eclipse.microprofile.metrics.Timer.Context;

public class MicroProfileMetricsExchangeEventNotifier extends AbstractMicroProfileMetricsEventNotifier<ExchangeEvent> {

    private Predicate<Exchange> ignoreExchanges = exchange -> false;
    private MicroProfileMetricsExchangeEventNotifierNamingStrategy namingStrategy = MicroProfileMetricsExchangeEventNotifierNamingStrategy.DEFAULT;

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
        Timer timer = getMetricRegistry().timer(name, tags);
        createdEvent.getExchange().setProperty("eventTimer:" + name, timer);
        createdEvent.getExchange().setProperty("eventTimerContext:" + name, timer.time());
    }

    protected void handleSentEvent(ExchangeSentEvent sentEvent) {
        String name = namingStrategy.getName(sentEvent.getExchange(), sentEvent.getEndpoint());
        Timer timer = sentEvent.getExchange().getProperty("eventTimer:" + name, Timer.class);
        if (timer == null) {
            Tag[] tags = namingStrategy.getTags(sentEvent, sentEvent.getEndpoint());
            timer = getMetricRegistry().timer(name, tags);
            sentEvent.getExchange().setProperty("eventTimer:" + name, timer);
        }
        timer.update(sentEvent.getTimeTaken(), TimeUnit.MILLISECONDS);
    }

    protected void handleDoneEvent(ExchangeEvent doneEvent) {
        String name = namingStrategy.getName(doneEvent.getExchange(), doneEvent.getExchange().getFromEndpoint());
        doneEvent.getExchange().removeProperty("eventTimer:" + name);
        Context context = (Context) doneEvent.getExchange().removeProperty("eventTimerContext:" + name);
        if (context != null) {
            context.stop();
        }
    }
}
