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
import org.apache.camel.Endpoint;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.component.twitter.consumer.TwitterConsumerPolling;
import org.apache.camel.component.twitter.data.EndpointType;
import org.apache.camel.impl.DefaultPollingEndpoint;
import org.apache.camel.spi.UriParam;

/**
 * The base Twitter Endpoint.
 */
public abstract class AbstractTwitterEndpoint extends DefaultPollingEndpoint implements TwitterEndpoint {

    @UriParam(optionalPrefix = "consumer.", defaultValue = "" + TwitterConsumerPolling.DEFAULT_CONSUMER_DELAY, label = "consumer,scheduler",
        description = "Milliseconds before the next poll.")
    private long delay = TwitterConsumerPolling.DEFAULT_CONSUMER_DELAY;

    @UriParam
    private TwitterConfiguration properties;

    public AbstractTwitterEndpoint(String uri, AbstractTwitterComponent component, TwitterConfiguration properties) {
        super(uri, component);
        this.properties = properties;
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (properties.getType() == EndpointType.EVENT && properties.getTwitterStream() != null) {
            properties.getTwitterStream().shutdown();
        }
    }

    public void configureConsumer(Consumer consumer) throws Exception {
        super.configureConsumer(consumer);
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
        return getProperties().getType();
    }

    /**
     * Milliseconds before the next poll.
     */
    @Override
    public void setDelay(long delay) {
        super.setDelay(delay);
        this.delay = delay;
    }

}
