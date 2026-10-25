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
package org.apache.camel.component.stripe;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Interact with the Stripe payment platform.
 */
@UriEndpoint(firstVersion = "4.17.0", scheme = "stripe", title = "Stripe", syntax = "stripe:operation",
             category = { Category.SAAS, Category.FINANCE }, producerOnly = true, headersClass = StripeConstants.class)
@BodyTypes(producerIn = @BodyType(value = { Carrier.MAP, Carrier.TEXT, Carrier.NULL },
                                  description = "The request parameters of the call: a Map, or a JSON object as"
                                                + " text; null for none. Bytes, a stream or a POJO fails: convert it"
                                                + " to a String first. retrieve, delete and cancel ignore the body;"
                                                + " they and update and capture take the id of the object from the"
                                                + " CamelStripeObjectId header"),
           producerOut = @BodyType(value = Carrier.POJO,
                                   description = "The Stripe object the call returns, such as a"
                                                 + " com.stripe.model.Customer; list gives a collection of them, such"
                                                 + " as a CustomerCollection, with the objects in its data"))
public class StripeEndpoint extends DefaultEndpoint implements EndpointServiceLocation {

    @UriParam
    private StripeConfiguration configuration;

    public StripeEndpoint(String uri, StripeComponent component, StripeConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new StripeProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Stripe component does not support consumers");
    }

    public StripeConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public String getServiceUrl() {
        return configuration.getApiBase() != null ? configuration.getApiBase() : "https://api.stripe.com";
    }

    @Override
    public String getServiceProtocol() {
        return "rest";
    }
}
