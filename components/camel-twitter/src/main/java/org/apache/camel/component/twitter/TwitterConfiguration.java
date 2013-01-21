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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import twitter4j.Twitter;
import twitter4j.TwitterFactory;
import twitter4j.TwitterStream;
import twitter4j.TwitterStreamFactory;
import twitter4j.conf.Configuration;
import twitter4j.conf.ConfigurationBuilder;

public class TwitterConfiguration {

    /**
     * OAuth
     */
    private String consumerKey;
    private String consumerSecret;
    private String accessToken;
    private String accessTokenSecret;

    /**
     * Defines the Twitter API endpoint.
     */
    private String type;

    /**
     * Polling delay.
     */
    private int delay = 60;

    /**
     * Username -- used for searching, etc.
     */
    private String user;

    /**
     * Keywords used for search and filters.
     */
    private String keywords;

    /**
     * Lon/Lat bounding boxes used for filtering.
     */
    private String locations;

    /**
     * List of userIds used for searching, etc.
     */
    private String userIds;

    /**
     * Filter out old tweets that have been previously polled.
     */
    private boolean filterOld = true;

    /**
     * Used for time-based endpoints (trends, etc.)
     */
    private String date;
    
    /**
     * Used to set the sinceId from pulling
     */
    private long sinceId  = 1;

    /**
     * Used ot set the preferred language on which to search
     */
    private String lang;
    
    private Date parsedDate;
    private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

    /**
     * Singleton, on demand instances of Twitter4J's Twitter & TwitterStream.
     * This should not be created by an endpoint's doStart(), etc., since
     * instances of twitter and/or twitterStream can be supplied by the route
     * itself.  Further, as an example, we don't want to initialize twitter
     * if we only need twitterStream.
     */
    private Twitter twitter;
    private TwitterStream twitterStream;

    /**
     * Ensures required fields are available.
     */
    public void checkComplete() {
        if (twitter == null && twitterStream == null
                && (consumerKey.isEmpty() || consumerSecret.isEmpty() || accessToken.isEmpty() || accessTokenSecret.isEmpty())) {
            throw new IllegalArgumentException("twitter or twitterStream or all of consumerKey, consumerSecret, accessToken, and accessTokenSecret must be set!");
        }
    }

    /**
     * Builds a Twitter4J Configuration using the OAuth params.
     *
     * @return Configuration
     */
    public Configuration getConfiguration() {
        ConfigurationBuilder confBuilder = new ConfigurationBuilder();
        confBuilder.setOAuthConsumerKey(consumerKey);
        confBuilder.setOAuthConsumerSecret(consumerSecret);
        confBuilder.setOAuthAccessToken(accessToken);
        confBuilder.setOAuthAccessTokenSecret(accessTokenSecret);
        return confBuilder.build();
    }

    public String getConsumerKey() {
        return consumerKey;
    }

    public void setConsumerKey(String consumerKey) {
        this.consumerKey = consumerKey;
    }

    public String getConsumerSecret() {
        return consumerSecret;
    }

    public void setConsumerSecret(String consumerSecret) {
        this.consumerSecret = consumerSecret;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getAccessTokenSecret() {
        return accessTokenSecret;
    }

    public void setAccessTokenSecret(String accessTokenSecret) {
        this.accessTokenSecret = accessTokenSecret;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getKeywords() {
        return keywords;
    }

    public void setKeywords(String keywords) {
        this.keywords = keywords;
    }

    public int getDelay() {
        return delay;
    }

    public void setDelay(int delay) {
        this.delay = delay;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getLocations() {
        return locations;
    }

    public void setLocations(String locations) {
        this.locations = locations;
    }

    public String getUserIds() {
        return userIds;
    }

    public void setUserIds(String userIds) {
        this.userIds = userIds;
    }

    public boolean isFilterOld() {
        return filterOld;
    }

    public void setFilterOld(boolean filterOld) {
        this.filterOld = filterOld;
    }

    public Twitter getTwitter() {
        if (twitter == null) {
            twitter = new TwitterFactory(getConfiguration()).getInstance();
        }
        return twitter;
    }

    public void setTwitter(Twitter twitter) {
        this.twitter = twitter;
    }

    public TwitterStream getTwitterStream() {
        return twitterStream;
    }

    public void setTwitterStream(TwitterStream twitterStream) {
        this.twitterStream = twitterStream;
    }

    public String getDate() {
        return date;
    }

    public Date parseDate() {
        return parsedDate;
    }

    public void setDate(String date) {
        this.date = date;
        try {
            parsedDate = sdf.parse(date);
        } catch (ParseException e) {
            throw new IllegalArgumentException("date must be in yyyy-mm-dd format!");
        }
    }

    public TwitterStream createTwitterStream() {
        if (twitterStream == null) {
            twitterStream = new TwitterStreamFactory(getConfiguration()).getInstance();
        }

        return twitterStream;
    }
    
    public long getSinceId() {
        return sinceId;
    }

    public void setSinceId(long sinceId) {
        this.sinceId = sinceId;
    }

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }
}



