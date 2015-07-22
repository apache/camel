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

import java.util.regex.Pattern;

import org.apache.camel.component.twitter.consumer.Twitter4JConsumer;
import org.apache.camel.component.twitter.consumer.directmessage.DirectMessageConsumer;
import org.apache.camel.component.twitter.consumer.search.SearchConsumer;
import org.apache.camel.component.twitter.consumer.streaming.FilterConsumer;
import org.apache.camel.component.twitter.consumer.streaming.SampleConsumer;
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
import org.apache.camel.component.twitter.producer.UserProducer;
import org.apache.camel.impl.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps the endpoint URI to the respective Twitter4J consumer or producer.
 * <p/>
 * URI STRUCTURE:
 * <p/>
 * timeline/
 * public
 * home
 * friends
 * user (ALSO A PRODUCER)
 * mentions
 * retweetsofme
 * user/
 * search users (DIRECT ONLY)
 * user suggestions (DIRECT ONLY)
 * trends/
 * daily
 * weekly
 * userlist
 * directmessage (ALSO A PRODUCER)
 * streaming/
 * filter (POLLING ONLY)
 * sample (POLLING ONLY)
 * user (POLLING ONLY)
 */
public final class Twitter4JFactory {

    private static final Logger LOG = LoggerFactory.getLogger(Twitter4JFactory.class);

    private Twitter4JFactory() {
        // helper class
    }

    public static Twitter4JConsumer getConsumer(TwitterEndpoint te, String uri) throws IllegalArgumentException {
        String[] uriSplit = splitUri(uri);

        if (uriSplit.length > 0) {
            switch (ConsumerType.fromUri(uriSplit[0])) {
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
                switch (StreamingType.fromUri(uriSplit[1])) {
                case SAMPLE:
                    return new SampleConsumer(te);
                case FILTER:
                    return new FilterConsumer(te);
                case USER:
                    return new UserStreamingConsumer(te);
                default:
                    break;
                }
                break;
            case TIMELINE:
                if (uriSplit.length > 1) {
                    switch (TimelineType.fromUri(uriSplit[1])) {
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

    public static DefaultProducer getProducer(TwitterEndpoint te, String uri) throws IllegalArgumentException {
        String[] uriSplit = splitUri(uri);

        if (uriSplit.length > 0) {
            switch (ConsumerType.fromUri(uriSplit[0])) {
            case DIRECTMESSAGE:
                if (te.getProperties().getUser() == null || te.getProperties().getUser().trim().isEmpty()) {
                    throw new IllegalArgumentException(
                            "Producer type set to DIRECT MESSAGE but no recipient user was set.");
                } else {
                    return new DirectMessageProducer(te);
                }
            case TIMELINE:
                if (uriSplit.length > 1) {
                    switch (TimelineType.fromUri(uriSplit[1])) {
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

    private static String[] splitUri(String uri) {
        Pattern p1 = Pattern.compile("twitter:(//)*");
        Pattern p2 = Pattern.compile("\\?.*");

        uri = p1.matcher(uri).replaceAll("");
        uri = p2.matcher(uri).replaceAll("");

        return uri.split("/");
    }
}
