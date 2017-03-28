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
package org.apache.camel.component.consul.enpoint;

import com.orbitz.consul.KeyValueClient;
import com.orbitz.consul.option.PutOptions;
import com.orbitz.consul.option.QueryOptions;
import org.apache.camel.InvokeOnHeader;
import org.apache.camel.Message;
import org.apache.camel.component.consul.AbstractConsulProducer;
import org.apache.camel.component.consul.ConsulConfiguration;
import org.apache.camel.component.consul.ConsulConstants;
import org.apache.camel.component.consul.ConsulEndpoint;

public class ConsulKeyValueProducer extends AbstractConsulProducer<KeyValueClient> {

    public ConsulKeyValueProducer(ConsulEndpoint endpoint, ConsulConfiguration configuration) {
        super(endpoint, configuration, c -> c.keyValueClient());
    }

    @InvokeOnHeader(ConsulKeyValueActions.PUT)
    protected void put(Message message) throws Exception {
        message.setHeader(
            ConsulConstants.CONSUL_RESULT,
            getClient().putValue(
                getMandatoryKey(message),
                message.getBody(String.class),
                message.getHeader(ConsulConstants.CONSUL_FLAGS, 0L, Long.class),
                getOption(message, PutOptions.BLANK, PutOptions.class)
            )
        );
    }

    @InvokeOnHeader(ConsulKeyValueActions.GET_VALUE)
    protected void getValue(Message message) throws Exception {
        Object result;

        if (isValueAsString(message)) {
            result = getClient().getValueAsString(
                getMandatoryKey(message)
            ).orNull();
        } else {
            result = getClient().getValue(
                getMandatoryKey(message),
                getOption(message, QueryOptions.BLANK, QueryOptions.class)
            ).orNull();
        }

        setBodyAndResult(message, result);
    }

    @InvokeOnHeader(ConsulKeyValueActions.GET_VALUES)
    protected void getValues(Message message) throws Exception {
        Object result;

        if (isValueAsString(message)) {
            result = getClient().getValuesAsString(
                getMandatoryKey(message)
            );
        } else {
            result = getClient().getValues(
                getMandatoryKey(message),
                getOption(message, QueryOptions.BLANK, QueryOptions.class)
            );
        }

        setBodyAndResult(message, result);
    }

    @InvokeOnHeader(ConsulKeyValueActions.GET_KEYS)
    protected void getKeys(Message message) throws Exception {
        setBodyAndResult(message, getClient().getKeys(getMandatoryKey(message)));
    }

    @InvokeOnHeader(ConsulKeyValueActions.GET_SESSIONS)
    protected void getSessions(Message message) throws Exception {
        setBodyAndResult(message, getClient().getSession(getMandatoryKey(message)));
    }

    @InvokeOnHeader(ConsulKeyValueActions.DELETE_KEY)
    protected void deleteKey(Message message) throws Exception {
        getClient().deleteKey(getMandatoryKey(message));
        message.setHeader(ConsulConstants.CONSUL_RESULT, true);
    }

    @InvokeOnHeader(ConsulKeyValueActions.DELETE_KEYS)
    protected void deleteKeys(Message message) throws Exception {
        getClient().deleteKeys(getMandatoryKey(message));
        message.setHeader(ConsulConstants.CONSUL_RESULT, true);
    }

    @InvokeOnHeader(ConsulKeyValueActions.LOCK)
    protected void lock(Message message) throws Exception {
        message.setHeader(ConsulConstants.CONSUL_RESULT,
            getClient().acquireLock(
                getMandatoryKey(message),
                getBody(message, null, String.class),
                message.getHeader(ConsulConstants.CONSUL_SESSION, "", String.class)
            )
        );
    }

    @InvokeOnHeader(ConsulKeyValueActions.UNLOCK)
    protected void unlock(Message message) throws Exception {
        message.setHeader(ConsulConstants.CONSUL_RESULT,
            getClient().releaseLock(
                getMandatoryKey(message),
                getMandatoryHeader(message, ConsulConstants.CONSUL_SESSION, String.class)
            )
        );
    }
}
