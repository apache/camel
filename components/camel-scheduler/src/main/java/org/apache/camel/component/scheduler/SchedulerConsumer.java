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
package org.apache.camel.component.scheduler;

import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.support.ScheduledPollConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SchedulerConsumer extends ScheduledPollConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(SchedulerConsumer.class);

    public SchedulerConsumer(SchedulerEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    protected HealthCheck.State initialHealthCheckState() {
        // the scheduler should be regarded as healthy on startup
        return HealthCheck.State.UP;
    }

    @Override
    public SchedulerEndpoint getEndpoint() {
        return (SchedulerEndpoint) super.getEndpoint();
    }

    @Override
    protected int poll() throws Exception {
        return sendTimerExchange();
    }

    protected int sendTimerExchange() {
        final Exchange exchange = createExchange(false);

        if (getEndpoint().isIncludeMetadata()) {
            exchange.setProperty(Exchange.TIMER_NAME, getEndpoint().getName());

            Date now = new Date();
            exchange.setProperty(Exchange.TIMER_FIRED_TIME, now);
            exchange.getIn().setHeader(SchedulerConstants.MESSAGE_TIMESTAMP, now.getTime());
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("Timer {} is firing", getEndpoint().getName());
        }

        if (!getEndpoint().isSynchronous()) {
            final AtomicBoolean polled = new AtomicBoolean(true);
            boolean doneSync = getAsyncProcessor().process(exchange, cbDoneSync -> {
                // handle any thrown exception
                if (exchange.getException() != null) {
                    getExceptionHandler().handleException("Error processing exchange", exchange, exchange.getException());
                }
                boolean wasPolled = exchange.getProperty(Exchange.SCHEDULER_POLLED_MESSAGES, true, boolean.class);
                if (!wasPolled) {
                    polled.set(false);
                }

                // sync wil release outside this callback
                if (!cbDoneSync) {
                    releaseExchange(exchange, false);
                }
            });
            if (!doneSync) {
                return polled.get() ? 1 : 0;
            }
        } else {
            try {
                getProcessor().process(exchange);
            } catch (Exception e) {
                exchange.setException(e);
            }

            // handle any thrown exception
            if (exchange.getException() != null) {
                getExceptionHandler().handleException("Error processing exchange", exchange, exchange.getException());
            }
        }

        // a property can be used to control if the scheduler polled a message or not
        // for example to overrule and indicate no message was polled, which can affect the scheduler
        // to leverage backoff on idle etc.
        boolean polled = exchange.getProperty(Exchange.SCHEDULER_POLLED_MESSAGES, true, boolean.class);
        releaseExchange(exchange, false);
        return polled ? 1 : 0;
    }

    @Override
    protected void doStart() throws Exception {
        getEndpoint().onConsumerStart(this);

        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        getEndpoint().onConsumerStop(this);

        super.doStop();
    }
}
