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
package org.apache.camel.component.apns;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import com.notnoop.apns.ApnsService;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.ScheduledPollEndpoint;

/**
 * For sending notifications to Apple iOS devices.
 */
@UriEndpoint(firstVersion = "2.8.0", scheme = "apns", title = "APNS", syntax = "apns:name", label = "eventbus,mobile")
public class ApnsEndpoint extends ScheduledPollEndpoint {

    private final CopyOnWriteArraySet<DefaultConsumer> consumers = new CopyOnWriteArraySet<>();

    @UriPath(description = "Name of the endpoint")
    private String name;
    @UriParam
    private String tokens;

    public ApnsEndpoint(String uri, ApnsComponent component) {
        super(uri, component);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTokens() {
        return tokens;
    }

    /**
     * Configure this property in case you want to statically declare tokens related to devices you want to notify. Tokens are separated by comma.
     */
    public void setTokens(String tokens) {
        this.tokens = tokens;
    }

    private ApnsComponent getApnsComponent() {
        return (ApnsComponent)getComponent();
    }

    public ApnsService getApnsService() {
        return getApnsComponent().getApnsService();
    }

    protected Set<DefaultConsumer> getConsumers() {
        return consumers;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        ApnsConsumer apnsConsumer = new ApnsConsumer(this, processor);
        configureConsumer(apnsConsumer);
        return apnsConsumer;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new ApnsProducer(this);
    }

}
