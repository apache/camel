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

package org.apache.camel.component.braintree;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.braintreegateway.BraintreeGateway;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.braintree.internal.BraintreeApiCollection;
import org.apache.camel.component.braintree.internal.BraintreeApiName;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.component.AbstractApiComponent;

/**
 * Represents the component that manages {@link BraintreeEndpoint}.
 */
@Component("braintree")
public class BraintreeComponent
        extends AbstractApiComponent<BraintreeApiName, BraintreeConfiguration, BraintreeApiCollection> {

    @Metadata
    private BraintreeConfiguration configuration;

    private final Map<String, BraintreeGateway> gateways;

    public BraintreeComponent() {
        super(BraintreeApiName.class, BraintreeApiCollection.getCollection());
        this.gateways = new ConcurrentHashMap<>();
    }

    public BraintreeComponent(CamelContext context) {
        super(context, BraintreeApiName.class, BraintreeApiCollection.getCollection());
        this.gateways = new ConcurrentHashMap<>();
    }

    @Override
    protected BraintreeApiName getApiName(String apiNameStr) {
        return getCamelContext().getTypeConverter().convertTo(BraintreeApiName.class, apiNameStr);
    }

    @Override
    protected Endpoint createEndpoint(
            String uri, String methodName, BraintreeApiName apiName, BraintreeConfiguration endpointConfiguration) {
        endpointConfiguration.setApiName(apiName);
        endpointConfiguration.setMethodName(methodName);
        this.configuration = endpointConfiguration;
        return new BraintreeEndpoint(uri, this, apiName, methodName, endpointConfiguration);
    }

    public BraintreeGateway getGateway(BraintreeConfiguration configuration) {
        BraintreeGateway gateway;
        if (configuration.getAccessToken() != null) {
            gateway =
                    gateways.computeIfAbsent(configuration.getAccessToken(), k -> configuration.newBraintreeGateway());
        } else {
            gateway = gateways.computeIfAbsent(configuration.getMerchantId(), k -> configuration.newBraintreeGateway());
        }
        return gateway;
    }
}
