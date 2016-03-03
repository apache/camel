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

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import mousio.client.promises.ResponsePromise;
import mousio.etcd4j.requests.EtcdKeyGetRequest;
import mousio.etcd4j.responses.EtcdKeysResponse;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EtcdWatchConsumer extends AbstractEtcdConsumer implements ResponsePromise.IsSimplePromiseResponseHandler<EtcdKeysResponse> {
    private static final Logger LOGGER = LoggerFactory.getLogger(EtcdWatchConsumer.class);

    private final EtcdWatchEndpoint endpoint;
    private final EtcdConfiguration configuration;

    public EtcdWatchConsumer(EtcdWatchEndpoint endpoint, Processor processor, EtcdConfiguration configuration, EtcdNamespace namespace, String path) {
        super(endpoint, processor, configuration, namespace, path);

        this.endpoint = endpoint;
        this.configuration = configuration;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        watch();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
    }

    @Override
    public void onResponse(ResponsePromise<EtcdKeysResponse> promise) {
        if (!isRunAllowed()) {
            return;
        }

        try {
            EtcdKeysResponse response = promise.get();

            Exchange exchange = endpoint.createExchange();
            exchange.getIn().setHeader(EtcdConstants.ETCD_NAMESPACE, getNamespace());
            exchange.getIn().setHeader(EtcdConstants.ETCD_PATH, response.node.key);
            exchange.getIn().setBody(response);

            getProcessor().process(exchange);

            watch();
        } catch (TimeoutException e) {
            LOGGER.debug("Timeout watching for {}", getPath());

            if (configuration.isSendEmptyExchangeOnTimeout()) {
                Exchange exchange = endpoint.createExchange();
                try {
                    exchange.getIn().setHeader(EtcdConstants.ETCD_NAMESPACE, getNamespace());
                    exchange.getIn().setHeader(EtcdConstants.ETCD_TIMEOUT, true);
                    exchange.getIn().setHeader(EtcdConstants.ETCD_PATH, getPath());
                    exchange.getIn().setBody(null);

                    getProcessor().process(exchange);
                } catch (Exception e1) {
                    getExceptionHandler().handleException("Error processing exchange", exchange, e1);
                }
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    private void watch() throws Exception {
        if (!isRunAllowed()) {
            return;
        }

        EtcdKeyGetRequest request = getClient().get(getPath()).waitForChange();
        if (configuration.isRecursive()) {
            request.recursive();
        }
        if (configuration.getTimeout() != null) {
            request.timeout(configuration.getTimeout(), TimeUnit.MILLISECONDS);
        }

        try {
            request.send().addListener(this);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
