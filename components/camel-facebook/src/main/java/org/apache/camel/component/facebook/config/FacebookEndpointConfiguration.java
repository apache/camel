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
package org.apache.camel.component.facebook.config;

import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import facebook4j.AlbumUpdate;
import facebook4j.CheckinUpdate;
import facebook4j.EventUpdate;
import facebook4j.GeoLocation;
import facebook4j.Media;
import facebook4j.PictureSize;
import facebook4j.PostUpdate;
import facebook4j.Reading;
import facebook4j.TagUpdate;
import facebook4j.TestUser;

import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
public class FacebookEndpointConfiguration extends FacebookConfiguration {

    @UriParam
    private URL achievementURL;
    @UriParam
    private AlbumUpdate albumUpdate;
    @UriParam
    private String albumId;
    @UriParam
    private String appId;
    @UriParam
    private GeoLocation center;
    @UriParam
    private CheckinUpdate checkinUpdate;
    @UriParam
    private String checkinId;
    @UriParam
    private String commentId;
    @UriParam
    private String description;
    @UriParam
    private Integer distance;
    @UriParam
    private String domainId;
    @UriParam
    private String domainName;
    @UriParam
    private List<String> domainNames;
    @UriParam
    private String eventId;
    @UriParam
    private EventUpdate eventUpdate;
    @UriParam
    private String friendId;
    @UriParam
    private String friendUserId;
    @UriParam
    private String friendlistId;
    @UriParam
    private String friendlistName;
    @UriParam
    private String groupId;
    @UriParam
    private List<String> ids;
    @UriParam
    private Boolean includeRead;
    @UriParam
    private URL link;
    @UriParam
    private String linkId;
    @UriParam
    private Locale locale;
    @UriParam
    private String message;
    @UriParam
    private String messageId;
    @UriParam
    private String metric;
    @UriParam
    private String name;
    @UriParam
    private String noteId;
    @UriParam
    private String notificationId;
    @UriParam
    private String objectId;
    @UriParam
    private String optionDescription;
    @UriParam
    private String permissionName;
    @UriParam
    private String permissions;
    @UriParam
    private String photoId;
    @UriParam
    private String placeId;
    @UriParam
    private String postId;
    @UriParam
    private PostUpdate postUpdate;
    @UriParam
    private Map<String, String> queries;
    @UriParam
    private String query;
    @UriParam
    private String questionId;
    @UriParam
    private Reading reading;
    @UriParam
    private Integer scoreValue;
    @UriParam
    private PictureSize size;
    @UriParam
    private Media source;
    @UriParam
    private String subject;
    @UriParam
    private TagUpdate tagUpdate;
    @UriParam
    private TestUser testUser1;
    @UriParam
    private TestUser testUser2;
    @UriParam
    private String testUserId;
    @UriParam
    private String title;
    @UriParam
    private String toUserId;
    @UriParam
    private List<String> toUserIds;
    @UriParam
    private String userId1;
    @UriParam
    private String userId2;
    @UriParam
    private String userId;
    @UriParam
    private List<String> userIds;
    @UriParam
    private String userLocale;
    @UriParam
    private String videoId;

    public URL getAchievementURL() {
        return achievementURL;
    }

    public void setAchievementURL(URL achievementURL) {
        this.achievementURL = achievementURL;
    }

    public AlbumUpdate getAlbumUpdate() {
        return albumUpdate;
    }

    public void setAlbumUpdate(AlbumUpdate albumUpdate) {
        this.albumUpdate = albumUpdate;
    }

    public String getAlbumId() {
        return albumId;
    }

    public void setAlbumId(String albumId) {
        this.albumId = albumId;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public GeoLocation getCenter() {
        return center;
    }

    public void setCenter(GeoLocation center) {
        this.center = center;
    }

    public CheckinUpdate getCheckinUpdate() {
        return checkinUpdate;
    }

    public void setCheckinUpdate(CheckinUpdate checkinUpdate) {
        this.checkinUpdate = checkinUpdate;
    }

    public String getCheckinId() {
        return checkinId;
    }

    public void setCheckinId(String checkinId) {
        this.checkinId = checkinId;
    }

    public String getCommentId() {
        return commentId;
    }

    public void setCommentId(String commentId) {
        this.commentId = commentId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getDistance() {
        return distance;
    }

    public void setDistance(Integer distance) {
        this.distance = distance;
    }

    public String getDomainId() {
        return domainId;
    }

    public void setDomainId(String domainId) {
        this.domainId = domainId;
    }

    public String getDomainName() {
        return domainName;
    }

    public void setDomainName(String domainName) {
        this.domainName = domainName;
    }

    public List<String> getDomainNames() {
        return domainNames;
    }

    public void setDomainNames(List<String> domainNames) {
        this.domainNames = domainNames;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public EventUpdate getEventUpdate() {
        return eventUpdate;
    }

    public void setEventUpdate(EventUpdate eventUpdate) {
        this.eventUpdate = eventUpdate;
    }

    public String getFriendId() {
        return friendId;
    }

    public void setFriendId(String friendId) {
        this.friendId = friendId;
    }

    public String getFriendUserId() {
        return friendUserId;
    }

    public void setFriendUserId(String friendUserId) {
        this.friendUserId = friendUserId;
    }

    public String getFriendlistId() {
        return friendlistId;
    }

    public void setFriendlistId(String friendlistId) {
        this.friendlistId = friendlistId;
    }

    public String getFriendlistName() {
        return friendlistName;
    }

    public void setFriendlistName(String friendlistName) {
        this.friendlistName = friendlistName;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public List<String> getIds() {
        return ids;
    }

    public void setIds(List<String> ids) {
        this.ids = ids;
    }

    public Boolean isIncludeRead() {
        return includeRead;
    }

    public void setIncludeRead(Boolean includeRead) {
        this.includeRead = includeRead;
    }

    public URL getLink() {
        return link;
    }

    public void setLink(URL link) {
        this.link = link;
    }

    public String getLinkId() {
        return linkId;
    }

    public void setLinkId(String linkId) {
        this.linkId = linkId;
    }

    public Locale getLocale() {
        return locale;
    }

    public void setLocale(Locale locale) {
        this.locale = locale;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getMetric() {
        return metric;
    }

    public void setMetric(String metric) {
        this.metric = metric;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNoteId() {
        return noteId;
    }

    public void setNoteId(String noteId) {
        this.noteId = noteId;
    }

    public String getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(String notificationId) {
        this.notificationId = notificationId;
    }

    public String getObjectId() {
        return objectId;
    }

    public void setObjectId(String objectId) {
        this.objectId = objectId;
    }

    public String getOptionDescription() {
        return optionDescription;
    }

    public void setOptionDescription(String optionDescription) {
        this.optionDescription = optionDescription;
    }

    public String getPermissionName() {
        return permissionName;
    }

    public void setPermissionName(String permissionName) {
        this.permissionName = permissionName;
    }

    public String getPermissions() {
        return permissions;
    }

    public void setPermissions(String permissions) {
        this.permissions = permissions;
    }

    public String getPhotoId() {
        return photoId;
    }

    public void setPhotoId(String photoId) {
        this.photoId = photoId;
    }

    public String getPlaceId() {
        return placeId;
    }

    public void setPlaceId(String placeId) {
        this.placeId = placeId;
    }

    public String getPostId() {
        return postId;
    }

    public void setPostId(String postId) {
        this.postId = postId;
    }

    public PostUpdate getPostUpdate() {
        return postUpdate;
    }

    public void setPostUpdate(PostUpdate postUpdate) {
        this.postUpdate = postUpdate;
    }

    public Map<String, String> getQueries() {
        return queries;
    }

    public void setQueries(Map<String, String> queries) {
        this.queries = queries;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getQuestionId() {
        return questionId;
    }

    public void setQuestionId(String questionId) {
        this.questionId = questionId;
    }

    public Reading getReading() {
        return reading;
    }

    public void setReading(Reading reading) {
        this.reading = reading;
    }

    public Integer getScoreValue() {
        return scoreValue;
    }

    public void setScoreValue(Integer scoreValue) {
        this.scoreValue = scoreValue;
    }

    public PictureSize getSize() {
        return size;
    }

    public void setSize(PictureSize size) {
        this.size = size;
    }

    public Media getSource() {
        return source;
    }

    public void setSource(Media source) {
        this.source = source;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public TagUpdate getTagUpdate() {
        return tagUpdate;
    }

    public void setTagUpdate(TagUpdate tagUpdate) {
        this.tagUpdate = tagUpdate;
    }

    public TestUser getTestUser1() {
        return testUser1;
    }

    public void setTestUser1(TestUser testUser1) {
        this.testUser1 = testUser1;
    }

    public TestUser getTestUser2() {
        return testUser2;
    }

    public void setTestUser2(TestUser testUser2) {
        this.testUser2 = testUser2;
    }

    public String getTestUserId() {
        return testUserId;
    }

    public void setTestUserId(String testUserId) {
        this.testUserId = testUserId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getToUserId() {
        return toUserId;
    }

    public void setToUserId(String toUserId) {
        this.toUserId = toUserId;
    }

    public List<String> getToUserIds() {
        return toUserIds;
    }

    public void setToUserIds(List<String> toUserIds) {
        this.toUserIds = toUserIds;
    }

    public String getUserId1() {
        return userId1;
    }

    public void setUserId1(String userId1) {
        this.userId1 = userId1;
    }

    public String getUserId2() {
        return userId2;
    }

    public void setUserId2(String userId2) {
        this.userId2 = userId2;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public List<String> getUserIds() {
        return userIds;
    }

    public void setUserIds(List<String> userIds) {
        this.userIds = userIds;
    }

    public String getUserLocale() {
        return userLocale;
    }

    public void setUserLocale(String userLocale) {
        this.userLocale = userLocale;
    }

    public String getVideoId() {
        return videoId;
    }

    public void setVideoId(String videoId) {
        this.videoId = videoId;
    }

}
