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

import org.apache.camel.component.twitter.data.EndpointType;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.util.ObjectHelper;
import twitter4j.Twitter;
import twitter4j.TwitterFactory;
import twitter4j.conf.Configuration;
import twitter4j.conf.ConfigurationBuilder;

@UriParams
public class TwitterConfiguration {

    @UriParam(label = "consumer", defaultValue = "polling", enums = "polling,direct")
    private EndpointType type = EndpointType.POLLING;
    @UriParam(label = "security", secret = true)
    private String accessToken;
    @UriParam(label = "security", secret = true)
    private String accessTokenSecret;
    @UriParam(label = "security", secret = true)
    private String consumerKey;
    @UriParam(label = "security", secret = true)
    private String consumerSecret;
    @UriParam(label = "consumer,filter")
    private String userIds;
    @UriParam(label = "consumer,filter", defaultValue = "true")
    private boolean filterOld = true;
    @UriParam(label = "consumer,filter", defaultValue = "1")
    private long sinceId  = 1;
    @UriParam(label = "consumer,filter")
    private String lang;
    @UriParam(label = "consumer,filter", defaultValue = "5")
    private Integer count = 5;
    @UriParam(label = "consumer,filter", defaultValue = "1")
    private Integer numberOfPages = 1;
    @UriParam(label = "consumer,sort", defaultValue = "true")
    private boolean sortById = true;
    @UriParam(label = "proxy")
    private String httpProxyHost;
    @UriParam(label = "proxy")
    private String httpProxyUser;
    @UriParam(label = "proxy")
    private String httpProxyPassword;
    @UriParam(label = "proxy")
    private Integer httpProxyPort;
    @UriParam(label = "consumer,advanced")
    private String locations;
    @UriParam(label = "consumer,advanced")
    private Double latitude;
    @UriParam(label = "consumer,advanced")
    private Double longitude;
    @UriParam(label = "consumer,advanced")
    private Double radius;
    @UriParam(label = "consumer,advanced", defaultValue = "km", enums = "km,mi")
    private String distanceMetric;
    @UriParam(label = "consumer,advanced", defaultValue = "true")
    private boolean extendedMode = true;

    /**
     * Singleton, on demand instances of Twitter4J's Twitter.
     * This should not be created by an endpoint's doStart(), etc., since
     * instances of twitter can be supplied by the route
     * itself.
     */
    private Twitter twitter;

    /**
     * Ensures required fields are available.
     */
    public void checkComplete() {
        if (twitter == null 
            && (ObjectHelper.isEmpty(consumerKey) || ObjectHelper.isEmpty(consumerSecret) || ObjectHelper.isEmpty(accessToken) ||  ObjectHelper.isEmpty(accessTokenSecret))) {
            throw new IllegalArgumentException("twitter or all of consumerKey, consumerSecret, accessToken, and accessTokenSecret must be set!");
        }
    }

    /**
     * Builds a Twitter4J Configuration using the OAuth params.
     *
     * @return Configuration
     */
    public Configuration getConfiguration() {
        checkComplete();
        ConfigurationBuilder confBuilder = new ConfigurationBuilder();
        confBuilder.setOAuthConsumerKey(consumerKey);
        confBuilder.setOAuthConsumerSecret(consumerSecret);
        confBuilder.setOAuthAccessToken(accessToken);
        confBuilder.setOAuthAccessTokenSecret(accessTokenSecret);
        confBuilder.setTweetModeExtended(isExtendedMode());
        if (getHttpProxyHost() != null) {
            confBuilder.setHttpProxyHost(getHttpProxyHost());
        }
        if (getHttpProxyUser() != null) {
            confBuilder.setHttpProxyUser(getHttpProxyUser());
        }
        if (getHttpProxyPassword() != null) {
            confBuilder.setHttpProxyPassword(getHttpProxyPassword());
        }
        if (httpProxyPort != null) {
            confBuilder.setHttpProxyPort(httpProxyPort);
        }

        return confBuilder.build();
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

    public String getConsumerKey() {
        return consumerKey;
    }

    /**
     * The consumer key. Can also be configured on the TwitterComponent level instead.
     */
    public void setConsumerKey(String consumerKey) {
        this.consumerKey = consumerKey;
    }

    public String getConsumerSecret() {
        return consumerSecret;
    }

    /**
     * The consumer secret. Can also be configured on the TwitterComponent level instead.
     */
    public void setConsumerSecret(String consumerSecret) {
        this.consumerSecret = consumerSecret;
    }

    /**
     * The access token. Can also be configured on the TwitterComponent level instead.
     */
    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    /**
     * The access secret. Can also be configured on the TwitterComponent level instead.
     */
    public String getAccessTokenSecret() {
        return accessTokenSecret;
    }

    public void setAccessTokenSecret(String accessTokenSecret) {
        this.accessTokenSecret = accessTokenSecret;
    }

    public EndpointType getType() {
        return type;
    }

    /**
     * Endpoint type to use.
     */
    public void setType(EndpointType type) {
        this.type = type;
    }

    public String getLocations() {
        return locations;
    }

    /**
     * Bounding boxes, created by pairs of lat/lons. Can be used for filter. A pair is defined as lat,lon. And multiple paris can be separated by semi colon.
     */
    public void setLocations(String locations) {
        this.locations = locations;
    }

    public String getUserIds() {
        return userIds;
    }

    /**
     * To filter by user ids for filter. Multiple values can be separated by comma.
     */
    public void setUserIds(String userIds) {
        this.userIds = userIds;
    }

    public boolean isFilterOld() {
        return filterOld;
    }

    /**
     * Filter out old tweets, that has previously been polled.
     * This state is stored in memory only, and based on last tweet id.
     */
    public void setFilterOld(boolean filterOld) {
        this.filterOld = filterOld;
    }

    public long getSinceId() {
        return sinceId;
    }

    /**
     * The last tweet id which will be used for pulling the tweets. It is useful when the camel route is restarted after a long running.
     */
    public void setSinceId(long sinceId) {
        this.sinceId = sinceId;
    }

    public String getLang() {
        return lang;
    }

    /**
     * The lang string ISO_639-1 which will be used for searching
     */
    public void setLang(String lang) {
        this.lang = lang;
    }

    public Integer getCount() {
        return count;
    }

    /**
     * Limiting number of results per page.
     */
    public void setCount(Integer count) {
        this.count = count;
    }

    public Integer getNumberOfPages() {
        return numberOfPages;
    }

    /**
     * The number of pages result which you want camel-twitter to consume.
     */
    public void setNumberOfPages(Integer numberOfPages) {
        this.numberOfPages = numberOfPages;
    }

    public boolean isSortById() {
        return sortById;
    }

    /**
     * Sorts by id, so the oldest are first, and newest last.
     */
    public void setSortById(boolean sortById) {
        this.sortById = sortById;
    }

    /**
     * The http proxy host which can be used for the camel-twitter. Can also be configured on the TwitterComponent level instead.
     */
    public void setHttpProxyHost(String httpProxyHost) {
        this.httpProxyHost = httpProxyHost;
    }

    public String getHttpProxyHost() {
        return httpProxyHost;
    }

    /**
     * The http proxy user which can be used for the camel-twitter. Can also be configured on the TwitterComponent level instead.
     */
    public void setHttpProxyUser(String httpProxyUser) {
        this.httpProxyUser = httpProxyUser;
    }

    public String getHttpProxyUser() {
        return httpProxyUser;
    }

    /**
     * The http proxy password which can be used for the camel-twitter. Can also be configured on the TwitterComponent level instead.
     */
    public void setHttpProxyPassword(String httpProxyPassword) {
        this.httpProxyPassword = httpProxyPassword;
    }

    public String getHttpProxyPassword() {
        return httpProxyPassword;
    }

    /**
     * The http proxy port which can be used for the camel-twitter. Can also be configured on the TwitterComponent level instead.
     */
    public void setHttpProxyPort(Integer httpProxyPort) {
        this.httpProxyPort = httpProxyPort;
    }

    public Integer getHttpProxyPort() {
        return httpProxyPort;
    }

    public Double getLongitude() {
        return longitude;
    }

    /**
     * Used by the geography search to search by longitude.
     * <p/>
     * You need to configure all the following options: longitude, latitude, radius, and distanceMetric.
     */
    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Double getLatitude() {
        return latitude;
    }

    /**
     * Used by the geography search to search by latitude.
     * <p/>
     * You need to configure all the following options: longitude, latitude, radius, and distanceMetric.
     */
    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getRadius() {
        return radius;
    }

    /**
     * Used by the geography search to search by radius.
     * <p/>
     * You need to configure all the following options: longitude, latitude, radius, and distanceMetric.
     */
    public void setRadius(Double radius) {
        this.radius = radius;
    }

    public String getDistanceMetric() {
        return distanceMetric;
    }

    /**
     * Used by the geography search, to search by radius using the configured metrics.
     * <p/>
     * The unit can either be mi for miles, or km for kilometers.
     * <p/>
     * You need to configure all the following options: longitude, latitude, radius, and distanceMetric.
     */
    public void setDistanceMetric(String distanceMetric) {
        this.distanceMetric = distanceMetric;
    }

    /**
     * Used for enabling full text from twitter (eg receive tweets that contains more than 140 characters).
     */
    public void setExtendedMode(Boolean extendedMode) {
        this.extendedMode = extendedMode;
    }

    public boolean isExtendedMode() {
        return extendedMode;
    }

}
