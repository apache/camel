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
package org.apache.camel.component.micrometer.eventnotifier;

import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.apache.camel.Exchange;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelEvent.ExchangeCompletedEvent;
import org.apache.camel.spi.CamelEvent.ExchangeCreatedEvent;
import org.apache.camel.spi.CamelEvent.ExchangeEvent;
import org.apache.camel.spi.CamelEvent.ExchangeFailedEvent;
import org.apache.camel.spi.CamelEvent.ExchangeSentEvent;
import org.apache.camel.spi.InflightRepository;

public class MicrometerExchangeEventNotifier extends AbstractMicrometerEventNotifier<ExchangeEvent> {
    private InflightRepository inflightRepository;
    private Predicate<Exchange> ignoreExchanges = exchange -> false;
    private MicrometerExchangeEventNotifierNamingStrategy namingStrategy
            = MicrometerExchangeEventNotifierNamingStrategy.DEFAULT;

    public MicrometerExchangeEventNotifier() {
        super(ExchangeEvent.class);
    }

    public void setIgnoreExchanges(Predicate<Exchange> ignoreExchanges) {
        this.ignoreExchanges = ignoreExchanges;
    }

    public Predicate<Exchange> getIgnoreExchanges() {
        return ignoreExchanges;
    }

    public MicrometerExchangeEventNotifierNamingStrategy getNamingStrategy() {
        return namingStrategy;
    }

    public void setNamingStrategy(MicrometerExchangeEventNotifierNamingStrategy namingStrategy) {
        this.namingStrategy = namingStrategy;
    }

    @Override
    protected void doStart() throws Exception {
        inflightRepository = getCamelContext().getInflightRepository();
        super.doStart();
    }

    @Override
    public void notify(CamelEvent eventObject) {
        if (!(getIgnoreExchanges().test(((ExchangeEvent) eventObject).getExchange()))) {
            handleExchangeEvent((ExchangeEvent) eventObject);
            if (eventObject instanceof ExchangeCreatedEvent) {
                handleCreatedEvent((ExchangeCreatedEvent) eventObject);
            } else if (eventObject instanceof ExchangeSentEvent) {
                handleSentEvent((ExchangeSentEvent) eventObject);
            } else if (eventObject instanceof ExchangeCompletedEvent || eventObject instanceof ExchangeFailedEvent) {
                handleDoneEvent((ExchangeEvent) eventObject);
            }
        }
    }

    private void handleExchangeEvent(ExchangeEvent exchangeEvent) {
        Exchange exchange = exchangeEvent.getExchange();
        if (exchange.getFromRouteId() != null && exchange.getFromEndpoint() != null) {
            String name = namingStrategy.getInflightExchangesName(exchange, exchange.getFromEndpoint());
            Tags tags = namingStrategy.getInflightExchangesTags(exchangeEvent, exchange.getFromEndpoint());
            Gauge.builder(name, () -> getInflightExchangesInRoute(exchangeEvent))
                    .description("Route inflight messages")
                    .tags(tags)
                    .register(getMeterRegistry());
        }
    }

    protected void handleSentEvent(ExchangeSentEvent sentEvent) {
        String name = namingStrategy.getName(sentEvent.getExchange(), sentEvent.getEndpoint());
        Tags tags = namingStrategy.getTags(sentEvent, sentEvent.getEndpoint());
        Timer timer = Timer.builder(name).tags(tags).description("Time taken to send message to the endpoint")
                .register(getMeterRegistry());
        timer.record(sentEvent.getTimeTaken(), TimeUnit.MILLISECONDS);
    }

    protected void handleCreatedEvent(ExchangeCreatedEvent createdEvent) {
        String name = namingStrategy.getName(createdEvent.getExchange(), createdEvent.getExchange().getFromEndpoint());
        createdEvent.getExchange().setProperty("eventTimer:" + name, Timer.start(getMeterRegistry()));
    }

    protected void handleDoneEvent(ExchangeEvent doneEvent) {
        String name = namingStrategy.getName(doneEvent.getExchange(), doneEvent.getExchange().getFromEndpoint());
        Tags tags = namingStrategy.getTags(doneEvent, doneEvent.getExchange().getFromEndpoint());
        // Would have preferred LongTaskTimer, but you cannot set the FAILED_TAG once it is registered
        Timer.Sample sample = (Timer.Sample) doneEvent.getExchange().removeProperty("eventTimer:" + name);
        if (sample != null) {
            sample.stop(getMeterRegistry().timer(name, tags));
        }
    }

    private int getInflightExchangesInRoute(ExchangeEvent exchangeEvent) {
        String routeId = exchangeEvent.getExchange().getFromRouteId();
        return inflightRepository.size(routeId);
    }

}
