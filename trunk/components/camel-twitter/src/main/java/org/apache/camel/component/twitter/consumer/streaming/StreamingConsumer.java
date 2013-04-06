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
import java.util.List;

import org.apache.camel.component.twitter.TwitterEndpoint;
import org.apache.camel.component.twitter.consumer.TweeterStatusListener;
import org.apache.camel.component.twitter.consumer.Twitter4JConsumer;

import twitter4j.Status;
import twitter4j.StatusDeletionNotice;
import twitter4j.StatusListener;
import twitter4j.TwitterException;
import twitter4j.TwitterStream;

/**
 * Super class providing consuming capabilities for the streaming API.
 */
public abstract class StreamingConsumer extends Twitter4JConsumer implements StatusListener {
    protected final TwitterStream twitterStream;
    private final List<Status> receivedStatuses = new ArrayList<Status>();
    private volatile boolean clear;
    private TweeterStatusListener tweeterStatusListener;

    public StreamingConsumer(TwitterEndpoint te) {
        super(te);
        twitterStream = te.getProperties().createTwitterStream();
        twitterStream.addListener(this);
    }

    public List<Status> pollConsume() throws TwitterException {
        clear = true;
        return Collections.unmodifiableList(new ArrayList<Status>(receivedStatuses));
    }

    public List<Status> directConsume() throws TwitterException {
        // not used
        return null;
    }

    @Override
    public void onException(Exception ex) {
    }

    @Override
    public void onStatus(Status status) {
        if (tweeterStatusListener != null) {
            tweeterStatusListener.onStatus(status);
        } else {
            if (clear) {
                receivedStatuses.clear();
                clear = false;
            }
            receivedStatuses.add(status);
        }
    }

    @Override
    public void onDeletionNotice(StatusDeletionNotice statusDeletionNotice) {
        // noop
    }

    @Override
    public void onTrackLimitationNotice(int numberOfLimitedStatuses) {
        // noop
    }

    @Override
    public void onScrubGeo(long userId, long upToStatusId) {
        // noop
    }

    public void registerTweetListener(TweeterStatusListener tweeterStatusListener) {
        this.tweeterStatusListener = tweeterStatusListener;
    }

    public void unregisterTweetListener(TweeterStatusListener tweeterStatusListener) {
        this.tweeterStatusListener = null;
    }

    public void doStart() {
        startStreaming();
    }

    public void doStop() {
        twitterStream.shutdown();
    }

    protected abstract void startStreaming();

}
