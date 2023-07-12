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
package org.apache.camel.component.twitter.consumer;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.component.twitter.TwitterEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.v1.Paging;

public abstract class AbstractTwitterConsumerHandler {

    protected final Logger log = LoggerFactory.getLogger(this.getClass());

    /**
     * Instance of TwitterEndpoint.
     */
    protected final TwitterEndpoint endpoint;

    /**
     * The last tweet ID received.
     */
    private long lastId;

    protected AbstractTwitterConsumerHandler(TwitterEndpoint endpoint) {
        this.endpoint = endpoint;
        this.lastId = -1;
    }

    /**
     * Called by polling consumers during each poll. It needs to be separate from directConsume() since, as an example,
     * to allow tweets to build up between polls.
     */
    public abstract List<Exchange> pollConsume() throws TwitterException;

    /**
     * Called by direct consumers.
     */
    public abstract List<Exchange> directConsume() throws TwitterException;

    /**
     * Can't assume that the end of the list will be the most recent ID. The Twitter API sometimes returns them slightly
     * out of order.
     */
    protected void setLastIdIfGreater(long newId) {
        if (newId > lastId) {
            lastId = newId;
        }
    }

    /**
     * Support to update the Consumer's lastId when starting the consumer
     */
    public void setLastId(long sinceId) {
        lastId = sinceId;
    }

    protected Twitter getTwitter() {
        return endpoint.getProperties().getTwitter();
    }

    protected long getLastId() {
        return lastId;
    }

    protected Paging getLastIdPaging() {
        Integer pages = endpoint.getProperties().getNumberOfPages();
        Integer count = endpoint.getProperties().getCount();
        if (pages != null && count != null) {
            return Paging.ofPage(pages).count(count).sinceId(lastId);
        } else if (pages != null) {
            return Paging.ofPage(pages).sinceId(lastId);
        } else {
            return Paging.ofSinceId(lastId);
        }
    }
}
