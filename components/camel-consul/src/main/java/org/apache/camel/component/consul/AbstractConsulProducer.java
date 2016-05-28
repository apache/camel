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
package org.apache.camel.component.consul;

import java.util.function.Function;

import com.orbitz.consul.Consul;
import org.apache.camel.Message;
import org.apache.camel.NoSuchHeaderException;
import org.apache.camel.Processor;
import org.apache.camel.impl.HeaderSelectorProducer;

public abstract class AbstractConsulProducer<C> extends HeaderSelectorProducer {
    private final ConsulEndpoint endpoint;
    private final ConsulConfiguration configuration;
    private final Function<Consul, C> clientSupplier;
    private C client;

    protected AbstractConsulProducer(ConsulEndpoint endpoint, ConsulConfiguration configuration, Function<Consul, C> clientSupplier) {
        super(endpoint, ConsulConstants.CONSUL_ACTION, configuration.getAction());

        this.endpoint = endpoint;
        this.configuration = configuration;
        this.clientSupplier = clientSupplier;
        this.client = null;
    }

    // *************************************************************************
    //
    // *************************************************************************

    protected Consul getConsul() throws Exception {
        return endpoint.getConsul();
    }

    protected C getClient() throws Exception {
        if (client == null) {
            client = clientSupplier.apply(getConsul());
        }

        return client;
    }

    protected ConsulConfiguration getConfiguration() {
        return configuration;
    }

    protected <D> D getHeader(Message message, String header, D defaultValue, Class<D> type) {
        return message.getHeader(header, defaultValue, type);
    }

    protected <D> D getMandatoryHeader(Message message, String header, Class<D> type) throws Exception {
        return getMandatoryHeader(message, header, null, type);
    }

    protected <D> D getMandatoryHeader(Message message, String header, D defaultValue, Class<D> type) throws Exception {
        D value = getHeader(message, header, defaultValue, type);
        if (value == null) {
            throw new NoSuchHeaderException(message.getExchange(), header, type);
        }

        return value;
    }

    protected String getKey(Message message) {
        return message.getHeader(
            ConsulConstants.CONSUL_KEY,
            configuration.getKey(),
            String.class);
    }

    protected String getMandatoryKey(Message message) throws Exception {
        return getMandatoryHeader(
            message,
            ConsulConstants.CONSUL_KEY,
            configuration.getKey(),
            String.class);
    }

    protected <T> T getOption(Message message, T defaultValue, Class<T> type) {
        return message.getHeader(ConsulConstants.CONSUL_OPTIONS, defaultValue, type);
    }

    protected boolean isValueAsString(Message message) throws Exception {
        return message.getHeader(
            ConsulConstants.CONSUL_VALUE_AS_STRING,
            configuration.isValueAsString(),
            Boolean.class);
    }

    protected <T> T getBody(Message message, T defaultValue, Class<T> type) throws Exception {
        T body = message.getBody(type);
        if (body == null) {
            body = defaultValue;
        }

        return  body;
    }

    protected void setBodyAndResult(Message message, Object body) throws Exception {
        setBodyAndResult(message, body, body != null);
    }

    protected void setBodyAndResult(Message message, Object body, boolean result) throws Exception {
        message.setHeader(ConsulConstants.CONSUL_RESULT, result);
        if (body != null) {
            message.setBody(body);
        }
    }

    protected Processor wrap(Function<C, Object> supplier) {
        return exchange -> setBodyAndResult(exchange.getIn(), supplier.apply(getClient()));
    }
}
