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
package org.apache.camel.component.twitter.mocks;

import twitter4j.ConnectionLifeCycleListener;
import twitter4j.FilterQuery;
import twitter4j.RateLimitStatusListener;
import twitter4j.SiteStreamsListener;
import twitter4j.StatusListener;
import twitter4j.StatusStream;
import twitter4j.TwitterException;
import twitter4j.TwitterStream;
import twitter4j.UserStream;
import twitter4j.UserStreamListener;
import twitter4j.auth.AccessToken;
import twitter4j.auth.Authorization;
import twitter4j.auth.RequestToken;
import twitter4j.conf.Configuration;

public class TwitterStreamMock implements TwitterStream {
    private StatusListener statusListener;

    public void updateStatus(String text) {
        statusListener.onStatus(new StatusMock(text));
    }

    @Override
    public void addConnectionLifeCycleListener(ConnectionLifeCycleListener listener) {

    }

    @Override
    public void addListener(UserStreamListener listener) {

    }

    @Override
    public void addListener(StatusListener listener) {
        this.statusListener = listener;
    }

    @Override
    public void addListener(SiteStreamsListener listener) {

    }

    @Override
    public void firehose(int count) {

    }

    @Override
    public StatusStream getFirehoseStream(int count) throws TwitterException {
        return null;
    }

    @Override
    public void links(int count) {

    }

    @Override
    public StatusStream getLinksStream(int count) throws TwitterException {
        return null;
    }

    @Override
    public void retweet() {

    }

    @Override
    public StatusStream getRetweetStream() throws TwitterException {
        return null;
    }

    @Override
    public void sample() {

    }

    @Override
    public StatusStream getSampleStream() throws TwitterException {
        return null;
    }

    @Override
    public void user() {

    }

    @Override
    public void user(String[] track) {

    }

    @Override
    public UserStream getUserStream() throws TwitterException {
        return null;
    }

    @Override
    public UserStream getUserStream(String[] track) throws TwitterException {
        return null;
    }

    @Override
    public void site(boolean withFollowings, long[] follow) {

    }

    @Override
    public void filter(FilterQuery query) {

    }

    @Override
    public StatusStream getFilterStream(FilterQuery query) throws TwitterException {
        return null;
    }

    @Override
    public void cleanUp() {

    }

    @Override
    public String getScreenName() throws TwitterException, IllegalStateException {
        return null;
    }

    @Override
    public long getId() throws TwitterException, IllegalStateException {
        return 0;
    }

    @Override
    public void addRateLimitStatusListener(RateLimitStatusListener listener) {

    }

    @Override
    public Authorization getAuthorization() {
        return null;
    }

    @Override
    public Configuration getConfiguration() {
        return null;
    }

    @Override
    public void shutdown() {

    }

    @Override
    public void setOAuthConsumer(String consumerKey, String consumerSecret) {

    }

    @Override
    public RequestToken getOAuthRequestToken() throws TwitterException {
        return null;
    }

    @Override
    public RequestToken getOAuthRequestToken(String callbackURL) throws TwitterException {
        return null;
    }

    @Override
    public RequestToken getOAuthRequestToken(String callbackURL, String xAuthAccessType)
        throws TwitterException {
        return null;
    }

    @Override
    public AccessToken getOAuthAccessToken() throws TwitterException {
        return null;
    }

    @Override
    public AccessToken getOAuthAccessToken(String oauthVerifier) throws TwitterException {
        return null;
    }

    @Override
    public AccessToken getOAuthAccessToken(RequestToken requestToken) throws TwitterException {
        return null;
    }

    @Override
    public AccessToken getOAuthAccessToken(RequestToken requestToken, String oauthVerifier)
        throws TwitterException {
        return null;
    }

    @Override
    public AccessToken getOAuthAccessToken(String screenName, String password) throws TwitterException {
        return null;
    }

    @Override
    public void setOAuthAccessToken(AccessToken accessToken) {

    }

}
