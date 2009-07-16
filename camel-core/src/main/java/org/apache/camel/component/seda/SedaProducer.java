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
package org.apache.camel.component.seda;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.WaitForTaskToComplete;
import org.apache.camel.impl.SynchronizationAdapter;
import org.apache.camel.util.ExchangeHelper;

/**
 * @version $Revision$
 */
public class SedaProducer extends CollectionProducer {
    private final SedaEndpoint endpoint;
    private final WaitForTaskToComplete waitForTaskToComplete;
    private final long timeout;

    public SedaProducer(SedaEndpoint endpoint, BlockingQueue<Exchange> queue, WaitForTaskToComplete waitForTaskToComplete, long timeout) {
        super(endpoint, queue);
        this.endpoint = endpoint;
        this.waitForTaskToComplete = waitForTaskToComplete;
        this.timeout = timeout;
    }

    @Override
    public void process(final Exchange exchange) throws Exception {
        // use a new copy of the exchange to route async and handover the on completion to the new copy
        // so its the new copy that performs the on completion callback when its done
        Exchange copy = exchange.copy(true);
        // set a new from endpoint to be the seda queue
        copy.setFromEndpoint(endpoint);

        WaitForTaskToComplete wait = waitForTaskToComplete;
        if (exchange.getProperty(Exchange.ASYNC_WAIT) != null) {
            wait = exchange.getProperty(Exchange.ASYNC_WAIT, WaitForTaskToComplete.class);
        }

        if (wait == WaitForTaskToComplete.Always
            || (wait == WaitForTaskToComplete.IfReplyExpected && ExchangeHelper.isOutCapable(exchange))) {

            // latch that waits until we are complete
            final CountDownLatch latch = new CountDownLatch(1);

            // we should wait for the reply so install a on completion so we know when its complete
            copy.addOnCompletion(new SynchronizationAdapter() {
                @Override
                public void onDone(Exchange response) {
                    try {
                        ExchangeHelper.copyResults(exchange, response);
                    } finally {
                        // always ensure latch is triggered
                        latch.countDown();
                    }
                }
            });

            queue.add(copy);
            // lets see if we can get the task done before the timeout
            boolean done = latch.await(timeout, TimeUnit.MILLISECONDS);
            if (!done) {
                exchange.setException(new ExchangeTimedOutException(exchange, timeout));
            }
        } else {
            // no wait, eg its a InOnly then just add to queue and return
            queue.add(copy);
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        endpoint.onStarted(this);
    }

    @Override
    protected void doStop() throws Exception {
        endpoint.onStopped(this);
        super.doStop();
    }
}
