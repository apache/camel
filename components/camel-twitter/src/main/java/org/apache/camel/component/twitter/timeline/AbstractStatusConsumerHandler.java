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
package org.apache.camel.component.twitter.timeline;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.component.twitter.TwitterEndpoint;
import org.apache.camel.component.twitter.consumer.AbstractTwitterConsumerHandler;
import org.apache.camel.component.twitter.consumer.TwitterEventType;
import twitter4j.TwitterException;
import twitter4j.v1.Status;

/**
 * Consumes the user's home timeline.
 */
abstract class AbstractStatusConsumerHandler extends AbstractTwitterConsumerHandler {

    AbstractStatusConsumerHandler(TwitterEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public List<Exchange> pollConsume() throws TwitterException {
        List<Status> statusList = doPoll();
        for (Status status : doPoll()) {
            setLastIdIfGreater(status.getId());
        }

        return TwitterEventType.STATUS.createExchangeList(endpoint, statusList);
    }

    @Override
    public List<Exchange> directConsume() throws TwitterException {
        return TwitterEventType.STATUS.createExchangeList(endpoint, doDirect());
    }

    protected abstract List<Status> doPoll() throws TwitterException;

    protected abstract List<Status> doDirect() throws TwitterException;
}
