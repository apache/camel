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
package org.apache.camel.component.consul.endpoint;

import java.math.BigInteger;
import java.util.List;
import java.util.function.Function;

import org.apache.camel.Message;
import org.apache.camel.NoSuchHeaderException;
import org.apache.camel.Processor;
import org.apache.camel.component.consul.ConsulConfiguration;
import org.apache.camel.component.consul.ConsulConstants;
import org.apache.camel.component.consul.ConsulEndpoint;
import org.apache.camel.support.HeaderSelectorProducer;
import org.apache.camel.util.ObjectHelper;
import org.kiwiproject.consul.Consul;
import org.kiwiproject.consul.model.ConsulResponse;
import org.kiwiproject.consul.option.ConsistencyMode;
import org.kiwiproject.consul.option.ImmutableQueryOptions;
import org.kiwiproject.consul.option.QueryOptions;

abstract class AbstractConsulProducer<C> extends HeaderSelectorProducer {
    private final ConsulEndpoint endpoint;
    private final ConsulConfiguration configuration;
    private final Function<Consul, C> clientSupplier;
    private C client;

    protected AbstractConsulProducer(ConsulEndpoint endpoint, ConsulConfiguration configuration,
                                     Function<Consul, C> clientSupplier) {
        super(endpoint, ConsulConstants.CONSUL_ACTION, configuration.getAction());

        this.endpoint = endpoint;
        this.configuration = configuration;
        this.clientSupplier = clientSupplier;
        this.client = null;
    }

    // *************************************************************************
    //
    // *************************************************************************

    protected C getClient() throws Exception {
        if (client == null) {
            client = clientSupplier.apply(endpoint.getConsul());
        }

        return client;
    }

    protected ConsulConfiguration getConfiguration() {
        return configuration;
    }

    protected <D> D getMandatoryHeader(Message message, String header, Class<D> type) throws NoSuchHeaderException {
        return getMandatoryHeader(message, header, (D) null, type);
    }

    protected <D> D getMandatoryHeader(Message message, String header, D defaultValue, Class<D> type)
            throws NoSuchHeaderException {
        D value = message.getHeader(header, defaultValue, type);
        if (value == null) {
            throw new NoSuchHeaderException(message.getExchange(), header, type);
        }

        return value;
    }

    protected QueryOptions buildQueryOptions(Message message, ConsulConfiguration conf) {
        ImmutableQueryOptions.Builder builder = ImmutableQueryOptions.builder();

        ObjectHelper.ifNotEmpty(message.getHeader(ConsulConstants.CONSUL_INDEX, BigInteger.class), builder::index);
        ObjectHelper.ifNotEmpty(message.getHeader(ConsulConstants.CONSUL_WAIT, String.class), builder::wait);
        ObjectHelper.ifNotEmpty(message.getHeader(ConsulConstants.CONSUL_DATACENTER, conf.getDatacenter(), String.class),
                builder::datacenter);
        ObjectHelper.ifNotEmpty(message.getHeader(ConsulConstants.CONSUL_NEAR_NODE, conf.getNearNode(), String.class),
                builder::near);
        ObjectHelper.ifNotEmpty(conf.getAclToken(), builder::token);
        ObjectHelper.ifNotEmpty(
                message.getHeader(ConsulConstants.CONSUL_CONSISTENCY_MODE, conf.getConsistencyMode(), ConsistencyMode.class),
                builder::consistencyMode);
        ObjectHelper.ifNotEmpty(message.getHeader(ConsulConstants.CONSUL_NODE_META, conf.getNodeMeta(), List.class),
                builder::nodeMeta);

        return builder.build();
    }

    protected <T> void processConsulResponse(Message message, ConsulResponse<T> response) {
        message.setHeader(ConsulConstants.CONSUL_INDEX, response.getIndex());
        message.setHeader(ConsulConstants.CONSUL_LAST_CONTACT, response.getLastContact());
        message.setHeader(ConsulConstants.CONSUL_KNOWN_LEADER, response.isKnownLeader());

        setBodyAndResult(message, response.getResponse());
    }

    protected void setBodyAndResult(Message message, Object body) {
        setBodyAndResult(message, body, body != null);
    }

    protected void setBodyAndResult(Message message, Object body, boolean result) {
        message.setHeader(ConsulConstants.CONSUL_RESULT, result);
        if (body != null) {
            message.setBody(body);
        }
    }

    protected Processor wrap(Function<C, Object> supplier) {
        return exchange -> setBodyAndResult(exchange.getIn(), supplier.apply(getClient()));
    }
}
