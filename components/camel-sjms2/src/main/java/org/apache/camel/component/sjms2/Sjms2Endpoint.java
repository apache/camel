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
package org.apache.camel.component.sjms2;

import org.apache.camel.AsyncEndpoint;
import org.apache.camel.Component;
import org.apache.camel.component.sjms.SjmsEndpoint;
import org.apache.camel.component.sjms2.jms.Jms2ObjectFactory;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;

/**
 * The sjms2 component (simple jms) allows messages to be sent to (or consumed from) a JMS Queue or Topic (uses JMS 2.x API).
 *
 * This component uses plain JMS 2.x API where as the jms component uses Spring JMS.
 */
@UriEndpoint(firstVersion = "2.19.0", scheme = "sjms2", extendsScheme = "sjms", title = "Simple JMS2",
        syntax = "sjms2:destinationType:destinationName", label = "messaging")
public class Sjms2Endpoint extends SjmsEndpoint implements AsyncEndpoint {

    @UriParam(label = "consumer")
    private String subscriptionId;
    @UriParam(label = "consumer")
    private boolean durable;
    @UriParam(label = "consumer")
    private boolean shared;

    public Sjms2Endpoint() {
    }

    public Sjms2Endpoint(String uri, Component component, String remaining) {
        super(uri, component, remaining);
        setJmsObjectFactory(new Jms2ObjectFactory());
    }

    @Override
    public Sjms2Component getComponent() {
        return (Sjms2Component) super.getComponent();
    }

    /**
     * Sets the durable subscription Id required for durable topics.
     */
    @Override
    public void setDurableSubscriptionId(String durableSubscriptionId) {
        super.setDurableSubscriptionId(durableSubscriptionId);
        subscriptionId = durableSubscriptionId;
        durable = true;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    /**
     * Sets the subscription Id, required for durable or shared topics.
     */
    public void setSubscriptionId(String subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public boolean isDurable() {
        return durable;
    }

    /**
     * Sets topic consumer to durable.
     */
    public void setDurable(boolean durable) {
        this.durable = durable;
    }

    public boolean isShared() {
        return shared;
    }

    /**
     * Sets the consumer to shared.
     */
    public void setShared(boolean shared) {
        this.shared = shared;
    }

}
