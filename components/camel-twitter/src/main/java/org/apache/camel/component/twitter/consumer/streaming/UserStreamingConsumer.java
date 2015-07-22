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

import org.apache.camel.component.twitter.TwitterEndpoint;
import twitter4j.DirectMessage;
import twitter4j.StallWarning;
import twitter4j.Status;
import twitter4j.User;
import twitter4j.UserList;
import twitter4j.UserStreamListener;

public class UserStreamingConsumer extends StreamingConsumer implements UserStreamListener {

    public UserStreamingConsumer(TwitterEndpoint te) {
        super(te);
    }

    @Override
    protected void startStreaming() {
        twitterStream.user();
    }

    @Override
    public void onDeletionNotice(long l, long l2) {
        // noop
    }

    @Override
    public void onFriendList(long[] longs) {
        // noop
    }

    @Override
    public void onFavorite(User user, User user2, Status status) {
        // noop
    }

    @Override
    public void onUnfavorite(User user, User user2, Status status) {
        // noop
    }

    @Override
    public void onFollow(User user, User user2) {
        // noop
    }

    @Override
    public void onUnfollow(User user, User user2) {
        // noop
    }

    @Override
    public void onDirectMessage(DirectMessage directMessage) {
        // noop
    }

    @Override
    public void onUserListMemberAddition(User user, User user2, UserList userList) {
        // noop
    }

    @Override
    public void onUserListMemberDeletion(User user, User user2, UserList userList) {
        // noop
    }

    @Override
    public void onUserListSubscription(User user, User user2, UserList userList) {
        // noop
    }

    @Override
    public void onUserListUnsubscription(User user, User user2, UserList userList) {
        // noop
    }

    @Override
    public void onUserListCreation(User user, UserList userList) {
        // noop
    }

    @Override
    public void onUserListUpdate(User user, UserList userList) {
        // noop
    }

    @Override
    public void onUserListDeletion(User user, UserList userList) {
        // noop
    }

    @Override
    public void onUserProfileUpdate(User user) {
        // noop
    }

    @Override
    public void onUserSuspension(long l) {
        // noop
    }

    @Override
    public void onUserDeletion(long l) {
        // noop
    }

    @Override
    public void onBlock(User user, User user2) {
        // noop
    }

    @Override
    public void onUnblock(User user, User user2) {
        // noop
    }

    @Override
    public void onStallWarning(StallWarning stallWarning) {
        // noop
    }

    @Override
    public void onRetweetedRetweet(User source, User target, Status retweetedStatus) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void onFavoritedRetweet(User source, User target, Status favoritedRetweeet) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void onQuotedTweet(User source, User target, Status quotingTweet) {
        // TODO Auto-generated method stub
        
    }

}
