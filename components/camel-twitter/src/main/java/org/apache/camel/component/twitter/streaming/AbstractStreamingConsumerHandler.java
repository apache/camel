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
package org.apache.camel.component.twitter.streaming;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.Exchange;
import org.apache.camel.Service;
import org.apache.camel.component.twitter.TwitterEndpoint;
import org.apache.camel.component.twitter.consumer.AbstractTwitterConsumerHandler;
import org.apache.camel.component.twitter.consumer.TwitterEventListener;
import org.apache.camel.component.twitter.consumer.TwitterEventType;
import twitter4j.Status;
import twitter4j.StatusDeletionNotice;
import twitter4j.StatusListener;
import twitter4j.TwitterException;
import twitter4j.TwitterStream;

/**
 * Super class providing consuming capabilities for the streaming API.
 */
public abstract class AbstractStreamingConsumerHandler extends AbstractTwitterConsumerHandler implements StatusListener, Service {
    private final TwitterStream twitterStream;
    private final List<Exchange> receivedStatuses;
    private final AtomicReference<TwitterEventListener> twitterEventListener;
    private boolean clear;

    public AbstractStreamingConsumerHandler(TwitterEndpoint te) {
        super(te);
        this.receivedStatuses = new ArrayList<>();
        this.twitterStream = te.getProperties().createTwitterStream();
        this.twitterStream.addListener(this);
        this.twitterEventListener = new AtomicReference<>();
        this.clear = true;
    }

    @Override
    public List<Exchange> pollConsume() throws TwitterException {
        List<Exchange> result;

        synchronized (receivedStatuses) {
            clear = true;
            result = Collections.unmodifiableList(new ArrayList<>(receivedStatuses));
        }

        return result;
    }

    @Override
    public List<Exchange> directConsume() throws TwitterException {
        return Collections.emptyList();
    }

    @Override
    public void onException(Exception ex) {
    }

    @Override
    public void onStatus(Status status) {
        onEvent(TwitterEventType.STATUS.createExchange(endpoint, status));
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

    public void setEventListener(TwitterEventListener tweeterStatusListener) {
        twitterEventListener.set(tweeterStatusListener);
    }

    public void removeEventListener(TwitterEventListener tweeterStatusListener) {
        twitterEventListener.compareAndSet(tweeterStatusListener, null);
    }

    @Override
    public void stop() {
        twitterStream.removeListener(this);
        twitterStream.shutdown();
        twitterStream.cleanUp();
    }

    protected TwitterStream getTwitterStream() {
        return twitterStream;
    }

    protected void onEvent(Exchange exchange) {
        TwitterEventListener listener = twitterEventListener.get();
        if (listener != null) {
            listener.onEvent(exchange);
        } else {
            synchronized (receivedStatuses) {
                if (clear) {
                    receivedStatuses.clear();
                    clear = false;
                }
                receivedStatuses.add(exchange);
            }
        }
    }
}
