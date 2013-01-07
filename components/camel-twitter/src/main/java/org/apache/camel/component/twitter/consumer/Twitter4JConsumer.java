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

import java.io.Serializable;
import java.util.List;

import org.apache.camel.component.twitter.TwitterEndpoint;

import twitter4j.TwitterException;


public abstract class Twitter4JConsumer {

    /**
     * Instance of TwitterEndpoint.
     */
    protected TwitterEndpoint te;

    /**
     * The last tweet ID received.
     */
    protected long lastId = 1;

    protected Twitter4JConsumer(TwitterEndpoint te) {
        this.te = te;
    }

    /**
     * Can't assume that the end of the list will be the most recent ID.
     * The Twitter API sometimes returns them slightly out of order.
     */
    protected void checkLastId(long newId) {
        if (newId > lastId) {
            lastId = newId;
        }
    }

    /**
     * Called by polling consumers during each poll.  It needs to be separate
     * from directConsume() since, as an example, streaming API polling allows
     * tweets to build up between polls.
     */
    public abstract List<? extends Serializable> pollConsume() throws TwitterException;

    /**
     * Called by direct consumers.
     */
    public abstract List<? extends Serializable> directConsume() throws TwitterException;
    
    /**
     * Support to update the Consumer's lastId when starting the consumer
     * @param sinceId
     */
    public void setLastId(long sinceId) {
        lastId = sinceId;
    }
}
