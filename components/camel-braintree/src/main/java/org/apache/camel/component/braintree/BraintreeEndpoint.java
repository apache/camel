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
package org.apache.camel.component.braintree;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

import com.braintreegateway.BraintreeGateway;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.braintree.internal.BraintreeApiCollection;
import org.apache.camel.component.braintree.internal.BraintreeApiName;
import org.apache.camel.component.braintree.internal.BraintreeConstants;
import org.apache.camel.component.braintree.internal.BraintreePropertiesHelper;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.component.AbstractApiEndpoint;
import org.apache.camel.util.component.ApiMethod;
import org.apache.camel.util.component.ApiMethodPropertiesHelper;

/**
 * The braintree component is used for integrating with the Braintree Payment System.
 */
@UriEndpoint(firstVersion = "2.17.0", scheme = "braintree", title = "Braintree", syntax = "braintree:apiName/methodName", consumerClass = BraintreeConsumer.class, label = "api,cloud,payment")
public class BraintreeEndpoint extends AbstractApiEndpoint<BraintreeApiName, BraintreeConfiguration> {

    @UriParam
    private final BraintreeConfiguration configuration;

    private Object apiProxy;
    private final BraintreeGateway gateway;

    public BraintreeEndpoint(String uri, BraintreeComponent component,
                         BraintreeApiName apiName, String methodName, BraintreeConfiguration configuration, BraintreeGateway gateway) {
        super(uri, component, apiName, methodName, BraintreeApiCollection.getCollection().getHelper(apiName), configuration);
        this.configuration = configuration;
        this.gateway = gateway;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new BraintreeProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        BraintreeConsumer consumer = new BraintreeConsumer(this, processor);
        // also set consumer.* properties
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    protected ApiMethodPropertiesHelper<BraintreeConfiguration> getPropertiesHelper() {
        return BraintreePropertiesHelper.getHelper();
    }

    @Override
    protected String getThreadProfileName() {
        return BraintreeConstants.THREAD_PROFILE_NAME;
    }

    @Override
    protected void afterConfigureProperties() {
        try {
            Method method = gateway.getClass().getMethod(apiName.getName());
            if (method != null) {
                apiProxy = method.invoke(gateway);
            } else {
                throw new IllegalArgumentException("Invalid API name " + apiName);
            }
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(e);
        } catch (InvocationTargetException e) {
            throw new IllegalArgumentException(e);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public Object getApiProxy(ApiMethod method, Map<String, Object> args) {
        return apiProxy;
    }
}
