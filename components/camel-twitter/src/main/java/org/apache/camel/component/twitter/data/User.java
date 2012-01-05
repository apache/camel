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

import java.net.URL;
import java.util.Date;

public class User {

    private String description;
    private String screenName;
    private URL profileUrl;
    private URL profileImageUrl;
    private String name;
    private String location;
    private int followersCount;
    private int accessLevel;
    private Date createdAt;
    private int favoritesCount;
    private int friendsCount;
    private long id;
    private String language;
    private int listedCount;
    private int statusesCount;
    private String timeZone;
    private URL url;

    public User(String name) {
        this.name = name;
    }

    public User(twitter4j.User user) {
        this.name = user.getName();
        this.screenName = user.getScreenName();
        this.profileImageUrl = user.getProfileImageURL();
        this.profileUrl = user.getURL();
        this.location = user.getLocation();
        this.description = user.getDescription();
        this.followersCount = user.getFollowersCount();
        this.accessLevel = user.getAccessLevel();
        this.createdAt = user.getCreatedAt();
        this.favoritesCount = user.getFavouritesCount();
        this.friendsCount = user.getFriendsCount();
        this.id = user.getId();
        this.language = user.getLang();
        this.listedCount = user.getListedCount();
        this.statusesCount = user.getStatusesCount();
        this.timeZone = user.getTimeZone();
        this.url = user.getURL();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getScreenName() {
        return screenName;
    }

    public void setScreenName(String screenName) {
        this.screenName = screenName;
    }

    public URL getProfileUrl() {
        return profileUrl;
    }

    public void setProfileUrl(URL profileUrl) {
        this.profileUrl = profileUrl;
    }

    public URL getProfileImageUrl() {
        return profileImageUrl;
    }

    public void setProfileImageUrl(URL profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public int getFollowersCount() {
        return followersCount;
    }

    public void setFollowersCount(int followersCount) {
        this.followersCount = followersCount;
    }

    public int getAccessLevel() {
        return accessLevel;
    }

    public void setAccessLevel(int accessLevel) {
        this.accessLevel = accessLevel;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public int getFavoritesCount() {
        return favoritesCount;
    }

    public void setFavoritesCount(int favoritesCount) {
        this.favoritesCount = favoritesCount;
    }

    public int getFriendsCount() {
        return friendsCount;
    }

    public void setFriendsCount(int friendsCount) {
        this.friendsCount = friendsCount;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public int getListedCount() {
        return listedCount;
    }

    public void setListedCount(int listedCount) {
        this.listedCount = listedCount;
    }

    public int getStatusesCount() {
        return statusesCount;
    }

    public void setStatusesCount(int statusesCount) {
        this.statusesCount = statusesCount;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    public URL getUrl() {
        return url;
    }

    public void setUrl(URL url) {
        this.url = url;
    }
}
