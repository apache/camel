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
import facebook4j.CommentUpdate;
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
    @Deprecated
    private CheckinUpdate checkinUpdate;
    @UriParam
    private String checkinId;
    @UriParam
    private String commentId;
    @UriParam
    private CommentUpdate commentUpdate;
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
    @UriParam(prefix = "reading.", multiValue = true)
    private Map<String, Object> readingOptions;
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
    @UriParam
    private Integer pictureId;
    @UriParam
    private Integer pictureId2;
    @UriParam
    private PictureSize pictureSize;
    @UriParam
    private String pageId;
    @UriParam
    private String tabId;
    @UriParam
    private Boolean isHidden;
    @UriParam
    private String offerId;
    @UriParam
    private String milestoneId;

    public URL getAchievementURL() {
        return achievementURL;
    }

    /**
     * The unique URL of the achievement
     */
    public void setAchievementURL(URL achievementURL) {
        this.achievementURL = achievementURL;
    }

    public AlbumUpdate getAlbumUpdate() {
        return albumUpdate;
    }

    /**
     * The facebook Album to be created or updated
     */
    public void setAlbumUpdate(AlbumUpdate albumUpdate) {
        this.albumUpdate = albumUpdate;
    }

    public String getAlbumId() {
        return albumId;
    }

    /**
     * The album ID
     */
    public void setAlbumId(String albumId) {
        this.albumId = albumId;
    }

    public String getAppId() {
        return appId;
    }

    /**
     * The ID of the Facebook Application
     */
    public void setAppId(String appId) {
        this.appId = appId;
    }

    public GeoLocation getCenter() {
        return center;
    }

    /**
     * Location latitude and longitude
     */
    public void setCenter(GeoLocation center) {
        this.center = center;
    }

    public CheckinUpdate getCheckinUpdate() {
        return checkinUpdate;
    }

    /**
     * The checkin to be created. Deprecated, instead create a Post with an attached location
     * @deprecated instead create a Post with an attached location
     */
    @Deprecated
    public void setCheckinUpdate(CheckinUpdate checkinUpdate) {
        this.checkinUpdate = checkinUpdate;
    }

    public String getCheckinId() {
        return checkinId;
    }

    /**
     * The checkin ID
     */
    public void setCheckinId(String checkinId) {
        this.checkinId = checkinId;
    }

    public String getCommentId() {
        return commentId;
    }

    /**
     * The comment ID
     */
    public void setCommentId(String commentId) {
        this.commentId = commentId;
    }

    public String getDescription() {
        return description;
    }

    public CommentUpdate getCommentUpdate() {
        return commentUpdate;
    }

    /**
     * The facebook Comment to be created or updated
     */
    public void setCommentUpdate(CommentUpdate commentUpdate) {
        this.commentUpdate = commentUpdate;
    }

    /**
     * The description text
     */
    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getDistance() {
        return distance;
    }

    /**
     * Distance in meters
     */
    public void setDistance(Integer distance) {
        this.distance = distance;
    }

    public String getDomainId() {
        return domainId;
    }

    /**
     * The domain ID
     */
    public void setDomainId(String domainId) {
        this.domainId = domainId;
    }

    public String getDomainName() {
        return domainName;
    }

    /**
     * The domain name
     */
    public void setDomainName(String domainName) {
        this.domainName = domainName;
    }

    public List<String> getDomainNames() {
        return domainNames;
    }

    /**
     * The domain names
     */
    public void setDomainNames(List<String> domainNames) {
        this.domainNames = domainNames;
    }

    public String getEventId() {
        return eventId;
    }

    /**
     * The event ID
     */
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public EventUpdate getEventUpdate() {
        return eventUpdate;
    }

    /**
     * The event to be created or updated
     */
    public void setEventUpdate(EventUpdate eventUpdate) {
        this.eventUpdate = eventUpdate;
    }

    public String getFriendId() {
        return friendId;
    }

    /**
     * The friend ID
     */
    public void setFriendId(String friendId) {
        this.friendId = friendId;
    }

    public String getFriendUserId() {
        return friendUserId;
    }

    /**
     * The friend user ID
     */
    public void setFriendUserId(String friendUserId) {
        this.friendUserId = friendUserId;
    }

    public String getFriendlistId() {
        return friendlistId;
    }

    /**
     * The friend list ID
     */
    public void setFriendlistId(String friendlistId) {
        this.friendlistId = friendlistId;
    }

    public String getFriendlistName() {
        return friendlistName;
    }

    /**
     * The friend list Name
     */
    public void setFriendlistName(String friendlistName) {
        this.friendlistName = friendlistName;
    }

    public String getGroupId() {
        return groupId;
    }

    /**
     * The group ID
     */
    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public List<String> getIds() {
        return ids;
    }

    /**
     * The ids of users
     */
    public void setIds(List<String> ids) {
        this.ids = ids;
    }

    public Boolean isIncludeRead() {
        return includeRead;
    }

    /**
     * Enables notifications that the user has already read in addition to unread ones
     */
    public void setIncludeRead(Boolean includeRead) {
        this.includeRead = includeRead;
    }

    public URL getLink() {
        return link;
    }

    /**
     * Link URL
     */
    public void setLink(URL link) {
        this.link = link;
    }

    public String getLinkId() {
        return linkId;
    }

    /**
     * Link ID
     */
    public void setLinkId(String linkId) {
        this.linkId = linkId;
    }

    public Locale getLocale() {
        return locale;
    }

    /**
     * Desired FQL locale
     */
    public void setLocale(Locale locale) {
        this.locale = locale;
    }

    public String getMessage() {
        return message;
    }

    /**
     * The message text
     */
    public void setMessage(String message) {
        this.message = message;
    }

    public String getMessageId() {
        return messageId;
    }

    /**
     * The message ID
     */
    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getMetric() {
        return metric;
    }

    /**
     * The metric name
     */
    public void setMetric(String metric) {
        this.metric = metric;
    }

    public String getName() {
        return name;
    }

    /**
     * Test user name, must be of the form 'first last'
     */
    public void setName(String name) {
        this.name = name;
    }

    public String getNoteId() {
        return noteId;
    }

    /**
     * The note ID
     */
    public void setNoteId(String noteId) {
        this.noteId = noteId;
    }

    public String getNotificationId() {
        return notificationId;
    }

    /**
     * The notification ID
     */
    public void setNotificationId(String notificationId) {
        this.notificationId = notificationId;
    }

    public String getObjectId() {
        return objectId;
    }

    /**
     * The insight object ID
     */
    public void setObjectId(String objectId) {
        this.objectId = objectId;
    }

    public String getOptionDescription() {
        return optionDescription;
    }

    /**
     * The question's answer option description
     */
    public void setOptionDescription(String optionDescription) {
        this.optionDescription = optionDescription;
    }

    public String getPermissionName() {
        return permissionName;
    }

    /**
     * The permission name
     */
    public void setPermissionName(String permissionName) {
        this.permissionName = permissionName;
    }

    public String getPermissions() {
        return permissions;
    }

    /**
     * Test user permissions in the format perm1,perm2,...
     */
    public void setPermissions(String permissions) {
        this.permissions = permissions;
    }

    public String getPhotoId() {
        return photoId;
    }

    /**
     * The photo ID
     */
    public void setPhotoId(String photoId) {
        this.photoId = photoId;
    }

    public String getPlaceId() {
        return placeId;
    }

    /**
     * The place ID
     */
    public void setPlaceId(String placeId) {
        this.placeId = placeId;
    }

    public String getPostId() {
        return postId;
    }

    /**
     * The post ID
     */
    public void setPostId(String postId) {
        this.postId = postId;
    }

    public PostUpdate getPostUpdate() {
        return postUpdate;
    }

    /**
     * The post to create or update
     */
    public void setPostUpdate(PostUpdate postUpdate) {
        this.postUpdate = postUpdate;
    }

    public Map<String, String> getQueries() {
        return queries;
    }

    /**
     * FQL queries
     */
    public void setQueries(Map<String, String> queries) {
        this.queries = queries;
    }

    public String getQuery() {
        return query;
    }

    /**
     * FQL query or search terms for search* endpoints
     */
    public void setQuery(String query) {
        this.query = query;
    }

    public String getQuestionId() {
        return questionId;
    }

    /**
     * The question id
     */
    public void setQuestionId(String questionId) {
        this.questionId = questionId;
    }

    public Reading getReading() {
        return reading;
    }

    /**
     * Optional reading parameters. See Reading Options(#reading)
     */
    public void setReading(Reading reading) {
        this.reading = reading;
    }

    public Map<String, Object> getReadingOptions() {
        return readingOptions;
    }

    /**
     * To configure {@link Reading} using key/value pairs from the Map.
     */
    public void setReadingOptions(Map<String, Object> readingOptions) {
        this.readingOptions = readingOptions;
    }

    public Integer getScoreValue() {
        return scoreValue;
    }

    /**
     * The numeric score with value
     */
    public void setScoreValue(Integer scoreValue) {
        this.scoreValue = scoreValue;
    }

    public PictureSize getSize() {
        return size;
    }

    /**
     * The picture size, one of large, normal, small or square
     */
    public void setSize(PictureSize size) {
        this.size = size;
    }

    public Media getSource() {
        return source;
    }

    /**
     * The media content from either a java.io.File or java.io.Inputstream
     */
    public void setSource(Media source) {
        this.source = source;
    }

    public String getSubject() {
        return subject;
    }

    /**
     * The note of the subject
     */
    public void setSubject(String subject) {
        this.subject = subject;
    }

    public TagUpdate getTagUpdate() {
        return tagUpdate;
    }

    /**
     * Photo tag information
     */
    public void setTagUpdate(TagUpdate tagUpdate) {
        this.tagUpdate = tagUpdate;
    }

    public TestUser getTestUser1() {
        return testUser1;
    }

    /**
     * Test user 1
     */
    public void setTestUser1(TestUser testUser1) {
        this.testUser1 = testUser1;
    }

    public TestUser getTestUser2() {
        return testUser2;
    }

    /**
     * Test user 2
     */
    public void setTestUser2(TestUser testUser2) {
        this.testUser2 = testUser2;
    }

    public String getTestUserId() {
        return testUserId;
    }

    /**
     * The ID of the test user
     */
    public void setTestUserId(String testUserId) {
        this.testUserId = testUserId;
    }

    public String getTitle() {
        return title;
    }

    /**
     * The title text
     */
    public void setTitle(String title) {
        this.title = title;
    }

    public String getToUserId() {
        return toUserId;
    }

    /**
     * The ID of the user to tag
     */
    public void setToUserId(String toUserId) {
        this.toUserId = toUserId;
    }

    public List<String> getToUserIds() {
        return toUserIds;
    }

    /**
     * The IDs of the users to tag
     */
    public void setToUserIds(List<String> toUserIds) {
        this.toUserIds = toUserIds;
    }

    public String getUserId1() {
        return userId1;
    }

    /**
     * The ID of a user 1
     */
    public void setUserId1(String userId1) {
        this.userId1 = userId1;
    }

    public String getUserId2() {
        return userId2;
    }

    /**
     * The ID of a user 2
     */
    public void setUserId2(String userId2) {
        this.userId2 = userId2;
    }

    public String getUserId() {
        return userId;
    }

    /**
     * The Facebook user ID
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    public List<String> getUserIds() {
        return userIds;
    }

    /**
     * The IDs of users to invite to event
     */
    public void setUserIds(List<String> userIds) {
        this.userIds = userIds;
    }

    public String getUserLocale() {
        return userLocale;
    }

    /**
     * The test user locale
     */
    public void setUserLocale(String userLocale) {
        this.userLocale = userLocale;
    }

    public String getVideoId() {
        return videoId;
    }

    /**
     * The video ID
     */
    public void setVideoId(String videoId) {
        this.videoId = videoId;
    }

    public Integer getPictureId() {
        return pictureId;
    }

    /**
     * The picture id
     */
    public void setPictureId(Integer pictureId) {
        this.pictureId = pictureId;
    }

    public Integer getPictureId2() {
        return pictureId2;
    }

    /**
     * The picture2 id
     */
    public void setPictureId2(Integer pictureId2) {
        this.pictureId2 = pictureId2;
    }

    public PictureSize getPictureSize() {
        return pictureSize;
    }

    /**
     * The picture size
     */
    public void setPictureSize(PictureSize pictureSize) {
        this.pictureSize = pictureSize;
    }

    public String getPageId() {
        return pageId;
    }

    /**
     * The page id
     */
    public void setPageId(String pageId) {
        this.pageId = pageId;
    }

    public String getTabId() {
        return tabId;
    }

    /**
     * The tab id
     */
    public void setTabId(String tabId) {
        this.tabId = tabId;
    }

    public Boolean isHidden() {
        return isHidden;
    }

    /**
     * Whether hidden
     */
    public void setIsHidden(Boolean isHidden) {
        this.isHidden = isHidden;
    }

    public String getOfferId() {
        return offerId;
    }

    /**
     * The offer id
     */
    public void setOfferId(String offerId) {
        this.offerId = offerId;
    }

    public String getMilestoneId() {
        return milestoneId;
    }

    /**
     * The milestone id
     */
    public void setMilestoneId(String milestoneId) {
        this.milestoneId = milestoneId;
    }
}
