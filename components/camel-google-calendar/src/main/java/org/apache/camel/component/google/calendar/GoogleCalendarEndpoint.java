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
package org.apache.camel.component.google.calendar;

import java.util.Map;

import com.google.api.services.calendar.Calendar;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.google.calendar.internal.GoogleCalendarApiCollection;
import org.apache.camel.component.google.calendar.internal.GoogleCalendarApiName;
import org.apache.camel.component.google.calendar.internal.GoogleCalendarConstants;
import org.apache.camel.component.google.calendar.internal.GoogleCalendarPropertiesHelper;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.component.AbstractApiEndpoint;
import org.apache.camel.util.component.ApiMethod;
import org.apache.camel.util.component.ApiMethodPropertiesHelper;

/**
 * The google-calendar component provides access to Google Calendar.
 */
@UriEndpoint(firstVersion = "2.15.0", scheme = "google-calendar", title = "Google Calendar", syntax = "google-calendar:apiName/methodName",
consumerClass = GoogleCalendarConsumer.class, consumerPrefix = "consumer", label = "api,cloud")
public class GoogleCalendarEndpoint extends AbstractApiEndpoint<GoogleCalendarApiName, GoogleCalendarConfiguration> {

    @UriParam
    private GoogleCalendarConfiguration configuration;

    private Object apiProxy;

    public GoogleCalendarEndpoint(String uri, GoogleCalendarComponent component,
                         GoogleCalendarApiName apiName, String methodName, GoogleCalendarConfiguration endpointConfiguration) {
        super(uri, component, apiName, methodName, GoogleCalendarApiCollection.getCollection().getHelper(apiName), endpointConfiguration);
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
        return GoogleCalendarPropertiesHelper.getHelper();
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

    public Calendar getClient() {
        return ((GoogleCalendarComponent)getComponent()).getClient(configuration);
    }
    
    @Override
    public Object getApiProxy(ApiMethod method, Map<String, Object> args) {
        return apiProxy;
    }
    
    public GoogleCalendarClientFactory getClientFactory() {
        return ((GoogleCalendarComponent)getComponent()).getClientFactory();
    }

    public void setClientFactory(GoogleCalendarClientFactory clientFactory) {
        ((GoogleCalendarComponent)getComponent()).setClientFactory(clientFactory);
    }
}
