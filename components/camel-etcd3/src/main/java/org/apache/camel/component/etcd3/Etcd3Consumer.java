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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KeyValue;
import io.etcd.jetcd.Watch;
import io.etcd.jetcd.options.WatchOption;
import io.etcd.jetcd.watch.WatchEvent;
import io.etcd.jetcd.watch.WatchResponse;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.util.StringHelper;

/**
 * A consumer allowing to watch key-value pairs stored into etcd v3.
 */
class Etcd3Consumer extends DefaultConsumer implements Watch.Listener {

    /**
     * The configuration of the consumer.
     */
    private final Etcd3Configuration configuration;
    /**
     * The path of the key-value pair to watch.
     */
    private final String path;
    /**
     * The client to access to etcd.
     */
    private final Client client;
    /**
     * The client to watch key-value pairs stored into etcd.
     */
    private final Watch watch;
    /**
     * The revision from which the changes of the key-value pair are watched.
     */
    private final AtomicLong revision;
    /**
     * The charset to use for the keys.
     */
    private final Charset keyCharset;
    /**
     * The current watcher used to watch the changes of the target key-value pair.
     */
    private final AtomicReference<Watch.Watcher> watcher = new AtomicReference<>();

    /**
     * Construct a {@code Etcd3Consumer} with the given parameters.
     *
     * @param endpoint      the endpoint corresponding to the consumer.
     * @param processor     the processor corresponding to the consumer.
     * @param configuration the configuration of the consumer.
     * @param path          the path of the key-value pair to watch.
     */
    Etcd3Consumer(Etcd3Endpoint endpoint, Processor processor, Etcd3Configuration configuration,
                  String path) {
        super(endpoint, processor);

        this.configuration = configuration;
        this.path = StringHelper.notEmpty(path, "path");
        this.client = configuration.createClient();
        this.watch = client.getWatchClient();
        this.revision = new AtomicLong(configuration.getFromIndex());
        this.keyCharset = Charset.forName(configuration.getKeyCharset());
    }

    @Override
    protected void doStart() throws Exception {
        doWatch();
        super.doStart();
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
     * If allowed, starts to watch the changes on the target key-value pair.
     */
    private void doWatch() {
        if (!isRunAllowed()) {
            return;
        }
        watcher.getAndUpdate(w -> {
            if (w != null) {
                w.close();
            }
            return watch.watch(
                    ByteSequence.from(path, keyCharset),
                    WatchOption.newBuilder().isPrefix(configuration.isPrefix()).withRevision(revision.get()).build(),
                    this);
        });
    }

    @Override
    public void onNext(WatchResponse response) {
        for (WatchEvent event : response.getEvents()) {
            final Exchange exchange = createExchange(false);
            final KeyValue keyValue = event.getKeyValue();
            exchange.getIn().setHeader(Etcd3Constants.ETCD_PATH, keyValue.getKey().toString(keyCharset));
            exchange.getIn().setBody(event);
            // Watch from the revision + 1 of the node we got for ensuring
            // no events are missed between watch commands
            revision.getAndUpdate(r -> Math.max(r, keyValue.getModRevision() + 1));
            try {
                getProcessor().process(exchange);
            } catch (Exception e) {
                getExceptionHandler().handleException("Error processing exchange", exchange, e);
            }
            releaseExchange(exchange, false);
        }
    }

    @Override
    public void onError(Throwable throwable) {
        handleException("Error processing etcd response", throwable);
    }

    @Override
    public void onCompleted() {
        doWatch();
    }
}
