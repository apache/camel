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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

import com.braintreegateway.BraintreeGateway;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.braintree.internal.BraintreeApiCollection;
import org.apache.camel.component.braintree.internal.BraintreeApiName;
import org.apache.camel.component.braintree.internal.BraintreeConstants;
import org.apache.camel.component.braintree.internal.BraintreePropertiesHelper;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.component.AbstractApiEndpoint;
import org.apache.camel.support.component.ApiMethod;
import org.apache.camel.support.component.ApiMethodPropertiesHelper;

/**
 * Process payments using Braintree Payments.
 */
@UriEndpoint(firstVersion = "2.17.0", scheme = "braintree", title = "Braintree", syntax = "braintree:apiName/methodName",
             producerOnly = true,
             apiSyntax = "apiName/methodName",
             category = { Category.SAAS })
public class BraintreeEndpoint extends AbstractApiEndpoint<BraintreeApiName, BraintreeConfiguration> {

    @UriParam
    private final BraintreeConfiguration configuration;

    private Object apiProxy;

    public BraintreeEndpoint(String uri,
                             BraintreeComponent component,
                             BraintreeApiName apiName,
                             String methodName,
                             BraintreeConfiguration configuration) {
        super(uri, component, apiName, methodName, BraintreeApiCollection.getCollection().getHelper(apiName), configuration);
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new BraintreeProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer not supported");
    }

    @Override
    public BraintreeComponent getComponent() {
        return (BraintreeComponent) super.getComponent();
    }

    @Override
    protected ApiMethodPropertiesHelper<BraintreeConfiguration> getPropertiesHelper() {
        return BraintreePropertiesHelper.getHelper(getCamelContext());
    }

    @Override
    protected String getThreadProfileName() {
        return BraintreeConstants.THREAD_PROFILE_NAME;
    }

    @Override
    protected void afterConfigureProperties() {
        BraintreeGateway gateway = getComponent().getGateway(this.configuration);
        try {
            Method method = gateway.getClass().getMethod(apiName.getName());
            apiProxy = method.invoke(gateway);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public Object getApiProxy(ApiMethod method, Map<String, Object> args) {
        return apiProxy;
    }
}
