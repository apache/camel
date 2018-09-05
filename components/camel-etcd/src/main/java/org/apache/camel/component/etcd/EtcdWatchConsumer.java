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
import java.util.concurrent.atomic.AtomicLong;

import mousio.client.promises.ResponsePromise;
import mousio.etcd4j.requests.EtcdKeyGetRequest;
import mousio.etcd4j.responses.EtcdException;
import mousio.etcd4j.responses.EtcdKeysResponse;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EtcdWatchConsumer extends AbstractEtcdConsumer implements ResponsePromise.IsSimplePromiseResponseHandler<EtcdKeysResponse> {
    private static final Logger LOGGER = LoggerFactory.getLogger(EtcdWatchConsumer.class);

    private final EtcdWatchEndpoint endpoint;
    private final EtcdConfiguration configuration;
    private final AtomicLong index;

    public EtcdWatchConsumer(EtcdWatchEndpoint endpoint, Processor processor, EtcdConfiguration configuration, EtcdNamespace namespace, String path) {
        super(endpoint, processor, configuration, namespace, path);

        this.endpoint = endpoint;
        this.configuration = configuration;
        this.index = new AtomicLong(configuration.getFromIndex());
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

        Exchange exchange = null;
        Throwable throwable = promise.getException();

        if (throwable instanceof EtcdException) {
            EtcdException exception = (EtcdException) throwable;
            // Etcd only keeps the responses of the most recent 1000 events
            // across all etcd keys so if we wait for a cleared index, we
            // get "index is outdated response" like:
            //
            // {
            //     "errorCode" : 401,
            //     "message"   : "The event in requested index is outdated and cleared",
            //     "cause"     : "the requested history has been cleared [1008/8]",
            //     "index"     : 2007
            // }
            //
            // So we set the index to the one returned by the exception + 1
            if (EtcdHelper.isOutdatedIndexException(exception)) {
                LOGGER.debug("Outdated index, key: {}, cause={}", getPath(), exception.etcdCause);

                // We set the index to the one returned by the exception + 1.
                index.set(exception.index + 1);

                // Clean-up the exception so it is not rethrown/handled
                throwable = null;
            }
        } else {
            try {
                EtcdKeysResponse response = promise.get();

                exchange = endpoint.createExchange();
                exchange.getIn().setHeader(EtcdConstants.ETCD_NAMESPACE, getNamespace());
                exchange.getIn().setHeader(EtcdConstants.ETCD_PATH, response.node.key);
                exchange.getIn().setBody(response);

                // Watch from the modifiedIndex + 1 of the node we got for ensuring
                // no events are missed between watch commands
                index.set(response.node.modifiedIndex + 1);
            } catch (TimeoutException e) {
                LOGGER.debug("Timeout watching for {}", getPath());

                if (configuration.isSendEmptyExchangeOnTimeout()) {
                    exchange = endpoint.createExchange();
                    exchange.getIn().setHeader(EtcdConstants.ETCD_NAMESPACE, getNamespace());
                    exchange.getIn().setHeader(EtcdConstants.ETCD_TIMEOUT, true);
                    exchange.getIn().setHeader(EtcdConstants.ETCD_PATH, getPath());
                    exchange.getIn().setBody(null);
                }

                throwable = null;
            } catch (Exception e1) {
                throwable = e1;
            }

            if (exchange != null) {
                try {
                    throwable = null;
                    getProcessor().process(exchange);
                } catch (Exception e) {
                    getExceptionHandler().handleException("Error processing exchange", exchange, e);
                }
            }
        }

        if (throwable != null) {
            handleException("Error processing etcd response", throwable);
        }

        try {
            watch();
        } catch (Exception e) {
            handleException("Error watching key " + getPath(), e);
        }
    }

    private void watch() throws Exception {
        if (!isRunAllowed()) {
            return;
        }

        EtcdKeyGetRequest request = getClient().get(getPath()).waitForChange(index.get());
        if (configuration.isRecursive()) {
            request.recursive();
        }
        if (configuration.getTimeout() != null) {
            request.timeout(configuration.getTimeout(), TimeUnit.MILLISECONDS);
        }

        request.send().addListener(this);
    }
}
