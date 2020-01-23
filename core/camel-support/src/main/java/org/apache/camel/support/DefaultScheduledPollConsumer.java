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
package org.apache.camel.support;

import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Processor;
import org.apache.camel.support.service.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A default implementation of an event driven {@link org.apache.camel.Consumer} which uses the
 * {@link PollingConsumer}
 */
public class DefaultScheduledPollConsumer extends ScheduledPollConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultScheduledPollConsumer.class);

    private PollingConsumer pollingConsumer;
    private int timeout;

    public DefaultScheduledPollConsumer(DefaultEndpoint defaultEndpoint, Processor processor) {
        super(defaultEndpoint, processor);
    }

    public DefaultScheduledPollConsumer(Endpoint endpoint, Processor processor, ScheduledExecutorService executor) {
        super(endpoint, processor, executor);
    }

    @Override
    protected int poll() throws Exception {
        int messagesPolled = 0;

        while (isPollAllowed()) {
            Exchange exchange;
            if (timeout == 0) {
                exchange = pollingConsumer.receiveNoWait();
            } else if (timeout < 0) {
                exchange = pollingConsumer.receive();
            } else {
                exchange = pollingConsumer.receive(timeout);
            }

            if (exchange == null) {
                break;
            }

            messagesPolled++;
            LOG.trace("Polled {} {}", messagesPolled, exchange);

            // prepare for processing where message should be IN
            ExchangeHelper.prepareOutToIn(exchange);
            getProcessor().process(exchange);
        }

        return messagesPolled;
    }

    public int getTimeout() {
        return timeout;
    }

    /**
     * Sets a timeout to use with {@link PollingConsumer}.
     * <br/>
     * <br/>Use <tt>timeout < 0</tt> for {@link PollingConsumer#receive()}.
     * <br/>Use <tt>timeout == 0</tt> for {@link PollingConsumer#receiveNoWait()}.
     * <br/>Use <tt>timeout > 0</tt> for {@link PollingConsumer#receive(long)}}.
     * <br/> The default timeout value is <tt>0</tt>
     *
     * @param timeout the timeout value
     */
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    @Override
    protected void doStart() throws Exception {
        pollingConsumer = getEndpoint().createPollingConsumer();
        ServiceHelper.startService(pollingConsumer);
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(pollingConsumer);
        super.doStop();
    }
}
