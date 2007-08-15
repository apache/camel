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
package org.apache.camel.component.activemq;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.activemq.kaha.impl.async.AsyncDataManager;
import org.apache.activemq.kaha.impl.async.Location;
import org.apache.activemq.util.ByteSequence;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultProducer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class JournalEndpoint extends DefaultEndpoint<DefaultExchange> {

    private static final transient Log LOG = LogFactory.getLog(JournalEndpoint.class);

    public final class JournalProducer extends DefaultProducer<DefaultExchange> {
        private boolean syncWrite = JournalEndpoint.this.syncWrite;

        public JournalProducer(Endpoint<DefaultExchange> endpoint) {
            super(endpoint);
        }

        public void process(Exchange exchange) throws Exception {
            incrementReference();
            try {

                ByteSequence body = exchange.getIn().getBody(ByteSequence.class);
                if (body == null) {
                    byte[] bytes = exchange.getIn().getBody(byte[].class);
                    if (bytes != null) {
                        body = new ByteSequence(bytes);
                    }
                }
                if (body == null) {
                    throw new CamelExchangeException("In body message could not be converted to a ByteSequence or a byte array.", exchange);
                }
                dataManager.write(body, true);

            } finally {
                decrementReference();
            }
        }

        public boolean isSyncWrite() {
            return syncWrite;
        }

        public void setSyncWrite(boolean syncWrite) {
            this.syncWrite = syncWrite;
        }
    }

    private final File directory;
    private final AtomicReference<DefaultConsumer<DefaultExchange>> consumer = new AtomicReference<DefaultConsumer<DefaultExchange>>();
    private final Object activationMutex = new Object();
    private int referenceCount;
    private AsyncDataManager dataManager;
    private Thread thread;
    private Location lastReadLocation;
    private boolean syncWrite = true;
    private long idleDelay = 1000;

    public JournalEndpoint(String uri, JournalComponent journalComponent, File directory) {
        super(uri, journalComponent.getCamelContext());
        this.directory = directory;
    }

    public DefaultExchange createExchange() {
        return new DefaultExchange(getContext());
    }

    public boolean isSingleton() {
        return true;
    }

    public File getDirectory() {
        return directory;
    }

    public Consumer<DefaultExchange> createConsumer(Processor processor) throws Exception {
        return new DefaultConsumer<DefaultExchange>(this, processor) {
            @Override
            public void start() throws Exception {
                super.start();
                activateConsumer(this);
            }

            @Override
            public void stop() throws Exception {
                deactivateConsumer(this);
                super.stop();
            }
        };
    }

    protected void decrementReference() throws IOException {
        synchronized (activationMutex) {
            referenceCount--;
            if (referenceCount == 0) {
                LOG.debug("Closing data manager: " + directory);
                LOG.debug("Last mark at: " + lastReadLocation);
                dataManager.close();
                dataManager = null;
            }
        }
    }

    protected void incrementReference() throws IOException {
        synchronized (activationMutex) {
            referenceCount++;
            if (referenceCount == 1) {
                LOG.debug("Opening data manager: " + directory);
                dataManager = new AsyncDataManager();
                dataManager.setDirectory(directory);
                dataManager.start();

                lastReadLocation = dataManager.getMark();
                LOG.debug("Last mark at: " + lastReadLocation);
            }
        }
    }

    protected void deactivateConsumer(DefaultConsumer<DefaultExchange> consumer) throws IOException {
        synchronized (activationMutex) {
            if (this.consumer.get() != consumer) {
                throw new RuntimeCamelException("Consumer was not active.");
            }
            this.consumer.set(null);
            try {
                thread.join();
            } catch (InterruptedException e) {
                throw new InterruptedIOException();
            }
            decrementReference();
        }
    }

    protected void activateConsumer(DefaultConsumer<DefaultExchange> consumer) throws IOException {
        synchronized (activationMutex) {
            if (this.consumer.get() != null) {
                throw new RuntimeCamelException("Consumer already active: journal endpoints only support 1 active consumer");
            }
            incrementReference();
            this.consumer.set(consumer);
            thread = new Thread() {
                @Override
                public void run() {
                    dispatchToConsumer();
                }
            };
            thread.setName("Dipatch thread: " + getEndpointUri());
            thread.setDaemon(true);
            thread.start();
        }
    }

    protected void dispatchToConsumer() {
        try {
            DefaultConsumer<DefaultExchange> consumer;
            while ((consumer = this.consumer.get()) != null) {
                // See if there is a new record to process
                Location location = dataManager.getNextLocation(lastReadLocation);
                if (location != null) {

                    // Send it on.
                    ByteSequence read = dataManager.read(location);
                    DefaultExchange exchange = createExchange();
                    exchange.getIn().setBody(read);
                    exchange.getIn().setHeader("journal", getEndpointUri());
                    exchange.getIn().setHeader("location", location);
                    consumer.getProcessor().process(exchange);

                    // Setting the mark makes the data manager forget about
                    // everything
                    // before that record.
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Consumed record at: " + location);
                    }
                    dataManager.setMark(location, true);
                    lastReadLocation = location;
                } else {
                    // Avoid a tight CPU loop if there is no new record to read.
                    LOG.debug("Sleeping due to no records being available.");
                    Thread.sleep(idleDelay);
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public Producer<DefaultExchange> createProducer() throws Exception {
        return new JournalProducer(this);
    }

    public boolean isSyncWrite() {
        return syncWrite;
    }

    public void setSyncWrite(boolean syncWrite) {
        this.syncWrite = syncWrite;
    }

}
