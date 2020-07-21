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
package org.apache.camel.component.twitter;

import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.twitter.consumer.AbstractTwitterConsumerHandler;
import org.apache.camel.component.twitter.consumer.DefaultTwitterConsumer;
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

    public static <T extends Enum<T>> T enumFromString(T[] values, String uri, T defaultValue) {
        for (int i = values.length - 1; i >= 0; i--) {
            if (values[i].name().equalsIgnoreCase(uri)) {
                return values[i];
            }
        }

        return defaultValue;
    }
}
