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

import org.apache.camel.Message;
import org.apache.camel.component.consul.ConsulConfiguration;
import org.apache.camel.component.consul.ConsulConstants;
import org.apache.camel.component.consul.ConsulEndpoint;
import org.apache.camel.spi.InvokeOnHeader;
import org.kiwiproject.consul.Consul;
import org.kiwiproject.consul.KeyValueClient;
import org.kiwiproject.consul.option.PutOptions;
import org.kiwiproject.consul.option.QueryOptions;

public final class ConsulKeyValueProducer extends AbstractConsulProducer<KeyValueClient> {

    public ConsulKeyValueProducer(ConsulEndpoint endpoint, ConsulConfiguration configuration) {
        super(endpoint, configuration, Consul::keyValueClient);
    }

    @InvokeOnHeader(ConsulKeyValueActions.PUT)
    protected void put(Message message) throws Exception {
        message.setHeader(ConsulConstants.CONSUL_RESULT,
                getClient().putValue(
                        getMandatoryHeader(message, ConsulConstants.CONSUL_KEY, getConfiguration().getKey(), String.class),
                        message.getBody(String.class),
                        message.getHeader(ConsulConstants.CONSUL_FLAGS, 0L, Long.class),
                        message.getHeader(ConsulConstants.CONSUL_OPTIONS, PutOptions.BLANK, PutOptions.class)));
    }

    @InvokeOnHeader(ConsulKeyValueActions.GET_VALUE)
    protected void getValue(Message message) throws Exception {
        Object result;

        boolean asString = message.getHeader(ConsulConstants.CONSUL_VALUE_AS_STRING, getConfiguration().isValueAsString(),
                Boolean.class);
        if (asString) {
            result = getClient()
                    .getValueAsString(
                            getMandatoryHeader(message, ConsulConstants.CONSUL_KEY, getConfiguration().getKey(), String.class))
                    .orElse(null);
        } else {
            result = getClient()
                    .getValue(
                            getMandatoryHeader(message, ConsulConstants.CONSUL_KEY, getConfiguration().getKey(), String.class),
                            message.getHeader(ConsulConstants.CONSUL_OPTIONS, QueryOptions.BLANK, QueryOptions.class))
                    .orElse(null);
        }

        setBodyAndResult(message, result);
    }

    @InvokeOnHeader(ConsulKeyValueActions.GET_VALUES)
    protected void getValues(Message message) throws Exception {
        Object result;

        boolean asString = message.getHeader(ConsulConstants.CONSUL_VALUE_AS_STRING, getConfiguration().isValueAsString(),
                Boolean.class);
        if (asString) {
            result = getClient().getValuesAsString(
                    getMandatoryHeader(message, ConsulConstants.CONSUL_KEY, getConfiguration().getKey(), String.class));
        } else {
            result = getClient().getValues(
                    getMandatoryHeader(message, ConsulConstants.CONSUL_KEY, getConfiguration().getKey(), String.class),
                    message.getHeader(ConsulConstants.CONSUL_OPTIONS, QueryOptions.BLANK, QueryOptions.class));
        }

        setBodyAndResult(message, result);
    }

    @InvokeOnHeader(ConsulKeyValueActions.GET_KEYS)
    protected void getKeys(Message message) throws Exception {
        setBodyAndResult(message, getClient()
                .getKeys(getMandatoryHeader(message, ConsulConstants.CONSUL_KEY, getConfiguration().getKey(), String.class)));
    }

    @InvokeOnHeader(ConsulKeyValueActions.GET_SESSIONS)
    protected void getSessions(Message message) throws Exception {
        setBodyAndResult(message, getClient().getSession(
                getMandatoryHeader(message, ConsulConstants.CONSUL_KEY, getConfiguration().getKey(), String.class)));
    }

    @InvokeOnHeader(ConsulKeyValueActions.DELETE_KEY)
    protected void deleteKey(Message message) throws Exception {
        getClient()
                .deleteKey(getMandatoryHeader(message, ConsulConstants.CONSUL_KEY, getConfiguration().getKey(), String.class));

        message.setHeader(ConsulConstants.CONSUL_RESULT, true);
    }

    @InvokeOnHeader(ConsulKeyValueActions.DELETE_KEYS)
    protected void deleteKeys(Message message) throws Exception {
        getClient()
                .deleteKeys(getMandatoryHeader(message, ConsulConstants.CONSUL_KEY, getConfiguration().getKey(), String.class));

        message.setHeader(ConsulConstants.CONSUL_RESULT, true);
    }

    @InvokeOnHeader(ConsulKeyValueActions.LOCK)
    protected void lock(Message message) throws Exception {
        message.setHeader(ConsulConstants.CONSUL_RESULT,
                getClient().acquireLock(
                        getMandatoryHeader(message, ConsulConstants.CONSUL_KEY, getConfiguration().getKey(), String.class),
                        message.getBody(String.class),
                        message.getHeader(ConsulConstants.CONSUL_SESSION, "", String.class)));
    }

    @InvokeOnHeader(ConsulKeyValueActions.UNLOCK)
    protected void unlock(Message message) throws Exception {
        message.setHeader(ConsulConstants.CONSUL_RESULT,
                getClient().releaseLock(
                        getMandatoryHeader(message, ConsulConstants.CONSUL_KEY, getConfiguration().getKey(), String.class),
                        getMandatoryHeader(message, ConsulConstants.CONSUL_SESSION, String.class)));
    }
}
