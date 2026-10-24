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
import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.component.sjms.SjmsConstants;
import org.apache.camel.component.sjms.SjmsEndpoint;
import org.apache.camel.component.sjms2.jms.Jms2ObjectFactory;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;

/**
 * Send and receive messages to/from a JMS Queue or Topic using plain JMS 2.x API.
 *
 * This component uses plain JMS 2.x API, whereas the jms component uses Spring JMS.
 */
@UriEndpoint(firstVersion = "2.19.0", scheme = "sjms2", extendsScheme = "sjms", title = "Simple JMS2",
             syntax = "sjms2:destinationType:destinationName", category = { Category.MESSAGING },
             headersClass = SjmsConstants.class)
@BodyTypes(consumerOut = @BodyType(value = { Carrier.TEXT, Carrier.BYTES, Carrier.MAP, Carrier.POJO, Carrier.NULL },
                                   byOption = {
                                           @BodyType.When(option = "mapJmsMessage", value = "false", carriers = Carrier.POJO,
                                                          javaType = "jakarta.jms.Message"),
                                           @BodyType.When(option = "objectMessageEnabled", value = "true",
                                                          carriers = {
                                                                  Carrier.TEXT, Carrier.BYTES, Carrier.MAP, Carrier.VALUE,
                                                                  Carrier.LIST, Carrier.POJO, Carrier.NULL }) },
                                   description = "By the JMS message received: a String for a TextMessage, a byte array for a"
                                                 + " BytesMessage, a Map for a MapMessage, the jakarta.jms.StreamMessage itself"
                                                 + " for a StreamMessage, and null for a plain Message. An ObjectMessage fails"
                                                 + " unless objectMessageEnabled"),
           consumerReply = @BodyType(value = {
                   Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE, Carrier.MAP,
                   Carrier.NULL },
                                     dependsOn = "jmsMessageType",
                                     byOption = @BodyType.When(option = "objectMessageEnabled", value = "true",
                                                               carriers = {
                                                                       Carrier.TEXT, Carrier.BYTES, Carrier.STREAM,
                                                                       Carrier.FILE, Carrier.MAP, Carrier.VALUE, Carrier.LIST,
                                                                       Carrier.POJO, Carrier.NULL }),
                                     description = "The reply, sent as the producer sends a message. A reply that cannot be"
                                                   + " sent, such as a number or a List without objectMessageEnabled, is"
                                                   + " dropped and the caller times out"),
           producerIn = @BodyType(value = {
                   Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE, Carrier.MAP,
                   Carrier.NULL },
                                  dependsOn = { "jmsMessageType", "allowNullBody" },
                                  byOption = @BodyType.When(option = "objectMessageEnabled", value = "true",
                                                            carriers = {
                                                                    Carrier.TEXT, Carrier.BYTES, Carrier.STREAM,
                                                                    Carrier.FILE, Carrier.MAP, Carrier.VALUE, Carrier.LIST,
                                                                    Carrier.POJO, Carrier.NULL }),
                                  description = "Text is sent as a TextMessage; bytes, a stream or a file as a BytesMessage;"
                                                + " a Map of simple values as a MapMessage; null as an empty Message. A number,"
                                                + " a List or another Serializable object fails unless objectMessageEnabled;"
                                                + " a body that is not Serializable is sent as an empty Message"),
           producerOut = @BodyType(value = Carrier.UNKNOWN,
                                   byOption = {
                                           @BodyType.When(option = "exchangePattern", value = "InOnly",
                                                          carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "disableReplyTo", value = "true",
                                                          carriers = Carrier.UNCHANGED) },
                                   description = "Unchanged for a message that expects no reply (InOnly). A message that"
                                                 + " expects one (InOut) gets the body of the reply, as the consumer gives it"))
public class Sjms2Endpoint extends SjmsEndpoint implements AsyncEndpoint {

    @UriParam(label = "consumer", description = "Sets the topic subscription id, required for durable or shared topics.")
    private String subscriptionId;
    @UriParam(label = "consumer", description = "Sets the topic to be durable")
    private boolean durable;
    @UriParam(label = "consumer", description = "Sets the topic to be shared")
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

    @Override
    public void setDurableSubscriptionName(String durableSubscriptionId) {
        super.setDurableSubscriptionName(durableSubscriptionId);
        subscriptionId = durableSubscriptionId;
        durable = true;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public void setSubscriptionId(String subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public boolean isDurable() {
        return durable;
    }

    public void setDurable(boolean durable) {
        this.durable = durable;
    }

    public boolean isShared() {
        return shared;
    }

    public void setShared(boolean shared) {
        this.shared = shared;
    }

}
