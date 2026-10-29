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
package org.apache.camel.component.google.calendar;

import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.google.api.services.calendar.Calendar;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.google.calendar.internal.GoogleCalendarApiCollection;
import org.apache.camel.component.google.calendar.internal.GoogleCalendarApiName;
import org.apache.camel.component.google.calendar.internal.GoogleCalendarConstants;
import org.apache.camel.component.google.calendar.internal.GoogleCalendarPropertiesHelper;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.component.AbstractApiEndpoint;
import org.apache.camel.support.component.ApiMethod;
import org.apache.camel.support.component.ApiMethodPropertiesHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * Perform various operations on a Google Calendar.
 */
@UriEndpoint(firstVersion = "2.15.0", scheme = "google-calendar", title = "Google Calendar",
             syntax = "google-calendar:apiName/methodName", apiSyntax = "apiName/methodName",
             consumerPrefix = "consumer", category = { Category.API, Category.CLOUD })
@BodyTypes(consumerOut = @BodyType(value = Carrier.POJO,
                                   description = "One message per poll with the return value of the method: a Google"
                                                 + " Calendar model object, such as the Events of events/list, which is one"
                                                 + " message and not split per event. The model classes are GenericJson,"
                                                 + " which is also a Map of the JSON fields"),
           producerIn = @BodyType(value = Carrier.ANY, dependsOn = "inBody",
                                  description = "The body is used only when inBody names a parameter of the method, and"
                                                + " must then be of the type of that parameter: an Event for content of"
                                                + " events/insert, or text for text of events/quickAdd. JSON text or a Map is"
                                                + " not turned into a model object: build it first. Without inBody the"
                                                + " arguments come from the options and the CamelGoogleCalendar. headers"),
           producerOut = @BodyType(value = { Carrier.POJO, Carrier.NULL },
                                   description = "The return value of the method: a Google Calendar model object, such as"
                                                 + " the Event of events/insert and events/get or the Events of events/list;"
                                                 + " null for the methods without a response: events/delete, acl/delete,"
                                                 + " calendars/clear, calendars/delete, list/delete and channels/stop. The"
                                                 + " model classes are GenericJson, which is also a Map of the JSON fields"))
public class GoogleCalendarEndpoint extends AbstractApiEndpoint<GoogleCalendarApiName, GoogleCalendarConfiguration>
        implements EndpointServiceLocation {

    @UriParam
    private GoogleCalendarConfiguration configuration;
    private final Lock clientLock = new ReentrantLock();
    private volatile Calendar client;

    private Object apiProxy;

    public GoogleCalendarEndpoint(String uri, GoogleCalendarComponent component,
                                  GoogleCalendarApiName apiName, String methodName,
                                  GoogleCalendarConfiguration endpointConfiguration) {
        super(uri, component, apiName, methodName, GoogleCalendarApiCollection.getCollection().getHelper(apiName),
              endpointConfiguration);
        this.configuration = endpointConfiguration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new GoogleCalendarProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        // make sure inBody is not set for consumers
        if (inBody != null) {
            throw new IllegalArgumentException("Option inBody is not supported for consumer endpoint");
        }
        final GoogleCalendarConsumer consumer = new GoogleCalendarConsumer(this, processor);
        // also set consumer.* properties
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    protected ApiMethodPropertiesHelper<GoogleCalendarConfiguration> getPropertiesHelper() {
        return GoogleCalendarPropertiesHelper.getHelper(getCamelContext());
    }

    @Override
    protected String getThreadProfileName() {
        return GoogleCalendarConstants.THREAD_PROFILE_NAME;
    }

    @Override
    protected void afterConfigureProperties() {
        switch (apiName) {
            case LIST:
                apiProxy = getClient().calendarList();
                break;
            case ACL:
                apiProxy = getClient().acl();
                break;
            case CALENDARS:
                apiProxy = getClient().calendars();
                break;
            case CHANNELS:
                apiProxy = getClient().channels();
                break;
            case COLORS:
                apiProxy = getClient().colors();
                break;
            case EVENTS:
                apiProxy = getClient().events();
                break;
            case FREEBUSY:
                apiProxy = getClient().freebusy();
                break;
            case SETTINGS:
                apiProxy = getClient().settings();
                break;
            default:
                throw new IllegalArgumentException("Invalid API name " + apiName);
        }
    }

    /**
     * The client of this endpoint, created on first use from the options of this endpoint and kept for the lifetime of
     * the endpoint.
     */
    public Calendar getClient() {
        Calendar answer = client;
        if (answer == null) {
            clientLock.lock();
            try {
                if (client == null) {
                    client = ((GoogleCalendarComponent) getComponent()).getClient(configuration);
                }
                answer = client;
            } finally {
                clientLock.unlock();
            }
        }
        return answer;
    }

    @Override
    public Object getApiProxy(ApiMethod method, Map<String, Object> args) {
        return apiProxy;
    }

    @Override
    public String getServiceUrl() {
        if (ObjectHelper.isNotEmpty(ObjectHelper.isNotEmpty(configuration.getApiName())
                && ObjectHelper.isNotEmpty(configuration.getMethodName()))) {
            return getServiceProtocol() + ":" + configuration.getApiName() + ":" + configuration.getMethodName();
        }
        return null;
    }

    @Override
    public String getServiceProtocol() {
        return "calendar";
    }

    @Override
    public Map<String, String> getServiceMetadata() {
        if (configuration.getApplicationName() != null) {
            return Map.of("applicationName", configuration.getApplicationName());
        }
        return null;
    }

}
