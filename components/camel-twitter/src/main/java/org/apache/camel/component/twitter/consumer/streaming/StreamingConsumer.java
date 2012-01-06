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
package org.apache.camel.component.twitter.consumer.streaming;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.camel.component.twitter.TwitterEndpoint;
import org.apache.camel.component.twitter.consumer.Twitter4JConsumer;
import org.apache.camel.component.twitter.data.Status;
import org.apache.camel.component.twitter.util.TwitterConverter;

import twitter4j.StatusDeletionNotice;
import twitter4j.StatusListener;
import twitter4j.TwitterException;

/**
 * Super class providing consuming capabilities for the streaming API.
 * 
 */
public class StreamingConsumer implements Twitter4JConsumer, StatusListener {

    TwitterEndpoint te;
    private List<Status> receivedStatuses = new ArrayList<Status>();
    private boolean clear;

    public StreamingConsumer(TwitterEndpoint te) {
        this.te = te;
    }

    public Iterator<Status> requestPollingStatus(long lastStatusUpdateId) throws TwitterException {
        clear = true;
        return Collections.unmodifiableList(receivedStatuses).iterator();
    }

    public Iterator<Status> requestDirectStatus() throws TwitterException {
        // not used
        return null;
    }

    @Override
    public void onException(Exception ex) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onStatus(twitter4j.Status status) {
        if (clear) {
            receivedStatuses.clear();
            clear = false;
        }
        receivedStatuses.add(TwitterConverter.convertStatus(status));
    }

    @Override
    public void onDeletionNotice(StatusDeletionNotice statusDeletionNotice) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onTrackLimitationNotice(int numberOfLimitedStatuses) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onScrubGeo(long userId, long upToStatusId) {
        // TODO Auto-generated method stub

    }

}
