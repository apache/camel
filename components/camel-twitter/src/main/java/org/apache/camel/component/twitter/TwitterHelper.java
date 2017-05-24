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

import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.twitter.consumer.AbstractTwitterConsumerHandler;
import org.apache.camel.component.twitter.consumer.DefaultTwitterConsumer;
import org.apache.camel.component.twitter.consumer.TwitterConsumerDirect;
import org.apache.camel.component.twitter.consumer.TwitterConsumerEvent;
import org.apache.camel.component.twitter.consumer.TwitterConsumerPolling;
import org.apache.camel.component.twitter.data.ConsumerType;
import org.apache.camel.component.twitter.data.StreamingType;
import org.apache.camel.component.twitter.data.TimelineType;
import org.apache.camel.component.twitter.directmessage.DirectMessageConsumerHandler;
import org.apache.camel.component.twitter.directmessage.DirectMessageProducer;
import org.apache.camel.component.twitter.search.SearchConsumerHandler;
import org.apache.camel.component.twitter.search.SearchProducer;
import org.apache.camel.component.twitter.streaming.FilterStreamingConsumerHandler;
import org.apache.camel.component.twitter.streaming.SampleStreamingConsumerHandler;
import org.apache.camel.component.twitter.streaming.UserStreamingConsumerHandler;
import org.apache.camel.component.twitter.timeline.HomeConsumerHandler;
import org.apache.camel.component.twitter.timeline.MentionsConsumerHandler;
import org.apache.camel.component.twitter.timeline.RetweetsConsumerHandler;
import org.apache.camel.component.twitter.timeline.UserConsumerHandler;
import org.apache.camel.component.twitter.timeline.UserProducer;

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

    @Deprecated
    public static AbstractTwitterConsumerHandler createConsumer(CommonPropertiesTwitterEndpoint te, String uri, String remaining) throws IllegalArgumentException {
        String[] tokens = remaining.split("/");
        
        if (tokens.length > 0) {
            switch (ConsumerType.fromString(tokens[0])) {
            case DIRECTMESSAGE:
                return new DirectMessageConsumerHandler(te);
            case SEARCH:
                boolean hasNoKeywords = te.getKeywords() == null
                    || te.getKeywords().trim().isEmpty();
                if (hasNoKeywords) {
                    throw new IllegalArgumentException("Type set to SEARCH but no keywords were provided.");
                } else {
                    return new SearchConsumerHandler(te, te.getKeywords());
                }
            case STREAMING:
                if (tokens.length > 1) {
                    switch (StreamingType.fromString(tokens[1])) {
                    case SAMPLE:
                        return new SampleStreamingConsumerHandler(te);
                    case FILTER:
                        return new FilterStreamingConsumerHandler(te, te.getKeywords());
                    case USER:
                        return new UserStreamingConsumerHandler(te);
                    default:
                        break;
                    }
                }
                break;
            case TIMELINE:
                if (tokens.length > 1) {
                    switch (TimelineType.fromString(tokens[1])) {
                    case HOME:
                        return new HomeConsumerHandler(te);
                    case MENTIONS:
                        return new MentionsConsumerHandler(te);
                    case RETWEETSOFME:
                        return new RetweetsConsumerHandler(te);
                    case USER:
                        if (te.getUser() == null || te.getUser().trim().isEmpty()) {
                            throw new IllegalArgumentException("Fetch type set to USER TIMELINE but no user was set.");
                        } else {
                            return new UserConsumerHandler(te, te.getUser());
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

    public static Consumer createConsumer(Processor processor, AbstractTwitterEndpoint endpoint, AbstractTwitterConsumerHandler handler) throws Exception {
        Consumer answer = new DefaultTwitterConsumer(endpoint, processor, handler);
        switch (endpoint.getEndpointType()) {
        case POLLING:
            handler.setLastId(endpoint.getProperties().getSinceId());
            endpoint.configureConsumer(answer);
            break;
        case DIRECT:
            endpoint.configureConsumer(answer);
            break;
        default:
            break;
        }
        return answer;
    }

    @Deprecated
    public static Producer createProducer(CommonPropertiesTwitterEndpoint te, String uri, String remaining) throws IllegalArgumentException {
        String[] tokens = remaining.split("/");

        if (tokens.length > 0) {
            switch (ConsumerType.fromString(tokens[0])) {
            case DIRECTMESSAGE:
                if (te.getUser() == null || te.getUser().trim().isEmpty()) {
                    throw new IllegalArgumentException(
                        "Producer type set to DIRECT MESSAGE but no recipient user was set.");
                } else {
                    return new DirectMessageProducer(te, te.getUser());
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
                return new SearchProducer(te, te.getKeywords());
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
