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
package org.apache.camel.component.micrometer.eventnotifier;

import java.util.EventObject;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import io.micrometer.core.instrument.Timer;
import org.apache.camel.Exchange;
import org.apache.camel.management.event.AbstractExchangeEvent;
import org.apache.camel.management.event.ExchangeCompletedEvent;
import org.apache.camel.management.event.ExchangeCreatedEvent;
import org.apache.camel.management.event.ExchangeFailedEvent;
import org.apache.camel.management.event.ExchangeSentEvent;
import static org.apache.camel.component.micrometer.MicrometerConstants.CAMEL_CONTEXT_TAG;
import static org.apache.camel.component.micrometer.MicrometerConstants.DEFAULT_CAMEL_EXCHANGE_EVENT_METER_NAME;
import static org.apache.camel.component.micrometer.MicrometerConstants.ENDPOINT_NAME;
import static org.apache.camel.component.micrometer.MicrometerConstants.EVENT_TYPE_TAG;
import static org.apache.camel.component.micrometer.MicrometerConstants.FAILED_TAG;
import static org.apache.camel.component.micrometer.MicrometerConstants.SERVICE_NAME;

public class MicrometerExchangeEventNotifier extends AbstractMicrometerEventNotifier<AbstractExchangeEvent> {

    private Predicate<Exchange> ignoreExchanges = exchange -> false;
    private MicrometerExchangeEventNotifierNamingStrategy namingStrategy = (exchange, endpoint) -> DEFAULT_CAMEL_EXCHANGE_EVENT_METER_NAME;

    public MicrometerExchangeEventNotifier() {
        super(AbstractExchangeEvent.class);
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
    public boolean isEnabled(EventObject eventObject) {
        return super.isEnabled(eventObject) && !ignoreExchanges.test(((AbstractExchangeEvent) eventObject).getExchange());
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
    }

    @Override
    public void notify(EventObject eventObject) {
        if (eventObject instanceof ExchangeSentEvent) {
            handleSentEvent((ExchangeSentEvent) eventObject);
        } else if (eventObject instanceof ExchangeCreatedEvent) {
            handleCreatedEvent((ExchangeCreatedEvent) eventObject);
        } else if (eventObject instanceof ExchangeCompletedEvent || eventObject instanceof ExchangeFailedEvent) {
            handleDoneEvent((AbstractExchangeEvent) eventObject);
        }
    }

    protected void handleSentEvent(ExchangeSentEvent sentEvent) {
        Timer.builder(namingStrategy.getName(sentEvent.getExchange(), sentEvent.getEndpoint()))
                .tag(CAMEL_CONTEXT_TAG, getCamelContext().getName())
                .tag(SERVICE_NAME, MicrometerEventNotifierService.class.getSimpleName())
                .tag(ENDPOINT_NAME, sentEvent.getEndpoint().getEndpointUri())
                .tag(FAILED_TAG, Boolean.toString(sentEvent.getExchange().isFailed()))
                .tag(EVENT_TYPE_TAG, sentEvent.getClass().getSimpleName())
                .register(getMeterRegistry())
                .record(sentEvent.getTimeTaken(), TimeUnit.MILLISECONDS);
    }

    protected void handleCreatedEvent(ExchangeCreatedEvent createdEvent) {
        String name = namingStrategy.getName(createdEvent.getExchange(), createdEvent.getExchange().getFromEndpoint());
        createdEvent.getExchange().setProperty("eventTimer:" + name, Timer.start(getMeterRegistry()));
    }


    protected void handleDoneEvent(AbstractExchangeEvent doneEvent) {
        String name = namingStrategy.getName(doneEvent.getExchange(), doneEvent.getExchange().getFromEndpoint());
        // Would have preferred LongTaskTimer, but you cannot set the FAILED_TAG once it is registered
        Timer.Sample sample = (Timer.Sample) doneEvent.getExchange().removeProperty("eventTimer:" + name);
        if (sample != null) {
            sample.stop(Timer.builder(name)
                    .tag(CAMEL_CONTEXT_TAG, getCamelContext().getName())
                    .tag(SERVICE_NAME, MicrometerEventNotifierService.class.getSimpleName())
                    .tag(ENDPOINT_NAME, doneEvent.getExchange().getFromEndpoint().getEndpointUri())
                    .tag(FAILED_TAG, Boolean.toString(doneEvent.getExchange().isFailed()))
                    .tag(EVENT_TYPE_TAG, doneEvent.getClass().getSimpleName())
                    .register(getMeterRegistry()));
        }
    }


}
