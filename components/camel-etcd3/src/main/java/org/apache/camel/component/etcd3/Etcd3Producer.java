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
package org.apache.camel.component.etcd3;

import java.nio.charset.Charset;
import java.util.concurrent.CompletableFuture;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.options.DeleteOption;
import io.etcd.jetcd.options.GetOption;
import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.util.StringHelper;

/**
 * A producer allowing to get, delete and update key-value pairs from etcd v3 asynchronous.
 */
class Etcd3Producer extends DefaultAsyncProducer {

    /**
     * The configuration of the producer.
     */
    private final Etcd3Configuration configuration;
    /**
     * The default path against which the action is performed.
     */
    private final String path;
    /**
     * The client to access to etcd.
     */
    private final Client client;
    /**
     * The client to access to the key-value pairs stored into etcd.
     */
    private final KV kvClient;
    /**
     * The default charset to use for the keys.
     */
    private final Charset defaultKeyCharset;
    /**
     * The default charset to use for the values.
     */
    private final Charset defaultValueCharset;

    /**
     * Construct a {@code Etcd3Producer} with the given parameters.
     *
     * @param endpoint      the endpoint corresponding to the producer.
     * @param configuration the configuration of the producer.
     * @param path          the default path against which the action is performed.
     */
    Etcd3Producer(Etcd3Endpoint endpoint, Etcd3Configuration configuration, String path) {
        super(endpoint);
        this.configuration = configuration;
        this.path = path;
        this.client = configuration.createClient();
        this.kvClient = client.getKVClient();
        this.defaultKeyCharset = Charset.forName(configuration.getKeyCharset());
        this.defaultValueCharset = Charset.forName(configuration.getValueCharset());
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            String action = exchange.getIn().getHeader(Etcd3Constants.ETCD_ACTION, String.class);
            String targetPath = exchange.getIn().getHeader(Etcd3Constants.ETCD_PATH, String.class);
            if (targetPath == null) {
                targetPath = path;
            }

            StringHelper.notEmpty(targetPath, Etcd3Constants.ETCD_PATH);
            StringHelper.notEmpty(action, Etcd3Constants.ETCD_ACTION);

            switch (action) {
                case Etcd3Constants.ETCD_KEYS_ACTION_SET:
                    processSetAsync(targetPath, exchange, callback);
                    break;
                case Etcd3Constants.ETCD_KEYS_ACTION_GET:
                    processGetAsync(targetPath, exchange, callback);
                    break;
                case Etcd3Constants.ETCD_KEYS_ACTION_DELETE:
                    processDelAsync(targetPath, exchange, callback);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown action " + action);
            }
        } catch (Exception e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }
        return false;
    }

    @Override
    protected void doStop() throws Exception {
        try {
            client.close();
        } finally {
            super.doStop();
        }
    }

    /**
     * Add actions to perform once the given future is complete.
     *
     * @param future   the future to complete with specific actions.
     * @param exchange the exchange into which the result of the future (response or exception) is set.
     * @param callback the callback to call once the future is done.
     * @param <T>      the result type returned by the future.
     */
    private <T> void onComplete(CompletableFuture<T> future, Exchange exchange, AsyncCallback callback) {
        future.thenAccept(r -> exchange.getIn().setBody(r))
                .whenComplete(
                        (r, e) -> {
                            try {
                                if (e != null) {
                                    exchange.setException(new CamelExchangeException(
                                            "An error occurred while executing the action", exchange, e));
                                }
                            } finally {
                                callback.done(false);
                            }
                        });
    }

    /**
     * Deletes asynchronously the key-value pair corresponding to the given path.
     *
     * @param targetPath the key of the key-value pair to delete.
     * @param exchange   the exchange into which the result (delete response or exception) is set.
     * @param callback   the callback to call once the delete operation is done.
     */
    private void processDelAsync(String targetPath, Exchange exchange, AsyncCallback callback) {
        onComplete(
                kvClient.delete(
                        ByteSequence.from(targetPath, getKeyCharset(exchange)),
                        DeleteOption.newBuilder().isPrefix(isPrefix(exchange)).build()),
                exchange, callback);
    }

    /**
     * Gets asynchronously the key-value pair corresponding to the given path.
     *
     * @param targetPath the key of the key-value pair to get.
     * @param exchange   the exchange into which the result (get response or exception) is set.
     * @param callback   the callback to call once the get operation is done.
     */
    private void processGetAsync(String targetPath, Exchange exchange, AsyncCallback callback) {
        onComplete(
                kvClient.get(
                        ByteSequence.from(targetPath, getKeyCharset(exchange)),
                        GetOption.newBuilder().isPrefix(isPrefix(exchange)).build()),
                exchange, callback);
    }

    /**
     * Puts asynchronously a key-value pair with the given path as key and the message body as value.
     *
     * @param targetPath the key of the key-value pair to put.
     * @param exchange   the exchange from which the message body is extracted and into which the result (put response
     *                   or exception) is set.
     * @param callback   the callback to call once the put operation is done.
     */
    private void processSetAsync(String targetPath, Exchange exchange, AsyncCallback callback) {
        onComplete(
                kvClient.put(
                        ByteSequence.from(targetPath, getKeyCharset(exchange)),
                        ByteSequence.from(exchange.getIn().getBody(String.class), getValueCharset(exchange))),
                exchange, callback);
    }

    /**
     * Indicates whether the path given for the operation is a prefix or not.
     *
     * @param  exchange the exchange from which the value of the header {@link Etcd3Constants#ETCD_IS_PREFIX} is
     *                  extracted.
     * @return          the value of the header {@link Etcd3Constants#ETCD_IS_PREFIX} if it has been set, otherwise the
     *                  value extracted from the configuration.
     */
    private boolean isPrefix(Exchange exchange) {
        final Boolean isPrefix = exchange.getIn().getHeader(Etcd3Constants.ETCD_IS_PREFIX, Boolean.class);
        return isPrefix == null ? configuration.isPrefix() : isPrefix;
    }

    /**
     * Indicates the charset to use for the keys.
     *
     * @param  exchange the exchange from which the value of the header {@link Etcd3Constants#ETCD_KEY_CHARSET} is
     *                  extracted.
     * @return          the value of the header {@link Etcd3Constants#ETCD_KEY_CHARSET} if it has been set, otherwise
     *                  the value extracted from the configuration.
     */
    private Charset getKeyCharset(Exchange exchange) {
        final String charset = exchange.getIn().getHeader(Etcd3Constants.ETCD_KEY_CHARSET, String.class);
        return charset == null ? defaultKeyCharset : Charset.forName(charset);
    }

    /**
     * Indicates the charset to use for the values.
     *
     * @param  exchange the exchange from which the value of the header {@link Etcd3Constants#ETCD_VALUE_CHARSET} is
     *                  extracted.
     * @return          the value of the header {@link Etcd3Constants#ETCD_VALUE_CHARSET} if it has been set, otherwise
     *                  the value extracted from the configuration.
     */
    private Charset getValueCharset(Exchange exchange) {
        final String charset = exchange.getIn().getHeader(Etcd3Constants.ETCD_VALUE_CHARSET, String.class);
        return charset == null ? defaultValueCharset : Charset.forName(charset);
    }
}
