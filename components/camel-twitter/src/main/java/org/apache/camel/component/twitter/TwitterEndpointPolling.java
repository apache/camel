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
package org.apache.camel.component.twitter;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.ServiceStatus;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.twitter.consumer.Twitter4JConsumer;
import org.apache.camel.component.twitter.consumer.TwitterConsumerPolling;
import org.apache.camel.component.twitter.data.EndpointType;
import org.apache.camel.impl.DefaultPollingEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;

/**
 * Twitter polling endpoint
 */
@ManagedResource(description = "Managed Twitter Endpoint")
@UriEndpoint(scheme = "twitter", title = "Twitter", syntax = "twitter:type", consumerClass = Twitter4JConsumer.class, label = "api,social")
public class TwitterEndpointPolling extends DefaultPollingEndpoint implements TwitterEndpoint {

    @UriParam
    private TwitterConfiguration properties;

    public TwitterEndpointPolling(String uri, TwitterComponent component, TwitterConfiguration properties) {
        super(uri, component);
        this.properties = properties;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        Twitter4JConsumer twitter4jConsumer = Twitter4JFactory.getConsumer(this, getEndpointUri());
        // update the pulling lastID with sinceId
        twitter4jConsumer.setLastId(properties.getSinceId());
        Consumer tc = new TwitterConsumerPolling(this, processor, twitter4jConsumer);
        configureConsumer(tc);
        return tc;
    }

    @Override
    public Producer createProducer() throws Exception {
        return Twitter4JFactory.getProducer(this, getEndpointUri());
    }

    @ManagedAttribute
    public boolean isSingleton() {
        return true;
    }

    public TwitterConfiguration getProperties() {
        return properties;
    }

    public void setProperties(TwitterConfiguration properties) {
        this.properties = properties;
    }

    @ManagedAttribute(description = "Camel ID")
    public String getCamelId() {
        return getCamelContext().getName();
    }

    @ManagedAttribute(description = "Camel ManagementName")
    public String getCamelManagementName() {
        return getCamelContext().getManagementName();
    }

    @ManagedAttribute(description = "Endpoint Uri", mask = true)
    @Override
    public String getEndpointUri() {
        return super.getEndpointUri();
    }

    @ManagedAttribute(description = "Service State")
    public String getState() {
        ServiceStatus status = this.getStatus();
        // if no status exists then its stopped
        if (status == null) {
            status = ServiceStatus.Stopped;
        }
        return status.name();
    }

    @ManagedAttribute
    public String getKeywords() {
        return getProperties().getKeywords();
    }

    @ManagedAttribute
    public void setKeywords(String keywords) {
        getProperties().setKeywords(keywords);
    }

    @ManagedAttribute
    public String getLocations() {
        return getProperties().getLocations();
    }

    @ManagedAttribute
    public void setLocations(String locations) {
        getProperties().setLocations(locations);
    }

    @ManagedAttribute
    public void setFilterOld(boolean filterOld) {
        getProperties().setFilterOld(filterOld);
    }

    @ManagedAttribute
    public boolean isFilterOld() {
        return getProperties().isFilterOld();
    }

    @ManagedAttribute
    public void setDate(String date) {
        getProperties().setDate(date);
    }

    @ManagedAttribute
    public String getDate() {
        return getProperties().getDate();
    }

    @ManagedAttribute
    public void setSinceId(long sinceId) {
        getProperties().setSinceId(sinceId);
    }

    @ManagedAttribute
    public long getSinceId() {
        return getProperties().getSinceId();
    }

    @ManagedAttribute
    public void setLang(String lang) {
        getProperties().setLang(lang);
    }

    @ManagedAttribute
    public String getLang() {
        return getProperties().getLang();
    }

    @ManagedAttribute
    public void setCount(Integer count) {
        getProperties().setCount(count);
    }

    @ManagedAttribute
    public Integer getCount() {
        return getProperties().getCount();
    }

    @ManagedAttribute
    public void setNumberOfPages(Integer numberOfPages) {
        getProperties().setNumberOfPages(numberOfPages);
    }

    @ManagedAttribute
    public Integer getNumberOfPages() {
        return getProperties().getNumberOfPages();
    }

    @Override
    public EndpointType getEndpointType() {
        return EndpointType.POLLING;
    }

}
