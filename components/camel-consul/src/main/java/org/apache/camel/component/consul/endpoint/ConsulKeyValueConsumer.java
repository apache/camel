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
package org.apache.camel.component.consul.endpoint;

import java.util.List;

import com.google.common.base.Optional;
import com.orbitz.consul.Consul;
import com.orbitz.consul.KeyValueClient;
import com.orbitz.consul.async.ConsulResponseCallback;
import com.orbitz.consul.model.ConsulResponse;
import com.orbitz.consul.model.kv.Value;
import com.orbitz.consul.option.QueryOptions;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.consul.ConsulConfiguration;
import org.apache.camel.component.consul.ConsulConstants;
import org.apache.camel.component.consul.ConsulEndpoint;

public final class ConsulKeyValueConsumer extends AbstractConsulConsumer<KeyValueClient> {

    public ConsulKeyValueConsumer(ConsulEndpoint endpoint, ConsulConfiguration configuration, Processor processor) {
        super(endpoint, configuration, processor, Consul::keyValueClient);
    }

    @Override
    protected Runnable createWatcher(KeyValueClient client) throws Exception {
        return configuration.isRecursive() ? new RecursivePathWatcher(client) : new PathWatcher(client);
    }

    // *************************************************************************
    // Watch
    // *************************************************************************

    private abstract class AbstractPathWatcher<T> extends AbstractWatcher implements ConsulResponseCallback<T> {
        protected AbstractPathWatcher(KeyValueClient client) {
            super(client);
        }

        protected QueryOptions queryOptions() {
            return QueryOptions.blockSeconds(configuration.getBlockSeconds(), index.get()).build();
        }

        @Override
        public void onComplete(ConsulResponse<T> consulResponse) {
            if (isRunAllowed()) {
                onResponse(consulResponse.getResponse());
                setIndex(consulResponse.getIndex());
                watch();
            }
        }

        @Override
        public void onFailure(Throwable throwable) {
            onError(throwable);
        }

        protected void onValue(Value value) {
            final Exchange exchange = endpoint.createExchange();
            final Message message = exchange.getIn();

            message.setHeader(ConsulConstants.CONSUL_KEY, value.getKey());
            message.setHeader(ConsulConstants.CONSUL_RESULT, true);
            message.setHeader(ConsulConstants.CONSUL_FLAGS, value.getFlags());
            message.setHeader(ConsulConstants.CONSUL_CREATE_INDEX, value.getCreateIndex());
            message.setHeader(ConsulConstants.CONSUL_LOCK_INDEX, value.getLockIndex());
            message.setHeader(ConsulConstants.CONSUL_MODIFY_INDEX, value.getModifyIndex());

            if (value.getSession().isPresent()) {
                message.setHeader(ConsulConstants.CONSUL_SESSION, value.getSession().get());
            }

            message.setBody(configuration.isValueAsString() ? value.getValueAsString().orNull() : value.getValue().orNull());

            try {
                getProcessor().process(exchange);
            } catch (Exception e) {
                getExceptionHandler().handleException("Error processing exchange", exchange, e);
            }
        }

        protected abstract void onResponse(T consulResponse);
    }

    private class PathWatcher extends AbstractPathWatcher<Optional<Value>> {
        PathWatcher(KeyValueClient client) {
            super(client);
        }

        @Override
        public void watch(KeyValueClient client) {
            client.getValue(key, queryOptions(), this);
        }

        @Override
        public void onResponse(Optional<Value> value) {
            if (value.isPresent()) {
                onValue(value.get());
            }
        }
    }

    private class RecursivePathWatcher extends AbstractPathWatcher<List<Value>> {
        RecursivePathWatcher(KeyValueClient client) {
            super(client);
        }

        @Override
        public void watch(KeyValueClient client) {
            client.getValues(key, queryOptions(), this);
        }

        @Override
        public void onResponse(List<Value> values) {
            values.forEach(this::onValue);
        }
    }
}
