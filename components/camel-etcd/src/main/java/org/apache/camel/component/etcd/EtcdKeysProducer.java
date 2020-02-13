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
package org.apache.camel.component.etcd;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import mousio.etcd4j.EtcdClient;
import mousio.etcd4j.requests.EtcdKeyDeleteRequest;
import mousio.etcd4j.requests.EtcdKeyGetRequest;
import mousio.etcd4j.requests.EtcdKeyPutRequest;
import mousio.etcd4j.requests.EtcdRequest;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.util.StringHelper;

public class EtcdKeysProducer extends AbstractEtcdProducer {
    private final EtcdConfiguration configuration;

    public EtcdKeysProducer(EtcdKeysEndpoint endpoint, EtcdConfiguration configuration, String path) {
        super(endpoint, configuration, path);

        this.configuration = configuration;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String action = exchange.getIn().getHeader(EtcdConstants.ETCD_ACTION, String.class);
        String path = exchange.getIn().getHeader(EtcdConstants.ETCD_PATH, String.class);
        if (path == null) {
            path = getPath();
        }

        StringHelper.notEmpty(path, EtcdConstants.ETCD_PATH);
        StringHelper.notEmpty(action, EtcdConstants.ETCD_ACTION);

        switch(action) {
            case EtcdConstants.ETCD_KEYS_ACTION_SET:
                processSet(getClient(), path, exchange);
                break;
            case EtcdConstants.ETCD_KEYS_ACTION_GET:
                processGet(getClient(), path, exchange);
                break;
            case EtcdConstants.ETCD_KEYS_ACTION_DELETE:
                processDel(getClient(), path, false, exchange);
                break;
            case EtcdConstants.ETCD_KEYS_ACTION_DELETE_DIR:
                processDel(getClient(), path, true, exchange);
                break;
            default:
                throw new IllegalArgumentException("Unknown action " + action);
        }
    }

    // *************************************************************************
    // Processors
    // *************************************************************************

    private void processSet(EtcdClient client, String path, Exchange exchange) throws Exception {
        EtcdKeyPutRequest request = client.put(path, exchange.getIn().getBody(String.class));
        setRequestTimeToLive(request, exchange);
        setRequestTimeout(request, exchange);

        try {
            exchange.getIn().setHeader(EtcdConstants.ETCD_NAMESPACE, "keys");
            exchange.getIn().setBody(request.send().get());
        } catch (TimeoutException e) {
            throw new ExchangeTimedOutException(exchange, configuration.getTimeout());
        }
    }

    private void processGet(EtcdClient client, String path, Exchange exchange) throws Exception {
        EtcdKeyGetRequest request = client.get(path);
        setRequestTimeout(request, exchange);
        setRequestRecursive(request, exchange);

        try {
            exchange.getIn().setHeader(EtcdConstants.ETCD_NAMESPACE, "keys");
            exchange.getIn().setBody(request.send().get());
        } catch (TimeoutException e) {
            throw new ExchangeTimedOutException(exchange, configuration.getTimeout());
        }
    }

    private void processDel(EtcdClient client, String path, boolean dir, Exchange exchange) throws Exception {
        EtcdKeyDeleteRequest request = client.delete(path);
        setRequestTimeout(request, exchange);
        setRequestRecursive(request, exchange);

        if (dir) {
            request.dir();
        }

        try {
            exchange.getIn().setHeader(EtcdConstants.ETCD_NAMESPACE, "keys");
            exchange.getIn().setBody(request.send().get());
        } catch (TimeoutException e) {
            throw new ExchangeTimedOutException(exchange, configuration.getTimeout());
        }
    }

    private void setRequestTimeout(EtcdRequest<?> request, Exchange exchange) {
        Long timeout = exchange.getIn().getHeader(EtcdConstants.ETCD_TIMEOUT, Long.class);
        if (timeout != null) {
            request.timeout(timeout, TimeUnit.MILLISECONDS);
        } else if (configuration.getTimeout() != null) {
            request.timeout(configuration.getTimeout(), TimeUnit.MILLISECONDS);
        }
    }

    private void setRequestTimeToLive(EtcdKeyPutRequest request, Exchange exchange) {
        Integer ttl = exchange.getIn().getHeader(EtcdConstants.ETCD_TTL, Integer.class);
        if (ttl != null) {
            request.ttl(ttl);
        } else if (configuration.getTimeToLive() != null) {
            request.ttl(configuration.getTimeToLive());
        }
    }

    private void setRequestRecursive(EtcdKeyGetRequest request, Exchange exchange) {
        if (isRecursive(exchange)) {
            request.recursive();
        }
    }

    private void setRequestRecursive(EtcdKeyDeleteRequest request, Exchange exchange) {
        if (isRecursive(exchange)) {
            request.recursive();
        }
    }

    private boolean isRecursive(Exchange exchange) {
        Boolean recursive = exchange.getIn().getHeader(EtcdConstants.ETCD_RECURSIVE, Boolean.class);
        if (recursive != null) {
            return recursive;
        }

        return configuration.isRecursive();
    }
}
