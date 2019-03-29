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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.component.twitter.TwitterConstants;
import org.apache.camel.component.twitter.TwitterEndpoint;
import org.apache.camel.component.twitter.util.TwitterSorter;

public enum TwitterEventType {
    STATUS,
    DIRECT_MESSAGE,
    FAVORITE,
    UNFAVORITE,
    FOLLOW,
    UNFOLLOW,
    USERLIST_MEMBER_ADDITION,
    USERLIST_MEMBER_DELETION,
    USERLIST_SUBSCRIPTION,
    USERLIST_UNSUBSCRIPTION,
    USERLIST_CREATION,
    USERLIST_UPDATE,
    USERLIST_DELETETION,
    USER_PROFILE_UPDATE,
    USER_SUSPENSION,
    USER_DELETION,
    BLOCK,
    UNBLOCK,
    RETWEETED_RETWEET,
    FAVORITED_RETWEET,
    QUOTED_TWEET;

    public Exchange createExchange(TwitterEndpoint endpoint) {
        return createExchange(endpoint, null);
    }

    public <T> Exchange createExchange(TwitterEndpoint endpoint, T body) {
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setHeader(TwitterConstants.TWITTER_EVENT_TYPE, name());

        if (body != null) {
            exchange.getIn().setBody(body);
        }

        return exchange;
    }

    public <T> List<Exchange> createExchangeList(TwitterEndpoint endpoint, List<T> bodyList) {
        List<Exchange> exchanges = Collections.emptyList();

        if (bodyList != null && !bodyList.isEmpty()) {
            exchanges = new ArrayList<>(bodyList.size());
            for (int i = 0; i < bodyList.size(); i++) {
                exchanges.add(createExchange(endpoint, bodyList.get(i)));
            }
        }

        if (!exchanges.isEmpty() && endpoint.getProperties().isSortById()) {
            exchanges = TwitterSorter.sortByStatusId(exchanges);
        }

        return exchanges;
    }
}
