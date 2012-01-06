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
package org.apache.camel.component.twitter.data;

import java.util.Date;

import twitter4j.DirectMessage;
import twitter4j.Tweet;

public class Status {

    private Date date;
    private User user;
    private long id;
    private String text;
    private boolean truncated;
    private GeoLocation geoLocation;
    private String inReplyToScreenName;
    private long inReplyToStatusId;
    private long inReplyToUserId;
    private long retweetCount;
    private Status retweetedStatus;
    private String source;

    public Status(twitter4j.Status s) {
        this.user = new User(s.getUser());
        this.id = s.getId();
        this.text = s.getText();
        this.date = s.getCreatedAt();
        this.truncated = s.isTruncated();
        this.inReplyToScreenName = s.getInReplyToScreenName();
        this.inReplyToStatusId = s.getInReplyToStatusId();
        this.inReplyToUserId = s.getInReplyToUserId();
        this.retweetCount = s.getRetweetCount();
        this.source = s.getSource();

        if (s.getGeoLocation() != null) {
            this.geoLocation = new GeoLocation(s.getGeoLocation());
        }

        if (s.getRetweetedStatus() != null) {
            this.retweetedStatus = new Status(s.getRetweetedStatus());
        }
    }

    public Status(Tweet s) {
        this.user = new User(s.getFromUser());
        this.id = s.getId();
        this.text = s.getText();
        this.date = s.getCreatedAt();
        this.source = s.getSource();

        if (s.getGeoLocation() != null) {
            this.geoLocation = new GeoLocation(s.getGeoLocation());
        }
    }

    public Status(DirectMessage dm) {
        this.date = dm.getCreatedAt();
        this.id = dm.getId();
        this.user = new User(dm.getSender());
        this.text = dm.getText();
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public void setTruncated(boolean truncated) {
        this.truncated = truncated;
    }

    public GeoLocation getGeoLocation() {
        return geoLocation;
    }

    public void setGeoLocation(GeoLocation geoLocation) {
        this.geoLocation = geoLocation;
    }

    public String getInReplyToScreenName() {
        return inReplyToScreenName;
    }

    public void setInReplyToScreenName(String inReplyToScreenName) {
        this.inReplyToScreenName = inReplyToScreenName;
    }

    public long getInReplyToStatusId() {
        return inReplyToStatusId;
    }

    public void setInReplyToStatusId(long inReplyToStatusId) {
        this.inReplyToStatusId = inReplyToStatusId;
    }

    public long getInReplyToUserId() {
        return inReplyToUserId;
    }

    public void setInReplyToUserId(long inReplyToUserId) {
        this.inReplyToUserId = inReplyToUserId;
    }

    public long getRetweetCount() {
        return retweetCount;
    }

    public void setRetweetCount(long retweetCount) {
        this.retweetCount = retweetCount;
    }

    public Status getRetweetedStatus() {
        return retweetedStatus;
    }

    public void setRetweetedStatus(Status retweetedStatus) {
        this.retweetedStatus = retweetedStatus;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String toString() {
        StringBuilder s = new StringBuilder();
        s.append(getDate()).append(" (").append(getUser().getScreenName()).append(") ");
        s.append(getText());
        return s.toString();
    }
}
