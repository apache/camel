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
package org.apache.camel.component.etcd;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import mousio.etcd4j.EtcdClient;
import mousio.etcd4j.requests.EtcdKeyDeleteRequest;
import mousio.etcd4j.requests.EtcdKeyGetRequest;
import mousio.etcd4j.requests.EtcdKeyPutRequest;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.util.ObjectHelper;


class EtcdKeysProducer extends AbstractEtcdProducer {
    private final EtcdKeysConfiguration configuration;
    private final String defaultPath;

    EtcdKeysProducer(EtcdKeysEndpoint endpoint, EtcdKeysConfiguration configuration, EtcdNamespace namespace, String path) {
        super(endpoint, configuration, namespace, path);

        this.configuration = configuration;
        this.defaultPath = endpoint.getRemainingPath(configuration.getPath());
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String action = exchange.getIn().getHeader(EtcdConstants.ETCD_ACTION, String.class);
        String path = exchange.getIn().getHeader(EtcdConstants.ETCD_PATH, String.class);
        if (path == null) {
            path = defaultPath;
        }

        ObjectHelper.notEmpty(path, EtcdConstants.ETCD_PATH);
        ObjectHelper.notEmpty(action, EtcdConstants.ETCD_ACTION);

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
        if (configuration.hasTimeToLive()) {
            request.ttl(configuration.getTimeToLive());
        }
        if (configuration.hasTimeout()) {
            request.timeout(configuration.getTimeout(), TimeUnit.MILLISECONDS);
        }

        try {
            exchange.getIn().setBody(request.send().get());
        } catch (TimeoutException e) {
            throw new ExchangeTimedOutException(exchange, configuration.getTimeout());
        }
    }

    private void processGet(EtcdClient client, String path, Exchange exchange) throws Exception {
        EtcdKeyGetRequest request = client.get(path);
        if (configuration.hasTimeout()) {
            request.timeout(configuration.getTimeout(), TimeUnit.MILLISECONDS);
        }
        if (configuration.isRecursive()) {
            request.recursive();
        }

        try {
            exchange.getIn().setBody(request.send().get());
        } catch (TimeoutException e) {
            throw new ExchangeTimedOutException(exchange, configuration.getTimeout());
        }
    }

    private void processDel(EtcdClient client, String path, boolean dir, Exchange exchange) throws Exception {
        EtcdKeyDeleteRequest request = client.delete(path);
        if (configuration.hasTimeout()) {
            request.timeout(configuration.getTimeout(), TimeUnit.MILLISECONDS);
        }
        if (configuration.isRecursive()) {
            request.recursive();
        }
        if (dir) {
            request.dir();
        }

        try {
            exchange.getIn().setBody(request.send().get());
        } catch (TimeoutException e) {
            throw new ExchangeTimedOutException(exchange, configuration.getTimeout());
        }
    }
}
