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

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.options.DeleteOption;
import io.etcd.jetcd.options.GetOption;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.StringHelper;

class Etcd3Producer extends DefaultProducer {

    private final Etcd3Configuration configuration;
    private final String path;
    private final Client client;
    private final KV kvClient;
    private final Charset defaultKeyCharset;
    private final Charset defaultValueCharset;

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
    public void process(Exchange exchange) throws Exception {
        String action = exchange.getIn().getHeader(Etcd3Constants.ETCD_ACTION, String.class);
        String targetPath = exchange.getIn().getHeader(Etcd3Constants.ETCD_PATH, String.class);
        if (targetPath == null) {
            targetPath = path;
        }

        StringHelper.notEmpty(targetPath, Etcd3Constants.ETCD_PATH);
        StringHelper.notEmpty(action, Etcd3Constants.ETCD_ACTION);

        switch (action) {
            case Etcd3Constants.ETCD_KEYS_ACTION_SET:
                processSet(targetPath, exchange);
                break;
            case Etcd3Constants.ETCD_KEYS_ACTION_GET:
                processGet(targetPath, exchange);
                break;
            case Etcd3Constants.ETCD_KEYS_ACTION_DELETE:
                processDel(targetPath, exchange);
                break;
            default:
                throw new IllegalArgumentException("Unknown action " + action);
        }
    }

    private void processDel(String targetPath, Exchange exchange) throws Exception {
        exchange.getIn().setBody(
                kvClient.delete(
                        ByteSequence.from(targetPath, getKeyCharset(exchange)),
                        DeleteOption.newBuilder().isPrefix(isPrefix(exchange)).build()).get());
    }

    private void processGet(String targetPath, Exchange exchange) throws Exception {
        exchange.getIn().setBody(
                kvClient.get(
                        ByteSequence.from(targetPath, getKeyCharset(exchange)),
                        GetOption.newBuilder().isPrefix(isPrefix(exchange)).build()).get());
    }

    private void processSet(String targetPath, Exchange exchange) throws Exception {
        exchange.getIn().setBody(
                kvClient.put(
                        ByteSequence.from(targetPath, getKeyCharset(exchange)),
                        ByteSequence.from(exchange.getIn().getBody(String.class), getValueCharset(exchange))).get());
    }

    private boolean isPrefix(Exchange exchange) {
        final Boolean isPrefix = exchange.getIn().getHeader(Etcd3Constants.ETCD_IS_PREFIX, Boolean.class);
        return isPrefix == null ? configuration.isPrefix() : isPrefix;
    }

    private Charset getKeyCharset(Exchange exchange) {
        final String charset = exchange.getIn().getHeader(Etcd3Constants.ETCD_KEY_CHARSET, String.class);
        return charset == null ? defaultKeyCharset : Charset.forName(charset);
    }

    private Charset getValueCharset(Exchange exchange) {
        final String charset = exchange.getIn().getHeader(Etcd3Constants.ETCD_VALUE_CHARSET, String.class);
        return charset == null ? defaultValueCharset : Charset.forName(charset);
    }

    @Override
    protected void doStop() throws Exception {
        try {
            client.close();
        } finally {
            super.doStop();
        }
    }
}
