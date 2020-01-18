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
package org.apache.camel.component.cron;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.DelegateEndpoint;
import org.apache.camel.Endpoint;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.cron.api.CamelCronConfiguration;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;

@UriEndpoint(firstVersion = "3.1.0", scheme = "cron", title = "Cron", syntax = "cron:name", consumerOnly = true, label = "scheduling")
public class CronEndpoint extends DefaultEndpoint implements DelegateEndpoint {

    private Endpoint delegate;

    @UriParam
    private CamelCronConfiguration configuration;

    public CronEndpoint(String endpointUri, Component component, Endpoint delegate, CamelCronConfiguration configuration) {
        super(endpointUri, component);
        this.delegate = delegate;
        this.configuration = configuration;
    }

    @Override
    public Endpoint getEndpoint() {
        return delegate;
    }

    @Override
    public Producer createProducer() throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        Consumer consumer = delegate.createConsumer(processor);
        configureConsumer(consumer);
        return consumer;
    }

    public CamelCronConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public void setSynchronous(boolean synchronous) {
        super.setSynchronous(synchronous);
        if (delegate instanceof DefaultEndpoint) {
            ((DefaultEndpoint) delegate).setSynchronous(synchronous);
        }
    }

    @Override
    public void setBasicPropertyBinding(boolean basicPropertyBinding) {
        super.setBasicPropertyBinding(basicPropertyBinding);
        if (delegate instanceof DefaultEndpoint) {
            ((DefaultEndpoint) delegate).setBasicPropertyBinding(basicPropertyBinding);
        }
    }

    @Override
    public void setExchangePattern(ExchangePattern exchangePattern) {
        super.setExchangePattern(exchangePattern);
        if (delegate instanceof DefaultEndpoint) {
            ((DefaultEndpoint) delegate).setExchangePattern(exchangePattern);
        }
    }

    @Override
    public void setExceptionHandler(ExceptionHandler exceptionHandler) {
        super.setExceptionHandler(exceptionHandler);
        if (delegate instanceof DefaultEndpoint) {
            ((DefaultEndpoint) delegate).setExceptionHandler(exceptionHandler);
        }
    }

    @Override
    public void setBridgeErrorHandler(boolean bridgeErrorHandler) {
        super.setBridgeErrorHandler(bridgeErrorHandler);
        if (delegate instanceof DefaultEndpoint) {
            ((DefaultEndpoint) delegate).setBridgeErrorHandler(bridgeErrorHandler);
        }
    }

}
