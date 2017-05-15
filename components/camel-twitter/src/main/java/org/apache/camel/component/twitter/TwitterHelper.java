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
package org.apache.camel.component.twitter;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.twitter.consumer.TwitterConsumer;
import org.apache.camel.component.twitter.consumer.directmessage.DirectMessageConsumer;
import org.apache.camel.component.twitter.consumer.search.SearchConsumer;
import org.apache.camel.component.twitter.consumer.streaming.FilterStreamingConsumer;
import org.apache.camel.component.twitter.consumer.streaming.SampleStreamingConsumer;
import org.apache.camel.component.twitter.consumer.streaming.UserStreamingConsumer;
import org.apache.camel.component.twitter.consumer.timeline.HomeConsumer;
import org.apache.camel.component.twitter.consumer.timeline.MentionsConsumer;
import org.apache.camel.component.twitter.consumer.timeline.RetweetsConsumer;
import org.apache.camel.component.twitter.consumer.timeline.UserConsumer;
import org.apache.camel.component.twitter.data.ConsumerType;
import org.apache.camel.component.twitter.data.StreamingType;
import org.apache.camel.component.twitter.data.TimelineType;
import org.apache.camel.component.twitter.producer.DirectMessageProducer;
import org.apache.camel.component.twitter.producer.SearchProducer;
import org.apache.camel.component.twitter.producer.TwitterProducer;
import org.apache.camel.component.twitter.producer.UserProducer;
import twitter4j.User;

public final class TwitterHelper {
    private TwitterHelper() {
    }

    public static void setUserHeader(Exchange exchange, User user) {
        setUserHeader(exchange.getIn(), user);
    }

    public static void setUserHeader(Message message, User user) {
        message.setHeader(TwitterConstants.TWITTER_USER, user);
    }

    public static void setUserHeader(Exchange exchange, int index, User user, String role) {
        setUserHeader(exchange.getIn(), index, user, role);
    }

    public static void setUserHeader(Message message, int index, User user, String role) {
        message.setHeader(TwitterConstants.TWITTER_USER + index, user);
        message.setHeader(TwitterConstants.TWITTER_USER_ROLE + index, role);
    }

    public static TwitterConsumer createConsumer(TwitterEndpoint te, String uri, String remaining) throws IllegalArgumentException {
        String[] tokens = remaining.split("/");

        if (tokens.length > 0) {
            switch (ConsumerType.fromString(tokens[0])) {
            case DIRECTMESSAGE:
                return new DirectMessageConsumer(te);
            case SEARCH:
                boolean hasNoKeywords = te.getProperties().getKeywords() == null
                    || te.getProperties().getKeywords().trim().isEmpty();
                if (hasNoKeywords) {
                    throw new IllegalArgumentException("Type set to SEARCH but no keywords were provided.");
                } else {
                    return new SearchConsumer(te);
                }
            case STREAMING:
                if (tokens.length > 1) {
                    switch (StreamingType.fromString(tokens[1])) {
                    case SAMPLE:
                        return new SampleStreamingConsumer(te);
                    case FILTER:
                        return new FilterStreamingConsumer(te);
                    case USER:
                        return new UserStreamingConsumer(te);
                    default:
                        break;
                    }
                }
                break;
            case TIMELINE:
                if (tokens.length > 1) {
                    switch (TimelineType.fromString(tokens[1])) {
                    case HOME:
                        return new HomeConsumer(te);
                    case MENTIONS:
                        return new MentionsConsumer(te);
                    case RETWEETSOFME:
                        return new RetweetsConsumer(te);
                    case USER:
                        if (te.getProperties().getUser() == null || te.getProperties().getUser().trim().isEmpty()) {
                            throw new IllegalArgumentException("Fetch type set to USER TIMELINE but no user was set.");
                        } else {
                            return new UserConsumer(te);
                        }
                    default:
                        break;
                    }
                }
                break;
            default:
                break;
            }
        }

        throw new IllegalArgumentException("Cannot create any consumer with uri " + uri
            + ". A consumer type was not provided (or an incorrect pairing was used).");
    }

    public static TwitterProducer createProducer(TwitterEndpoint te, String uri, String remaining) throws IllegalArgumentException {
        String[] tokens = remaining.split("/");

        if (tokens.length > 0) {
            switch (ConsumerType.fromString(tokens[0])) {
            case DIRECTMESSAGE:
                if (te.getProperties().getUser() == null || te.getProperties().getUser().trim().isEmpty()) {
                    throw new IllegalArgumentException(
                        "Producer type set to DIRECT MESSAGE but no recipient user was set.");
                } else {
                    return new DirectMessageProducer(te);
                }
            case TIMELINE:
                if (tokens.length > 1) {
                    switch (TimelineType.fromString(tokens[1])) {
                    case USER:
                        return new UserProducer(te);
                    default:
                        break;
                    }
                }
                break;
            case SEARCH:
                return new SearchProducer(te);
            default:
                break;
            }

        }

        throw new IllegalArgumentException("Cannot create any producer with uri " + uri
            + ". A producer type was not provided (or an incorrect pairing was used).");
    }

    public static <T extends Enum<T>> T enumFromString(T[] values, String uri, T defaultValue) {
        for (int i = values.length - 1; i >= 0; i--) {
            if (values[i].name().equalsIgnoreCase(uri)) {
                return values[i];
            }
        }

        return defaultValue;
    }
}
