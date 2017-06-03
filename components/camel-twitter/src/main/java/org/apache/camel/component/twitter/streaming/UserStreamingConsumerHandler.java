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

import org.apache.camel.Exchange;
import org.apache.camel.component.twitter.TwitterConstants;
import org.apache.camel.component.twitter.TwitterEndpoint;
import org.apache.camel.component.twitter.TwitterHelper;
import org.apache.camel.component.twitter.consumer.TwitterEventType;
import twitter4j.DirectMessage;
import twitter4j.StallWarning;
import twitter4j.Status;
import twitter4j.User;
import twitter4j.UserList;
import twitter4j.UserStreamListener;

public class UserStreamingConsumerHandler extends AbstractStreamingConsumerHandler implements UserStreamListener {

    public UserStreamingConsumerHandler(TwitterEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void start() {
        getTwitterStream().user();
    }

    @Override
    public void onDeletionNotice(long directMessageId, long userId) {
        // noop
    }

    @Override
    public void onFriendList(long[] friendIds) {
        // noop
    }

    @Override
    public void onFavorite(User source, User target, Status favoritedStatus) {
        Exchange exchange = TwitterEventType.FAVORITE.createExchange(endpoint, favoritedStatus);
        TwitterHelper.setUserHeader(exchange, 1, source, "source");
        TwitterHelper.setUserHeader(exchange, 2, target, "target");

        onEvent(exchange);
    }

    @Override
    public void onUnfavorite(User source, User target, Status unfavoritedStatus) {
        Exchange exchange = TwitterEventType.UNFAVORITE.createExchange(endpoint, unfavoritedStatus);
        TwitterHelper.setUserHeader(exchange, 1, source, "source");
        TwitterHelper.setUserHeader(exchange, 2, target, "target");

        onEvent(exchange);
    }

    @Override
    public void onFollow(User source, User followedUser) {
        Exchange exchange = TwitterEventType.FOLLOW.createExchange(endpoint);
        TwitterHelper.setUserHeader(exchange, 1, source, "source");
        TwitterHelper.setUserHeader(exchange, 2, followedUser, "followed");

        onEvent(exchange);
    }

    @Override
    public void onUnfollow(User source, User unfollowedUser) {
        Exchange exchange = TwitterEventType.UNFOLLOW.createExchange(endpoint);
        TwitterHelper.setUserHeader(exchange, 1, source, "source");
        TwitterHelper.setUserHeader(exchange, 2, unfollowedUser, "unfollowed");

        onEvent(exchange);
    }

    @Override
    public void onDirectMessage(DirectMessage directMessage) {
        onEvent(TwitterEventType.DIRECT_MESSAGE.createExchange(endpoint, directMessage));
    }

    @Override
    public void onUserListMemberAddition(User addedMember, User listOwner, UserList list) {
        Exchange exchange = TwitterEventType.USERLIST_MEMBER_ADDITION.createExchange(endpoint, list);
        TwitterHelper.setUserHeader(exchange, 1, addedMember, "addedMember");
        TwitterHelper.setUserHeader(exchange, 2, listOwner, "listOwner");

        onEvent(exchange);
    }

    @Override
    public void onUserListMemberDeletion(User deletedMember, User listOwner, UserList list) {
        Exchange exchange = TwitterEventType.USERLIST_MEMBER_DELETION.createExchange(endpoint, list);
        TwitterHelper.setUserHeader(exchange, 1, deletedMember, "deletedMember");
        TwitterHelper.setUserHeader(exchange, 2, listOwner, "listOwner");

        onEvent(exchange);
    }

    @Override
    public void onUserListSubscription(User subscriber, User listOwner, UserList list) {
        Exchange exchange = TwitterEventType.USERLIST_SUBSCRIPTION.createExchange(endpoint, list);
        TwitterHelper.setUserHeader(exchange, 1, subscriber, "subscriber");
        TwitterHelper.setUserHeader(exchange, 2, listOwner, "listOwner");

        onEvent(exchange);
    }

    @Override
    public void onUserListUnsubscription(User subscriber, User listOwner, UserList list) {
        Exchange exchange = TwitterEventType.USERLIST_UNSUBSCRIPTION.createExchange(endpoint, list);
        TwitterHelper.setUserHeader(exchange, 1, subscriber, "subscriber");
        TwitterHelper.setUserHeader(exchange, 2, listOwner, "listOwner");

        onEvent(exchange);
    }

    @Override
    public void onUserListCreation(User user, UserList userList) {
        Exchange exchange = TwitterEventType.USERLIST_CREATION.createExchange(endpoint, userList);
        TwitterHelper.setUserHeader(exchange, user);

        onEvent(exchange);
    }

    @Override
    public void onUserListUpdate(User user, UserList userList) {
        Exchange exchange = TwitterEventType.USERLIST_UPDATE.createExchange(endpoint, userList);
        TwitterHelper.setUserHeader(exchange, user);

        onEvent(exchange);
    }

    @Override
    public void onUserListDeletion(User user, UserList userList) {
        Exchange exchange = TwitterEventType.USERLIST_DELETETION.createExchange(endpoint, userList);
        TwitterHelper.setUserHeader(exchange, user);

        onEvent(exchange);
    }

    @Override
    public void onUserProfileUpdate(User user) {
        Exchange exchange = TwitterEventType.USER_PROFILE_UPDATE.createExchange(endpoint);
        TwitterHelper.setUserHeader(exchange, user);

        onEvent(exchange);
    }

    @Override
    public void onUserSuspension(long suspendedUser) {
        Exchange exchange = TwitterEventType.USER_SUSPENSION.createExchange(endpoint);
        exchange.getIn().setHeader(TwitterConstants.TWITTER_USER, suspendedUser);

        onEvent(exchange);
    }

    @Override
    public void onUserDeletion(long deletedUser) {
        Exchange exchange = TwitterEventType.USER_DELETION.createExchange(endpoint);
        exchange.getIn().setHeader(TwitterConstants.TWITTER_USER, deletedUser);

        onEvent(exchange);
    }

    @Override
    public void onBlock(User source, User blockedUser) {
        Exchange exchange = TwitterEventType.BLOCK.createExchange(endpoint);
        TwitterHelper.setUserHeader(exchange, 1, source, "source");
        TwitterHelper.setUserHeader(exchange, 2, blockedUser, "blocked");

        onEvent(exchange);
    }

    @Override
    public void onUnblock(User source, User unblockedUser) {
        Exchange exchange = TwitterEventType.UNBLOCK.createExchange(endpoint);
        TwitterHelper.setUserHeader(exchange, 1, source, "source");
        TwitterHelper.setUserHeader(exchange, 2, unblockedUser, "unblocked");

        onEvent(exchange);
    }

    @Override
    public void onStallWarning(StallWarning stallWarning) {
        // noop
    }

    @Override
    public void onRetweetedRetweet(User source, User target, Status retweetedStatus) {
        Exchange exchange = TwitterEventType.RETWEETED_RETWEET.createExchange(endpoint, retweetedStatus);
        TwitterHelper.setUserHeader(exchange, 1, source, "source");
        TwitterHelper.setUserHeader(exchange, 2, target, "target");

        onEvent(exchange);
    }

    @Override
    public void onFavoritedRetweet(User source, User target, Status favoritedRetweeet) {
        Exchange exchange = TwitterEventType.FAVORITED_RETWEET.createExchange(endpoint, favoritedRetweeet);
        TwitterHelper.setUserHeader(exchange, 1, source, "source");
        TwitterHelper.setUserHeader(exchange, 2, target, "target");

        onEvent(exchange);
    }

    @Override
    public void onQuotedTweet(User source, User target, Status quotingTweet) {
        Exchange exchange = TwitterEventType.QUOTED_TWEET.createExchange(endpoint, quotingTweet);
        TwitterHelper.setUserHeader(exchange, 1, source, "source");
        TwitterHelper.setUserHeader(exchange, 2, target, "target");

        onEvent(exchange);
    }
}
