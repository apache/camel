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
package org.apache.camel.component.twitter.consumer;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.twitter.TwitterEndpoint;
import org.apache.camel.component.twitter.TwitterEndpointPolling;
import org.apache.camel.component.twitter.streaming.AbstractStreamingConsumerHandler;
import org.apache.camel.impl.ScheduledPollConsumer;

/**
 * Provides a scheduled polling consumer
 */
@Deprecated
public class TwitterConsumerPolling extends ScheduledPollConsumer {

    public static final long DEFAULT_CONSUMER_DELAY = 30 * 1000L;
    private final AbstractTwitterConsumerHandler twitter4jConsumer;

    public TwitterConsumerPolling(TwitterEndpoint endpoint, Processor processor, AbstractTwitterConsumerHandler twitter4jConsumer) {
        super(endpoint, processor);
        setDelay(DEFAULT_CONSUMER_DELAY);
        this.twitter4jConsumer = twitter4jConsumer;
    }

    @Override
    public TwitterEndpointPolling getEndpoint() {
        return (TwitterEndpointPolling) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (twitter4jConsumer instanceof AbstractStreamingConsumerHandler) {
            ((AbstractStreamingConsumerHandler) twitter4jConsumer).start();
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (twitter4jConsumer instanceof AbstractStreamingConsumerHandler) {
            ((AbstractStreamingConsumerHandler) twitter4jConsumer).stop();
        }

        super.doStop();
    }

    @Override
    protected int poll() throws Exception {
        List<Exchange> exchanges = twitter4jConsumer.pollConsume();

        int index = 0;
        for (; index < exchanges.size(); index++) {
            getProcessor().process(exchanges.get(index));
        }

        return index;
    }

}
