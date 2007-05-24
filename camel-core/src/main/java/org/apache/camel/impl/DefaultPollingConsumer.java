/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.impl;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.PollingConsumer;
import org.apache.camel.processor.Logger;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A default implementation of the {@link PollingConsumer} which uses the normal asynchronous consumer mechanism
 * along with a {@link BlockingQueue} to allow the caller to pull messages on demand.
 * 
 * @version $Revision: 1.1 $
 */
public class DefaultPollingConsumer<E extends Exchange> extends PollingConsumerSupport<E> implements Processor {
    private static final transient Log log = LogFactory.getLog(DefaultPollingConsumer.class);
    private BlockingQueue<E> queue;
    private ExceptionHandler interuptedExceptionHandler = new LoggingExceptionHandler(new Logger(log));
    private Consumer<E> consumer;

    public DefaultPollingConsumer(Endpoint<E> endpoint) {
        this(endpoint, new ArrayBlockingQueue<E>(1000));
    }

    public DefaultPollingConsumer(Endpoint<E> endpoint, BlockingQueue<E> queue) {
        super(endpoint);
        this.queue = queue;
    }

    public E receiveNoWait() {
        return receive(0);
    }

    public E receive() {
        while (!isStopping() && !isStopped()) {
            try {
                return queue.take();
            }
            catch (InterruptedException e) {
                handleInteruptedException(e);
            }
        }
        return null;
    }

    public E receive(long timeout) {
        try {
            return queue.poll(timeout, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException e) {
            handleInteruptedException(e);
            return null;
        }
    }

    public void process(Exchange exchange) throws Exception {
        queue.offer((E) exchange);
    }

    public ExceptionHandler getInteruptedExceptionHandler() {
        return interuptedExceptionHandler;
    }

    public void setInteruptedExceptionHandler(ExceptionHandler interuptedExceptionHandler) {
        this.interuptedExceptionHandler = interuptedExceptionHandler;
    }

    protected void handleInteruptedException(InterruptedException e) {
        getInteruptedExceptionHandler().handleException(e);
    }

    protected void doStart() throws Exception {
        // lets add ourselves as a consumer
        consumer = getEndpoint().createConsumer(this);
        consumer.start();
    }

    protected void doStop() throws Exception {
        if (consumer != null) {
            try {
                consumer.stop();
            }
            finally {
                consumer = null;
            }
        }
    }
}
