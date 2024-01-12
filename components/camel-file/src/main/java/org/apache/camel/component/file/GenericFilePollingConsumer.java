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
package org.apache.camel.component.file;

import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.PollingConsumerPollStrategy;
import org.apache.camel.support.EventDrivenPollingConsumer;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GenericFilePollingConsumer extends EventDrivenPollingConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(GenericFilePollingConsumer.class);
    private final long delay;

    public GenericFilePollingConsumer(GenericFileEndpoint endpoint) {
        super(endpoint);
        this.delay = endpoint.getDelay() > 0 ? endpoint.getDelay() : endpoint.getDefaultDelay();
    }

    @Override
    protected Consumer createConsumer() throws Exception {
        // lets add ourselves as a consumer
        GenericFileConsumer consumer = (GenericFileConsumer) super.createConsumer();
        // do not start scheduler as we poll manually
        consumer.setStartScheduler(false);
        // when using polling consumer we poll only 1 file per poll so we can
        // limit
        consumer.setMaxMessagesPerPoll(1);
        // however do not limit eager as we may sort the files and thus need to
        // do a full scan so we can sort afterwards
        consumer.setEagerLimitMaxMessagesPerPoll(false);
        // we only want to poll once so disconnect by default
        return consumer;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        // ensure consumer is started
        ServiceHelper.startService(getConsumer());
    }

    @Override
    protected GenericFileConsumer getConsumer() {
        return (GenericFileConsumer) super.getConsumer();
    }

    @Override
    public Exchange receiveNoWait() {
        if (LOG.isTraceEnabled()) {
            LOG.trace("receiveNoWait polling file: {}", getConsumer().getEndpoint());
        }
        int polled = doReceive(0);
        if (polled > 0) {
            return super.receive(0);
        } else {
            return null;
        }
    }

    @Override
    public Exchange receive() {
        if (LOG.isTraceEnabled()) {
            LOG.trace("receive polling file: {}", getConsumer().getEndpoint());
        }
        int polled = doReceive(Long.MAX_VALUE);
        if (polled > 0) {
            return super.receive();
        } else {
            return null;
        }
    }

    @Override
    public Exchange receive(long timeout) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("receive({}) polling file: {}", timeout, getConsumer().getEndpoint());
        }
        int polled = doReceive(timeout);
        if (polled > 0) {
            return super.receive(timeout);
        } else {
            return null;
        }
    }

    protected int doReceive(long timeout) {
        int retryCounter = -1;
        boolean done = false;
        Throwable cause = null;
        int polledMessages = 0;
        PollingConsumerPollStrategy pollStrategy = getConsumer().getPollStrategy();
        boolean sendEmptyMessageWhenIdle = getConsumer().isSendEmptyMessageWhenIdle();
        StopWatch watch = new StopWatch();

        while (!done) {
            try {
                cause = null;
                // eager assume we are done
                done = true;
                if (isRunAllowed()) {

                    if (retryCounter == -1) {
                        LOG.trace("Starting to poll: {}", this.getEndpoint());
                    } else {
                        LOG.debug("Retrying attempt {} to poll: {}", retryCounter, this.getEndpoint());
                    }

                    // mark we are polling which should also include the
                    // begin/poll/commit
                    boolean begin = pollStrategy.begin(getConsumer(), getEndpoint());
                    if (begin) {
                        retryCounter++;
                        polledMessages = getConsumer().poll();
                        LOG.trace("Polled {} messages", polledMessages);

                        if (polledMessages == 0 && sendEmptyMessageWhenIdle) {
                            // send an "empty" exchange
                            processEmptyMessage();
                            // set polledMessages=1 since the empty message is queued
                            polledMessages = 1;
                        } else if (polledMessages == 0 && timeout > 0) {
                            // if we did not poll a file and we are using
                            // timeout then try to poll again
                            done = false;
                        }

                        pollStrategy.commit(getConsumer(), getEndpoint(), polledMessages);
                    } else {
                        LOG.debug("Cannot begin polling as pollStrategy returned false: {}", pollStrategy);
                    }
                }

                LOG.trace("Finished polling: {}", this.getEndpoint());
            } catch (Exception e) {
                try {
                    boolean retry = pollStrategy.rollback(getConsumer(), getEndpoint(), retryCounter, e);
                    if (retry) {
                        // do not set cause as we retry
                        done = false;
                    } else {
                        cause = e;
                        done = true;
                    }
                } catch (Exception t) {
                    cause = t;
                    done = true;
                }
            }

            if (!done && timeout > 0) {
                // prepare for next attempt until we hit timeout
                long left = timeout - watch.taken();
                long min = Math.min(left, delay);
                if (min > 0) {
                    try {
                        // sleep for next pool
                        sleep(min);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    // timeout hit
                    done = true;
                }
            }
        }

        if (cause != null) {
            throw RuntimeCamelException.wrapRuntimeCamelException(cause);
        }

        return polledMessages;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Object name = exchange.getIn().getHeader(FileConstants.FILE_NAME);
        if (name != null) {
            LOG.debug("Received file: {}", name);
        }
        super.process(exchange);
    }

    /**
     * No messages to poll so send an empty message instead.
     *
     * @throws Exception is thrown if error processing the empty message.
     */
    protected void processEmptyMessage() throws Exception {
        Exchange exchange = getEndpoint().createExchange();
        LOG.debug("Sending empty message as there were no messages from polling: {}", this.getEndpoint());
        process(exchange);
    }

    private void sleep(long delay) throws InterruptedException {
        if (delay <= 0) {
            return;
        }
        LOG.trace("Sleeping for: {} millis", delay);
        Thread.sleep(delay);
    }

}
