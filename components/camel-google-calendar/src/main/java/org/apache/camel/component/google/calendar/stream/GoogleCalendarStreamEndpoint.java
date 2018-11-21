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
package org.apache.camel.component.google.calendar.stream;

import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.google.calendar.GoogleCalendarClientFactory;
import org.apache.camel.impl.ScheduledPollEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The google-calendar component provides access to Google Calendar in a streaming mod.
 */
@UriEndpoint(firstVersion = "2.23.0",
             scheme = "google-calendar-stream",
             title = "Google Calendar Stream",
             syntax = "google-calendar-stream:index",
             consumerClass = GoogleCalendarStreamConsumer.class,
             consumerOnly = true,
             label = "api,cloud")
public class GoogleCalendarStreamEndpoint extends ScheduledPollEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleCalendarStreamEndpoint.class);

    @UriParam
    private GoogleCalendarStreamConfiguration configuration;

    public GoogleCalendarStreamEndpoint(String uri, GoogleCalendarStreamComponent component, GoogleCalendarStreamConfiguration endpointConfiguration) {
        super(uri, component);
        this.configuration = endpointConfiguration;
    }

    @Override
    public Producer createProducer() throws Exception {
        throw new UnsupportedOperationException("The camel google calendar stream component doesn't support producer");
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        final GoogleCalendarStreamConsumer consumer = new GoogleCalendarStreamConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    public Calendar getClient() {
        return ((GoogleCalendarStreamComponent)getComponent()).getClient(configuration);
    }

    public GoogleCalendarClientFactory getClientFactory() {
        return ((GoogleCalendarStreamComponent)getComponent()).getClientFactory();
    }

    public void setClientFactory(GoogleCalendarClientFactory clientFactory) {
        ((GoogleCalendarStreamComponent)getComponent()).setClientFactory(clientFactory);
    }

    public GoogleCalendarStreamConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    public Exchange createExchange(ExchangePattern pattern, Event event) {
        Exchange exchange = super.createExchange(pattern);
        Message message = exchange.getIn();
        message.setBody(event);
        message.setHeader(GoogleCalendarStreamConstants.EVENT_ID, event.getId());
        return exchange;
    }
}
