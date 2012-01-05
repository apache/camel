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

import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.twitter.TwitterEndpoint;
import org.apache.camel.component.twitter.data.Status;
import org.apache.camel.impl.ScheduledPollConsumer;

/**
 * Provides a scheduled polling consumer
 * 
 */
public class TwitterConsumerPolling extends ScheduledPollConsumer implements TwitterConsumer {

    private Twitter4JConsumer twitter4jConsumer;

    private long lastStatusUpdateID = 1;

    private int delay;

    public TwitterConsumerPolling(TwitterEndpoint endpoint, Processor processor,
                                  Twitter4JConsumer twitter4jConsumer) {
        super(endpoint, processor);

        this.twitter4jConsumer = twitter4jConsumer;

        delay = endpoint.getProperties().getDelay();

        setInitialDelay(0);
        setDelay(delay);
        setTimeUnit(TimeUnit.SECONDS);
    }

    protected int poll() throws Exception {
        Iterator<Status> statusIterator = twitter4jConsumer.requestPollingStatus(lastStatusUpdateID);

        int total = 0;
        while (statusIterator.hasNext()) {
            Status tStatus = statusIterator.next();

            // if (tStatus.getId() <= lastStatusUpdateID) {
            // return 0;
            // }

            Exchange e = getEndpoint().createExchange();

            e.getIn().setHeader("screenName", tStatus.getUser().getScreenName());
            e.getIn().setHeader("date", tStatus.getDate());

            e.getIn().setBody(tStatus);

            getProcessor().process(e);

            total++;
            // make sure it ignores updates that were already polled
            long newerStatusID = tStatus.getId();
            if (newerStatusID > lastStatusUpdateID) {
                lastStatusUpdateID = newerStatusID;
            }
        }

        return total;
    }

    public long getLastStatusUpdateID() {
        return lastStatusUpdateID;
    }
}
