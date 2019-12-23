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
package org.apache.camel.component.feed;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.ScheduledPollConsumer;

/**
 * Base class for consuming feeds.
 */
public abstract class FeedPollingConsumer extends ScheduledPollConsumer {
    public static final long DEFAULT_CONSUMER_DELAY = 60 * 1000L;
    protected final FeedEndpoint endpoint;

    public FeedPollingConsumer(FeedEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    protected int poll() throws Exception {
        Object feed = createFeed();
        if (feed != null) {
            Exchange exchange = endpoint.createExchange(feed);
            getProcessor().process(exchange);
            return 1;
        } else {
            return 0;
        }
    }

    protected abstract Object createFeed() throws Exception;

}
