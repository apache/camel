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
package org.apache.camel.component.facebook.data;

import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import facebook4j.Album;
import facebook4j.AlbumUpdate;
import facebook4j.BackdatingPostUpdate;
import facebook4j.BatchRequests;
import facebook4j.Checkin;
import facebook4j.CheckinUpdate;
import facebook4j.Comment;
import facebook4j.Domain;
import facebook4j.Event;
import facebook4j.EventUpdate;
import facebook4j.Facebook;
import facebook4j.Friendlist;
import facebook4j.GeoLocation;
import facebook4j.Group;
import facebook4j.InboxResponseList;
import facebook4j.Link;
import facebook4j.Media;
import facebook4j.Message;
import facebook4j.MilestoneUpdate;
import facebook4j.Note;
import facebook4j.Offer;
import facebook4j.OfferUpdate;
import facebook4j.Page;
import facebook4j.PageCoverUpdate;
import facebook4j.PagePhotoUpdate;
import facebook4j.PageSettingUpdate;
import facebook4j.PageUpdate;
import facebook4j.Photo;
import facebook4j.PhotoUpdate;
import facebook4j.PictureSize;
import facebook4j.Post;
import facebook4j.PostUpdate;
import facebook4j.Question;
import facebook4j.QuestionUpdate;
import facebook4j.RawAPIResponse;
import facebook4j.Reading;
import facebook4j.ResponseList;
import facebook4j.TabUpdate;
import facebook4j.TagUpdate;
import facebook4j.TestUser;
import facebook4j.User;
import facebook4j.Video;
import facebook4j.VideoUpdate;
import facebook4j.internal.org.json.JSONArray;

import org.apache.camel.component.facebook.FacebookConstants;

/**
 * Enum for Facebook4J *Method interfaces.
 * The methods are ordered by the number and nature of arguments.
 */
public enum FacebookMethodsType {

    // AccountMethods
    GET_ACCOUNTS(ResponseList.class, "getAccounts"),
    GET_ACCOUNTS_WITH_OPTIONS(ResponseList.class, "getAccounts", Reading.class, FacebookConstants.READING_PPROPERTY),
    GET_ACCOUNTS_WITH_ID(ResponseList.class, "getAccounts", String.class, "userId"),
    GET_ACCOUNTS_WITH_ID_OPTIONS(ResponseList.class, "getAccounts", String.class, "userId", Reading.class, FacebookConstants.READING_PPROPERTY),

    // ActivityMethods
    GETACTIVITIES(ResponseList.class, "getActivities"),
    GETACTIVITIES_WITH_OPTIONS(ResponseList.class, "getActivities", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETACTIVITIES_WITH_ID(ResponseList.class, "getActivities", String.class, "userId"),
    GETACTIVITIES_WITH_ID_OPTIONS(ResponseList.class, "getActivities", String.class, "userId", Reading.class, FacebookConstants.READING_PPROPERTY),

    // AlbumMethods
    ADDALBUMPHOTO(String.class, "addAlbumPhoto", String.class, "albumId", Media.class, "source"),
    ADDALBUMPHOTO_WITH_MEDIA(String.class, "addAlbumPhoto", String.class, "albumId", Media.class, "source", String.class, "message"),
    COMMENTALBUM(String.class, "commentAlbum", String.class, "albumId", String.class, "message"),
    CREATEALBUM(String.class, "createAlbum", AlbumUpdate.class, "albumUpdate"),
    CREATEALBUM_WITH_ID(String.class, "createAlbum", String.class, "userId", AlbumUpdate.class, "albumUpdate"),
    GETALBUM(Album.class,  "getAlbum", String.class, "albumId"),
    GETALBUM_WITH_OPTIONS(Album.class,  "getAlbum", String.class, "albumId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETALBUMCOMMENTS(ResponseList.class, "getAlbumComments", String.class, "albumId"),
    GETALBUMCOMMENTS_WITH_OPTIONS(ResponseList.class, "getAlbumComments", String.class, "albumId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETALBUMCOVERPHOTO(URL.class, "getAlbumCoverPhoto", String.class, "albumId"),
    GETALBUMLIKES(ResponseList.class, "getAlbumLikes", String.class, "albumId"),
    GETALBUMLIKES_WITH_OPTIONS(ResponseList.class, "getAlbumLikes", String.class, "albumId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETALBUMPHOTOS(ResponseList.class, "getAlbumPhotos", String.class, "albumId"),
    GETALBUMPHOTOS_WITH_OPTIONS(ResponseList.class, "getAlbumPhotos", String.class, "albumId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETALBUMS(ResponseList.class, "getAlbums"),
    GETALBUMS_WITH_OPTIONS(ResponseList.class, "getAlbums", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETALBUMS_WITH_ID(ResponseList.class, "getAlbums", String.class, "userId"),
    GETALBUMS_WITH_ID_OPTIONS(ResponseList.class, "getAlbums", String.class, "userId", Reading.class, FacebookConstants.READING_PPROPERTY),
    LIKEALBUM(boolean.class, "likeAlbum", String.class, "albumId"),
    UNLIKEALBUM(boolean.class, "unlikeAlbum", String.class, "albumId"),

    // CheckinMethods
    CHECKIN(String.class, "checkin", CheckinUpdate.class, "checkinUpdate"),
    CHECKIN_WITH_ID(String.class, "checkin", String.class, "userId", CheckinUpdate.class, "checkinUpdate"),
    COMMENTCHECKIN(String.class, "commentCheckin", String.class, "checkinId", String.class, "message"),
    GETCHECKIN(Checkin.class, "getCheckin", String.class, "checkinId"),
    GETCHECKIN_WITH_OPTIONS(Checkin.class, "getCheckin", String.class, "checkinId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETCHECKINCOMMENTS(ResponseList.class, "getCheckinComments", String.class, "checkinId"),
    GETCHECKINCOMMENTS_WITH_OPTIONS(ResponseList.class, "getCheckinComments", String.class, "checkinId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETCHECKINLIKES(ResponseList.class, "getCheckinLikes", String.class, "checkinId"),
    GETCHECKINLIKES_WITH_OPTIONS(ResponseList.class, "getCheckinLikes", String.class, "checkinId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETCHECKINS(ResponseList.class, "getCheckins"),
    GETCHECKINS_WITH_OPTIONS(ResponseList.class, "getCheckins", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETCHECKINS_WITH_ID(ResponseList.class, "getCheckins", String.class, "userId"),
    GETCHECKINS_WITH_ID_OPTIONS(ResponseList.class, "getCheckins", String.class, "userId", Reading.class, FacebookConstants.READING_PPROPERTY),
    LIKECHECKIN(boolean.class, "likeCheckin", String.class, "checkinId"),
    UNLIKECHECKIN(boolean.class, "unlikeCheckin", String.class, "checkinId"),

    // CommentMethods
    DELETECOMMENT(boolean.class, "deleteComment", String.class, "commentId"),
    GETCOMMENT(Comment.class, "getComment", String.class, "commentId"),
    GETCOMMENTLIKES(ResponseList.class, "getCommentLikes", String.class, "commentId"),
    GETCOMMENTLIKES_WITH_OPTIONS(ResponseList.class, "getCommentLikes", String.class, "commentId", Reading.class, FacebookConstants.READING_PPROPERTY),
    LIKECOMMENT(boolean.class, "likeComment", String.class, "commentId"),
    UNLIKECOMMENT(Boolean.class, "unlikeComment", String.class, "commentId"),

    // DomainMethods
    GETDOMAIN(Domain.class, "getDomain", String.class, "domainId"),
    GETDOMAINBYNAME(Domain.class, "getDomainByName", String.class, "domainName"),
    GETDOMAINSBYNAME_WITH_DOMAINS(List.class, "getDomainsByName", new String[0].getClass(), "domainNames"),

    // EventMethods
    CREATEEVENT(String.class, "createEvent", EventUpdate.class, "eventUpdate"),
    CREATEEVENT_WITH_ID(String.class, "createEvent", String.class, "userId", EventUpdate.class, "eventUpdate"),
    DELETEEVENT(Boolean.class,  "deleteEvent", String.class, "eventId"),
    DELETEEVENTPICTURE(Boolean.class,  "deleteEventPicture", String.class, "eventId"),
    EDITEVENT(Boolean.class,  "editEvent", String.class, "eventId", EventUpdate.class, "eventUpdate"),
    GETEVENT(Event.class,  "getEvent", String.class, "eventId"),
    GETEVENT_WITH_OPTIONS(Event.class,  "getEvent", String.class, "eventId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETEVENTPHOTOS(ResponseList.class, "getEventPhotos", String.class, "eventId"),
    GETEVENTPHOTOS_WITH_OPTIONS(ResponseList.class, "getEventPhotos", String.class, "eventId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETEVENTPICTUREURL(URL.class,  "getEventPictureURL", String.class, "eventId"),
    GETEVENTPICTUREURL_WITH_PICTURESIZE(URL.class,  "getEventPictureURL", String.class, "eventId", PictureSize.class, "size"),
    GETEVENTS(ResponseList.class, "getEvents"),
    GETEVENTS_WITH_OPTIONS(ResponseList.class, "getEvents", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETEVENTS_WITH_ID(ResponseList.class, "getEvents", String.class, "userId"),
    GETEVENTS_WITH_ID_OPTIONS(ResponseList.class, "getEvents", String.class, "userId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETEVENTVIDEOS(ResponseList.class, "getEventVideos", String.class, "eventId"),
    GETEVENTVIDEOS_WITH_OPTIONS(ResponseList.class, "getEventVideos", String.class, "eventId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETRSVPSTATUSASINVITED(ResponseList.class, "getRSVPStatusAsInvited", String.class, "eventId"),
    GETRSVPSTATUSASINVITED_WITH_ID(ResponseList.class, "getRSVPStatusAsInvited", String.class, "eventId", String.class, "userId"),
    GETRSVPSTATUSASNOREPLY(ResponseList.class, "getRSVPStatusAsNoreply", String.class, "eventId"),
    GETRSVPSTATUSASNOREPLY_WITH_ID(ResponseList.class, "getRSVPStatusAsNoreply", String.class, "eventId", String.class, "userId"),
    GETRSVPSTATUSINATTENDING(ResponseList.class, "getRSVPStatusInAttending", String.class, "eventId"),
    GETRSVPSTATUSINATTENDING_WITH_ID(ResponseList.class, "getRSVPStatusInAttending", String.class, "eventId", String.class, "userId"),
    GETRSVPSTATUSINDECLINED(ResponseList.class, "getRSVPStatusInDeclined", String.class, "eventId"),
    GETRSVPSTATUSINDECLINED_WITH_ID(ResponseList.class, "getRSVPStatusInDeclined", String.class, "eventId", String.class, "userId"),
    GETRSVPSTATUSINMAYBE(ResponseList.class, "getRSVPStatusInMaybe", String.class, "eventId"),
    GETRSVPSTATUSINMAYBE_WITH_ID(ResponseList.class, "getRSVPStatusInMaybe", String.class, "eventId", String.class, "userId"),
    INVITETOEVENT(Boolean.class,  "inviteToEvent", String.class, "eventId", String.class, "userId"),
    INVITETOEVENT_WITH_IDS(Boolean.class,  "inviteToEvent", String.class, "eventId", new String[0].getClass(), "userIds"),
    POSTEVENTLINK_WITH_LINK(String.class, "postEventLink", String.class, "eventId", URL.class , "link"),
    POSTEVENTLINK_WITH_LINK_MSG(String.class, "postEventLink", String.class, "eventId", URL.class , "link", String.class, "message"),
    POSTEVENTPHOTO_WITH_MEDIA(String.class, "postEventPhoto", String.class, "eventId", Media.class, "source"),
    POSTEVENTPHOTO_WITH_MEDIA_MSG(String.class, "postEventPhoto", String.class, "eventId", Media.class, "source", String.class, "message"),
    POSTEVENTSTATUSMESSAGE_WITH_MSG(String.class, "postEventStatusMessage", String.class, "eventId", String.class, "message"),
    POSTEVENTVIDEO_WITH_MEDIA(String.class, "postEventVideo", String.class, "eventId", Media.class, "source"),
    POSTEVENTVIDEO_WITH_MEDIA_TITLE_DESC(String.class, "postEventVideo", String.class, "eventId", Media.class, "source", String.class, "title", String.class, "description"),
    RSVPEVENTASATTENDING(Boolean.class,  "rsvpEventAsAttending", String.class, "eventId"),
    RSVPEVENTASDECLINED(Boolean.class,  "rsvpEventAsDeclined", String.class, "eventId"),
    RSVPEVENTASMAYBE(Boolean.class,  "rsvpEventAsMaybe", String.class, "eventId"),
    UNINVITEFROMEVENT(Boolean.class,  "uninviteFromEvent", String.class, "eventId", String.class, "userId"),
    UPDATEEVENTPICTURE(Boolean.class,  "updateEventPicture", String.class, "eventId", Media.class, "source"),

    // FamilyMethods
    GETFAMILY(ResponseList.class, "getFamily"),
    GETFAMILY_WITH_OPTIONS(ResponseList.class, "getFamily", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETFAMILY_WITH_ID(ResponseList.class, "getFamily", String.class, "userId"),
    GETFAMILY_WITH_ID_OPTIONS(ResponseList.class, "getFamily", String.class, "userId", Reading.class, FacebookConstants.READING_PPROPERTY),

    // FavouriteMethods
    GETBOOKS(ResponseList.class, "getBooks"),
    GETBOOKS_WITH_OPTIONS(ResponseList.class, "getBooks", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETBOOKS_WITH_ID(ResponseList.class, "getBooks", String.class, "userId"),
    GETBOOKS_WITH_ID_OPTIONS(ResponseList.class, "getBooks", String.class, "userId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETGAMES(ResponseList.class, "getGames"),
    GETGAMES_WITH_OPTIONS(ResponseList.class, "getGames", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETGAMES_WITH_ID(ResponseList.class, "getGames", String.class, "userId"),
    GETGAMES_WITH_ID_OPTIONS(ResponseList.class, "getGames", String.class, "userId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETINTERESTS(ResponseList.class, "getInterests"),
    GETINTERESTS_WITH_ID(ResponseList.class, "getInterests", String.class, "userId"),
    GETINTERESTS_WITH_OPTIONS(ResponseList.class, "getInterests", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETINTERESTS_WITH_ID_OPTIONS(ResponseList.class, "getInterests",  String.class, "userId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETMOVIES(ResponseList.class, "getMovies"),
    GETMOVIES_WITH_OPTIONS(ResponseList.class, "getMovies", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETMOVIES_WITH_ID(ResponseList.class, "getMovies", String.class, "userId"),
    GETMOVIES_WITH_ID_OPTIONS(ResponseList.class, "getMovies", String.class, "userId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETMUSIC(ResponseList.class, "getMusic"),
    GETMUSIC_WITH_OPTIONS(ResponseList.class, "getMusic", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETMUSIC_WITH_ID(ResponseList.class, "getMusic", String.class, "userId"),
    GETMUSIC_WITH_ID_OPTIONS(ResponseList.class, "getMusic", String.class, "userId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETTELEVISION(ResponseList.class, "getTelevision"),
    GETTELEVISION_WITH_OPTIONS(ResponseList.class, "getTelevision", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETTELEVISION_WITH_ID(ResponseList.class, "getTelevision", String.class, "userId"),
    GETTELEVISION_WITH_ID_OPTIONS(ResponseList.class, "getTelevision", String.class, "userId", Reading.class, FacebookConstants.READING_PPROPERTY),

    // FQLMethods
    EXECUTEFQL(JSONArray.class, "executeFQL", String.class, "query"),
    EXECUTEFQL_WITH_LOCALE(JSONArray.class, "executeFQL", String.class, "query", Locale.class, " locale"),
    EXECUTEMULTIFQL(Map.class, "executeMultiFQL", Map.class, "queries"),
    EXECUTEMULTIFQL_WITH_LOCALE(Map.class, "executeMultiFQL", Map.class, "queries", Locale.class, "locale"),

    // FriendMethods
    ADDFRIENDLISTMEMBER(Boolean.class, "addFriendlistMember", String.class, "friendlistId", String.class, "userId"),
    CREATEFRIENDLIST(String.class, "createFriendlist", String.class, "friendlistName"),
    CREATEFRIENDLIST_WITH_ID(String.class, "createFriendlist", String.class, "userId", String.class, "friendlistName"),
    DELETEFRIENDLISTMEMBER(Boolean.class, "deleteFriendlistMember", String.class, "friendlistId", String.class, "userId"),
    DELETEFRIENDLIST(Boolean.class, "deleteFriendlist", String.class, "friendlistId"),
    GETBELONGSFRIEND(ResponseList.class, "getBelongsFriend", String.class, "friendId"),
    GETBELONGSFRIEND_WITH_OPTIONS(ResponseList.class, "getBelongsFriend", String.class, "friendId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETBELONGSFRIEND_WITH_ID(ResponseList.class, "getBelongsFriend", String.class, "userId", String.class, "friendId"),
    GETBELONGSFRIEND_WITH_ID_OPTIONS(ResponseList.class, "getBelongsFriend", String.class, "userId", String.class, "friendId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETFRIENDLIST(Friendlist.class, "getFriendlist", String.class, "friendlistId"),
    GETFRIENDLIST_WITH_OPTIONS(Friendlist.class, "getFriendlist", String.class, "friendlistId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETFRIENDLISTMEMBERS(ResponseList.class, "getFriendlistMembers", String.class, "friendlistId"),
    GETFRIENDLISTS(ResponseList.class, "getFriendlists"),
    GETFRIENDLISTS_WITH_OPTIONS(ResponseList.class, "getFriendlists", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETFRIENDLISTS_WITH_ID(ResponseList.class, "getFriendlists", String.class, "userId"),
    GETFRIENDLISTS_WITH_ID_OPTIONS(ResponseList.class, "getFriendlists", String.class, "userId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETFRIENDREQUESTS(ResponseList.class, "getFriendRequests"),
    GETFRIENDREQUESTS_WITH_OPTIONS(ResponseList.class, "getFriendRequests", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETFRIENDREQUESTS_WITH_ID(ResponseList.class, "getFriendRequests", String.class, "userId"),
    GETFRIENDREQUESTS_WITH_ID_OPTIONS(ResponseList.class, "getFriendRequests", String.class, "userId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETFRIENDS(ResponseList.class, "getFriends"),
    GETFRIENDS_WITH_OPTIONS(ResponseList.class, "getFriends", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETFRIENDS_WITH_ID(ResponseList.class, "getFriends", String.class, "userId"),
    GETFRIENDS_WITH_ID_OPTIONS(ResponseList.class, "getFriends", String.class, "userId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETTAGGABLEFRIENDS(ResponseList.class, "getTaggableFriends"),
    GETTAGGABLEFRIENDS_WITH_ID(ResponseList.class, "getTaggableFriends", String.class, "userId"),
    GETTAGGABLEFRIENDS_WITH_OPTIONS(ResponseList.class, "getTaggableFriends", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETTAGGABLEFRIENDS_WITH_ID_OPTIONS(ResponseList.class, "getTaggableFriends", String.class, "userId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETMUTUALFRIENDS(ResponseList.class, "getMutualFriends", String.class, "friendUserId"),
    GETMUTUALFRIENDS_WITH_OPTIONS(ResponseList.class, "getMutualFriends", String.class, "friendUserId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETMUTUALFRIENDS_WITH_ID(ResponseList.class, "getMutualFriends", String.class, "userId1", String.class, "userId2"),
    GETMUTUALFRIENDS_WITH_ID_OPTIONS(ResponseList.class, "getMutualFriends", String.class, "userId1", String.class, "userId2", Reading.class, FacebookConstants.READING_PPROPERTY),
    REMOVEFRIENDLISTMEMBER(Boolean.class, "removeFriendlistMember", String.class, "friendlistId", String.class, "userId"),

    // GameMethods
    DELETEACHIEVEMENT(Boolean.class, "deleteAchievement", URL.class, "achievementURL"),
    DELETEACHIEVEMENT_WITH_ID(Boolean.class, "deleteAchievement", String.class, "userId", URL.class , "achievementURL"),
    DELETESCORE(Boolean.class, "deleteScore"),
    DELETESCORE_WITH_ID(Boolean.class, "deleteScore", String.class, "userId"),
    GETACHIEVEMENTS(ResponseList.class, "getAchievements"),
    GETACHIEVEMENTS_WITH_OPTIONS(ResponseList.class, "getAchievements", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETACHIEVEMENTS_WITH_ID(ResponseList.class, "getAchievements", String.class, "userId"),
    GETACHIEVEMENTS_WITH_ID_OPTIONS(ResponseList.class, "getAchievements", String.class, "userId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETSCORES(ResponseList.class, "getScores"),
    GETSCORES_WITH_OPTIONS(ResponseList.class, "getScores", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETSCORES_WITH_ID(ResponseList.class, "getScores", String.class, "userId"),
    GETSCORES_WITH_ID_OPTIONS(ResponseList.class, "getScores", String.class, "userId", Reading.class, FacebookConstants.READING_PPROPERTY),
    POSTACHIEVEMENT(String.class, "postAchievement", URL.class, "achievementURL"),
    POSTACHIEVEMENT_WITH_ID(String.class, "postAchievement", String.class, "userId", URL.class, "achievementURL"),
    POSTSCORE(Boolean.class, "postScore", int.class, "scoreValue"),
    POSTSCORE_WITH_ID(Boolean.class,  "postScore", String.class, "userId", int.class, "scoreValue"),

    // GroupMethods
    GETGROUP(Group.class,  "getGroup", String.class, "groupId"),
    GETGROUP_WITH_OPTIONS(Group.class,  "getGroup", String.class, "groupId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETGROUPDOCS(ResponseList.class, "getGroupDocs", String.class, "groupId"),
    GETGROUPDOCS_WITH_OPTIONS(ResponseList.class, "getGroupDocs", String.class, "groupId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETGROUPFEED(ResponseList.class, "getGroupFeed", String.class, "groupId"),
    GETGROUPFEED_WITH_OPTIONS(ResponseList.class, "getGroupFeed", String.class, "groupId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETGROUPMEMBERS(ResponseList.class, "getGroupMembers", String.class, "groupId"),
    GETGROUPMEMBERS_WITH_OPTIONS(ResponseList.class, "getGroupMembers", String.class, "groupId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETGROUPPICTUREURL(URL.class,  "getGroupPictureURL", String.class, "groupId"),
    GETGROUPS(ResponseList.class, "getGroups"),
    GETGROUPS_WITH_OPTIONS(ResponseList.class, "getGroups", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETGROUPS_WITH_ID(ResponseList.class, "getGroups", String.class, "userId"),
    GETGROUPS_WITH_ID_OPTIONS(ResponseList.class, "getGroups", String.class, "userId", Reading.class, FacebookConstants.READING_PPROPERTY),
    POSTGROUPFEED_WITH_POSTUPDATE(String.class, "postGroupFeed", String.class, "groupId", PostUpdate.class, "postUpdate"),
    POSTGROUPLINK_WITH_LINK(String.class, "postGroupLink", String.class, "groupId", URL.class, "link"),
    POSTGROUPLINK_WITH_LINK_MSG(String.class, "postGroupLink", String.class, "groupId", URL.class, "link", String.class, "message"),
    POSTGROUPSTATUSMESSAGE(String.class, "postGroupStatusMessage", String.class, "groupId", String.class, "message"),

    // InsightMethods
    GETINSIGHTS(ResponseList.class, "getInsights", String.class, "objectId", String.class, "metric"),
    GETINSIGHTS_WITH_OPTIONS(ResponseList.class, "getInsights", String.class, "objectId", String.class, "metric", Reading.class, FacebookConstants.READING_PPROPERTY),

    // LikeMethods
    GETUSERLIKES(ResponseList.class, "getUserLikes"),
    GETUSERLIKES_WITH_OPTIONS(ResponseList.class, "getUserLikes", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETUSERLIKES_WITH_ID(ResponseList.class, "getUserLikes", String.class, "userId"),
    GETUSERLIKES_WITH_ID_OPTIONS(ResponseList.class, "getUserLikes", String.class, "userId", Reading.class, FacebookConstants.READING_PPROPERTY),

    // LinkMethods
    COMMENTLINK(String.class, "commentLink", String.class, "linkId", String.class, "message"),
    GETLINK(Link.class,  "getLink", String.class, "linkId"),
    GETLINK_WITH_OPTIONS(Link.class,  "getLink", String.class, "linkId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETLINKCOMMENTS(ResponseList.class, "getLinkComments", String.class, "linkId"),
    GETLINKCOMMENTS_WITH_OPTIONS(ResponseList.class, "getLinkComments", String.class, "linkId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETLINKLIKES(ResponseList.class, "getLinkLikes", String.class, "linkId"),
    GETLINKLIKES_WITH_OPTIONS(ResponseList.class, "getLinkLikes", String.class, "linkId", Reading.class, FacebookConstants.READING_PPROPERTY),
    LIKELINK(Boolean.class,  "likeLink", String.class, "linkId"),
    UNLIKELINK(Boolean.class,  "unlikeLink", String.class, "linkId"),

    // LocationMethods
    GETLOCATIONS(ResponseList.class, "getLocations"),
    GETLOCATIONS_WITH_OPTIONS(ResponseList.class, "getLocations", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETLOCATIONS_WITH_ID(ResponseList.class, "getLocations", String.class, "userId"),
    GETLOCATIONS_WITH_ID_OPTIONS(ResponseList.class, "getLocations", String.class, "userId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETTAGGEDPLACES(ResponseList.class, "getTaggedPlaces"),
    GETTAGGEDPLACES_OPTIONS(ResponseList.class, "getTaggedPlaces", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETTAGGEDPLACES_WITH_ID(ResponseList.class, "getTaggedPlaces", String.class, "userId"),
    GETTAGGEDPLACES_WITH_ID_OPTIONS(ResponseList.class, "getTaggedPlaces", String.class, "userId", Reading.class, FacebookConstants.READING_PPROPERTY),

    // MessageMethods
    GETINBOX(InboxResponseList.class, "getInbox"),
    GETINBOX_WITH_OPTIONS(InboxResponseList.class, "getInbox", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETINBOX_WITH_ID(InboxResponseList.class, "getInbox", String.class, "userId"),
    GETINBOX_WITH_ID_OPTIONS(InboxResponseList.class, "getInbox", String.class, "userId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETMESSAGE(Message.class,  "getMessage", String.class, "messageId"),
    GETMESSAGE_WITH_OPTIONS(Message.class,  "getMessage", String.class, "messageId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETOUTBOX(ResponseList.class, "getOutbox"),
    GETOUTBOX_WITH_OPTIONS(ResponseList.class, "getOutbox", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETOUTBOX_WITH_ID(ResponseList.class, "getOutbox", String.class, "userId"),
    GETOUTBOX_WITH_ID_OPTIONS(ResponseList.class, "getOutbox", String.class, "userId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETUPDATES(ResponseList.class, "getUpdates"),
    GETUPDATES_WITH_OPTIONS(ResponseList.class, "getUpdates", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETUPDATES_WITH_ID(ResponseList.class, "getUpdates", String.class, "userId"),
    GETUPDATES_WITH_ID_OPTIONS(ResponseList.class, "getUpdates", String.class, "userId", Reading.class, FacebookConstants.READING_PPROPERTY),

    // NoteMethods
    COMMENTNOTE(String.class, "commentNote", String.class, "noteId", String.class, "message"),
    CREATENOTE(String.class, "createNote", String.class, "subject", String.class, "message"),
    CREATENOTE_WITH_ID_MSG(String.class, "createNote", String.class, "userId", String.class, "subject", String.class, "message"),
    GETNOTE(Note.class,  "getNote", String.class, "noteId"),
    GETNOTE_WITH_OPTIONS(Note.class,  "getNote", String.class, "noteId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETNOTECOMMENTS(ResponseList.class, "getNoteComments", String.class, "noteId"),
    GETNOTECOMMENTS_WITH_OPTIONS(ResponseList.class, "getNoteComments", String.class, "noteId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETNOTELIKES(ResponseList.class, "getNoteLikes", String.class, "noteId"),
    GETNOTELIKES_WITH_OPTIONS(ResponseList.class, "getNoteLikes", String.class, "noteId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETNOTES(ResponseList.class, "getNotes"),
    GETNOTES_WITH_OPTIONS(ResponseList.class, "getNotes", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETNOTES_WITH_ID(ResponseList.class, "getNotes", String.class, "userId"),
    GETNOTES_WITH_ID_OPTIONS(ResponseList.class, "getNotes", String.class, "userId", Reading.class, FacebookConstants.READING_PPROPERTY),
    LIKENOTE(Boolean.class,  "likeNote", String.class, "noteId"),
    UNLIKENOTE(Boolean.class,  "unlikeNote", String.class, "noteId"),

    // NotificationMethods
    GETNOTIFICATIONS(ResponseList.class, "getNotifications"),
    GETNOTIFICATIONS_WITH_INCLUDEREAD(ResponseList.class, "getNotifications", boolean.class, "includeRead"),
    GETNOTIFICATIONS_WITH_OPTIONS(ResponseList.class, "getNotifications", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETNOTIFICATIONS_WITH_OPTIONS_INCLUDEREAD(ResponseList.class, "getNotifications", Reading.class, FacebookConstants.READING_PPROPERTY, boolean.class, "includeRead"),
    GETNOTIFICATIONS_WITH_ID(ResponseList.class, "getNotifications", String.class, "userId"),
    GETNOTIFICATIONS_WITH_ID_INCLUDEREAD(ResponseList.class, "getNotifications", String.class, "userId", boolean.class, "includeRead"),
    GETNOTIFICATIONS_WITH_ID_OPTIONS(ResponseList.class, "getNotifications", String.class, "userId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETNOTIFICATIONS_WITH_ID_OPTIONS_INCLUDEREAD(ResponseList.class, "getNotifications", String.class, "userId", Reading.class, FacebookConstants.READING_PPROPERTY, boolean.class, "includeRead"),
    MARKNOTIFICATIONASREAD(Boolean.class,  "markNotificationAsRead", String.class, "notificationId"),

    // PermissionMethods
    DELETEALLPERMISSIONS(Boolean.class,  "deleteAllPermissions"),
    DELETEALLPERMISSIONS_WITH_ID(Boolean.class,  "deleteAllPermissions", String.class, "userId"),
    GETPERMISSIONS(List.class, "getPermissions"),
    GETPERMISSIONS_WITH_ID(List.class, "getPermissions", String.class, "userId"),
    REVOKEPERMISSION(Boolean.class,  "revokePermission", String.class, "permissionName"),
    REVOKEPERMISSION_WITH_ID(Boolean.class,  "revokePermission", String.class, "userId", String.class, "permissionName"),
    REVOKEALLPERMISSIONS(Boolean.class,  "revokeAllPermissions"),
    REVOKEALLPERMISSIONS_WITH_ID(Boolean.class,  "revokeAllPermissions", String.class, "userId"),

    // PhotoMethods
    ADDTAGTOPHOTO(Boolean.class,  "addTagToPhoto", String.class, "photoId", String.class, "toUserId"),
    ADDTAGTOPHOTO_WITH_IDS(Boolean.class,  "addTagToPhoto", String.class, "photoId", List.class, "toUserIds"),
    ADDTAGTOPHOTO_WITH_TAGUPDATE(Boolean.class,  "addTagToPhoto", String.class, "photoId", TagUpdate.class, "tagUpdate"),
    COMMENTPHOTO(String.class, "commentPhoto", String.class, "photoId", String.class, "message"),
    DELETEPHOTO(Boolean.class,  "deletePhoto", String.class, "photoId"),
    GETPHOTO(Photo.class,  "getPhoto", String.class, "photoId"),
    GETPHOTO_WITH_OPTIONS(Photo.class,  "getPhoto", String.class, "photoId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETPHOTOCOMMENTS(ResponseList.class, "getPhotoComments", String.class, "photoId"),
    GETPHOTOCOMMENTS_WITH_OPTIONS(ResponseList.class, "getPhotoComments", String.class, "photoId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETPHOTOLIKES(ResponseList.class, "getPhotoLikes", String.class, "photoId"),
    GETPHOTOLIKES_WITH_OPTIONS(ResponseList.class, "getPhotoLikes", String.class, "photoId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETPHOTOS(ResponseList.class, "getPhotos"),
    GETPHOTOS_WITH_OPTIONS(ResponseList.class, "getPhotos", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETPHOTOS_WITH_ID(ResponseList.class, "getPhotos", String.class, "userId"),
    GETPHOTOS_WITH_ID_OPTIONS(ResponseList.class, "getPhotos", String.class, "userId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETPHOTOURL(URL.class,  "getPhotoURL", String.class, "photoId"),
    GETTAGSONPHOTO(ResponseList.class, "getTagsOnPhoto", String.class, "photoId"),
    GETTAGSONPHOTO_WITH_OPTIONS(ResponseList.class, "getTagsOnPhoto", String.class, "photoId", Reading.class, FacebookConstants.READING_PPROPERTY),
    LIKEPHOTO(Boolean.class,  "likePhoto", String.class, "photoId"),
    POSTPHOTO(String.class, "postPhoto", Media.class, "source"),
    POSTPHOTO_WITH_MEDIA(String.class, "postPhoto", String.class, "userId", Media.class, "source"),
    UNLIKEPHOTO(Boolean.class,  "unlikePhoto", String.class, "photoId"),
    UPDATETAGONPHOTO_WITH_TAGUPDATE(Boolean.class,  "updateTagOnPhoto", String.class, "photoId", TagUpdate.class, "tagUpdate"),

    // PokeMethods
    GETPOKES(ResponseList.class, "getPokes"),
    GETPOKES_WITH_OPTIONS(ResponseList.class, "getPokes", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETPOKES_WITH_ID(ResponseList.class, "getPokes", String.class, "userId"),
    GETPOKES_WITH_ID_OPTIONS(ResponseList.class, "getPokes", String.class, "userId", Reading.class, FacebookConstants.READING_PPROPERTY),

    // PostMethods
    COMMENTPOST(String.class, "commentPost", String.class, "postId", String.class, "message"),
    DELETEPOST(Boolean.class,  "deletePost", String.class, "postId"),
    GETFEED(ResponseList.class, "getFeed"),
    GETFEED_WITH_OPTIONS(ResponseList.class, "getFeed", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETFEED_WITH_ID(ResponseList.class, "getFeed", String.class, "userId"),
    GETFEED_WITH_ID_OPTIONS(ResponseList.class, "getFeed", String.class, "userId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETHOME(ResponseList.class, "getHome"),
    GETHOME_WITH_OPTIONS(ResponseList.class, "getHome", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETLINKS(ResponseList.class, "getLinks"),
    GETLINKS_WITH_OPTIONS(ResponseList.class, "getLinks", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETLINKS_WITH_ID(ResponseList.class, "getLinks", String.class, "userId"),
    GETLINKS_WITH_ID_OPTIONS(ResponseList.class, "getLinks", String.class, "userId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETPOST(Post.class,  "getPost", String.class, "postId"),
    GETPOST_WITH_OPTIONS(Post.class,  "getPost", String.class, "postId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETPOSTCOMMENTS(ResponseList.class, "getPostComments", String.class, "postId"),
    GETPOSTCOMMENTS_WITH_OPTIONS(ResponseList.class, "getPostComments", String.class, "postId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETPOSTLIKES(ResponseList.class, "getPostLikes", String.class, "postId"),
    GETPOSTLIKES_WITH_OPTIONS(ResponseList.class, "getPostLikes", String.class, "postId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETPOSTS(ResponseList.class, "getPosts"),
    GETPOSTS_WITH_OPTIONS(ResponseList.class, "getPosts", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETPOSTS_WITH_ID(ResponseList.class, "getPosts", String.class, "userId"),
    GETPOSTS_WITH_ID_OPTIONS(ResponseList.class, "getPosts", String.class, "userId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETSTATUSES(ResponseList.class, "getStatuses"),
    GETSTATUSES_WITH_OPTIONS(ResponseList.class, "getStatuses", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETSTATUSES_WITH_ID(ResponseList.class, "getStatuses", String.class, "userId"),
    GETSTATUSES_WITH_ID_OPTIONS(ResponseList.class, "getStatuses", String.class, "userId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETTAGGED(ResponseList.class, "getTagged"),
    GETTAGGED_WITH_OPTIONS(ResponseList.class, "getTagged", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETTAGGED_WITH_ID(ResponseList.class, "getTagged", String.class, "userId"),
    GETTAGGED_WITH_ID_OPTIONS(ResponseList.class, "getTagged", String.class, "userId", Reading.class, FacebookConstants.READING_PPROPERTY),
    LIKEPOST(Boolean.class,  "likePost", String.class, "postId"),
    POSTFEED(String.class, "postFeed", PostUpdate.class, "postUpdate"),
    POSTFEED_WITH_POSTUPDATE(String.class, "postFeed", String.class, "userId", PostUpdate.class, "postUpdate"),
    POSTLINK(String.class, "postLink", URL.class, "link"),
    POSTLINK_WITH_MSG(String.class, "postLink", URL.class, "link", String.class, "message"),
    POSTLINK_WITH_ID(String.class, "postLink", String.class, "userId", URL.class, "link"),
    POSTLINK_WITH_ID_MSG(String.class, "postLink", String.class, "userId", URL.class, "link", String.class, "message"),
    POSTSTATUSMESSAGE(String.class, "postStatusMessage", String.class, "message"),
    POSTSTATUSMESSAGE_WITH_ID(String.class, "postStatusMessage", String.class, "userId", String.class, "message"),
    UNLIKEPOST(Boolean.class,  "unlikePost", String.class, "postId"),

    // QuestionMethods
    ADDQUESTIONOPTION(String.class, "addQuestionOption", String.class, "questionId", String.class, "optionDescription"),
    DELETEQUESTION(Boolean.class,  "deleteQuestion", String.class, "questionId"),
    GETQUESTION(Question.class,  "getQuestion", String.class, "questionId"),
    GETQUESTION_WITH_OPTIONS(Question.class,  "getQuestion", String.class, "questionId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETQUESTIONOPTIONS(ResponseList.class, "getQuestionOptions", String.class, "questionId"),
    GETQUESTIONOPTIONS_WITH_OPTIONS(ResponseList.class, "getQuestionOptions", String.class, "questionId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETQUESTIONOPTIONVOTES(ResponseList.class, "getQuestionOptionVotes", String.class, "questionId"),
    GETQUESTIONS(ResponseList.class, "getQuestions"),
    GETQUESTIONS_WITH_OPTIONS(ResponseList.class, "getQuestions", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETQUESTIONS_WITH_ID(ResponseList.class, "getQuestions", String.class, "userId"),
    GETQUESTIONS_WITH_ID_OPTIONS(ResponseList.class, "getQuestions", String.class, "userId", Reading.class, FacebookConstants.READING_PPROPERTY),

    // SubscribeMethods
    GETSUBSCRIBEDTO(ResponseList.class, "getSubscribedto"),
    GETSUBSCRIBEDTO_WITH_OPTIONS(ResponseList.class, "getSubscribedto", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETSUBSCRIBEDTO_WITH_ID(ResponseList.class, "getSubscribedto", String.class, "userId"),
    GETSUBSCRIBEDTO_WITH_ID_OPTIONS(ResponseList.class, "getSubscribedto", String.class, "userId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETSUBSCRIBERS(ResponseList.class, "getSubscribers"),
    GETSUBSCRIBERS_WITH_OPTIONS(ResponseList.class, "getSubscribers", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETSUBSCRIBERS_WITH_ID(ResponseList.class, "getSubscribers", String.class, "userId"),
    GETSUBSCRIBERS_WITH_ID_OPTIONS(ResponseList.class, "getSubscribers", String.class, "userId", Reading.class, FacebookConstants.READING_PPROPERTY),

    // TestUserMethods
    CREATETESTUSER(TestUser.class,  "createTestUser", String.class, "appId"),
    CREATETESTUSER_WITH_NAME(TestUser.class,  "createTestUser", String.class, "appId", String.class, "name", String.class, "userLocale", String.class, "permissions"),
    DELETETESTUSER(Boolean.class,  "deleteTestUser", String.class, "testUserId"),
    GETTESTUSERS(List.class, "getTestUsers", String.class, "appId"),
    MAKEFRIENDTESTUSER(Boolean.class,  "makeFriendTestUser", TestUser.class, "testUser1", TestUser.class, "testUser2"),

    // UserMethods
    GETME(User.class,  "getMe"),
    GETME_WITH_OPTIONS(User.class,  "getMe", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETPICTUREURL(URL.class,  "getPictureURL"),
    GETPICTUREURL_WITH_DIM(URL.class,  "getPictureURL", int.class, "pictureId", int.class, "pictureId2"),
    GETPICTUREURL_WITH_PICTURESIZE(URL.class,  "getPictureURL", PictureSize.class, "size"),
    GETPICTUREURL_WITH_ID(URL.class,  "getPictureURL", String.class, "userId"),
    GETPICTUREURL_WITH_ID_PICTURESIZE(URL.class,  "getPictureURL", String.class, "userId", PictureSize.class, "size"),
    GETPICTUREURL_WITH_ID_AND_DIM(URL.class,  "getPictureURL", String.class, "userId", int.class, "pictureId", int.class, "pictureId2"),
    GETSSLPICTUREURL(URL.class,  "getSSLPictureURL"),
    GETSSLPICTUREURL_WITH_PICTURESIZE(URL.class,  "getSSLPictureURL", PictureSize.class, "size"),
    GETSSLPICTUREURL_WITH_ID(URL.class,  "getSSLPictureURL", String.class, "userId"),
    GETSSLPICTUREURL_WITH_ID_SIZE(URL.class,  "getSSLPictureURL", String.class, "userId", PictureSize.class, "size"),
    GETUSER(User.class,  "getUser", String.class, "userId"),
    GETUSER_WITH_OPTIONS(User.class,  "getUser", String.class, "userId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETUSERS(List.class, "getUsers", new String[0].getClass(), "ids"),

    // VideoMethods
    COMMENTVIDEO(String.class, "commentVideo", String.class, "videoId", String.class, "message"),
    GETVIDEO(Video.class,  "getVideo", String.class, "videoId"),
    GETVIDEO_WITH_OPTIONS(Video.class,  "getVideo", String.class, "videoId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETVIDEOCOMMENTS(ResponseList.class, "getVideoComments", String.class, "videoId"),
    GETVIDEOCOMMENTS_WITH_OPTIONS(ResponseList.class, "getVideoComments", String.class, "videoId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETVIDEOCOVER(URL.class,  "getVideoCover", String.class, "videoId"),
    GETVIDEOLIKES(ResponseList.class, "getVideoLikes", String.class, "videoId"),
    GETVIDEOLIKES_WITH_OPTIONS(ResponseList.class, "getVideoLikes", String.class, "videoId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETVIDEOS(ResponseList.class, "getVideos"),
    GETVIDEOS_WITH_OPTIONS(ResponseList.class, "getVideos", Reading.class, FacebookConstants.READING_PPROPERTY),
    GETVIDEOS_WITH_ID(ResponseList.class, "getVideos", String.class, "userId"),
    GETVIDEOS_WITH_ID_OPTIONS(ResponseList.class, "getVideos", String.class, "userId", Reading.class, FacebookConstants.READING_PPROPERTY),
    LIKEVIDEO(Boolean.class,  "likeVideo", String.class, "videoId"),
    UNLIKEVIDEO(Boolean.class,  "unlikeVideo", String.class, "videoId"),

    // SearchMethods get the highest priority with higher ordinal values
    SEARCH(ResponseList.class, "search", String.class, "query"),
    SEARCH_WITH_OPTIONS(ResponseList.class, "search", String.class, "query", Reading.class, FacebookConstants.READING_PPROPERTY),
    SEARCHCHECKINS(ResponseList.class, "searchCheckins"),
    SEARCHCHECKINS_WITH_OPTIONS(ResponseList.class, "searchCheckins", Reading.class, FacebookConstants.READING_PPROPERTY),
    SEARCHEVENTS(ResponseList.class, "searchEvents", String.class, "query"),
    SEARCHEVENTS_WITH_OPTIONS(ResponseList.class, "searchEvents", String.class, "query", Reading.class, FacebookConstants.READING_PPROPERTY),
    SEARCHGROUPS(ResponseList.class, "searchGroups", String.class, "query"),
    SEARCHGROUPS_WITH_OPTIONS(ResponseList.class, "searchGroups", String.class, "query", Reading.class, FacebookConstants.READING_PPROPERTY),
    SEARCHLOCATIONS(ResponseList.class, "searchLocations", GeoLocation.class, "center", int.class, "distance"),
    SEARCHLOCATIONS_WITH_OPTIONS(ResponseList.class, "searchLocations", GeoLocation.class, "center", int.class, "distance", Reading.class, FacebookConstants.READING_PPROPERTY),
    SEARCHLOCATIONS_WITH_ID(ResponseList.class, "searchLocations", String.class, "placeId"),
    SEARCHLOCATIONS_WITH_ID_OPTIONS(ResponseList.class, "searchLocations", String.class, "placeId", Reading.class, FacebookConstants.READING_PPROPERTY),
    SEARCHPLACES(ResponseList.class, "searchPlaces", String.class, "query"),
    SEARCHPLACES_WITH_OPTIONS(ResponseList.class, "searchPlaces", String.class, "query", Reading.class, FacebookConstants.READING_PPROPERTY),
    SEARCHPLACES_WITH_CENTER(ResponseList.class, "searchPlaces", String.class, "query", GeoLocation.class, "center", int.class, "distance"),
    SEARCHPLACES_WITH_CENTER_OPTIONS(ResponseList.class, "searchPlaces", String.class, "query", GeoLocation.class, "center", int.class, "distance", Reading.class, FacebookConstants.READING_PPROPERTY),
    SEARCHPOSTS(ResponseList.class, "searchPosts", String.class, "query"),
    SEARCHPOSTS_WITH_OPTIONS(ResponseList.class, "searchPosts", String.class, "query", Reading.class, FacebookConstants.READING_PPROPERTY),
    SEARCHUSERS(ResponseList.class, "searchUsers", String.class, "query"),
    SEARCHUSERS_WITH_OPTIONS(ResponseList.class, "searchUsers", String.class, "query", Reading.class, FacebookConstants.READING_PPROPERTY),

    // FROM UPDATE to 2.2.2 API
    BLOCK_USERLIST(Map.class, "block", List.class, "list"),
    BLOCK_USERLIST_WITH_PAGEID(Map.class, "block", String.class, "pageId", List.class, "list"),
    GET_PROMOTABLE_POSTS(ResponseList.class, "getPromotablePosts"),
    GET_PROMOTABLE_POSTS_WITH_PAGEID(ResponseList.class, "getPromotablePosts", String.class, "pageId"),
    GET_PROMOTABLE_POSTS_WITH_READING(ResponseList.class, "getPromotablePosts", Reading.class , FacebookConstants.READING_PPROPERTY),
    GET_PROMOTABLE_POSTS_WITH_PAGEID_AND_READING(ResponseList.class, "getPromotablePosts", String.class, "pageId", Reading.class , FacebookConstants.READING_PPROPERTY),
    POST_PAGE_PHOTO(String.class, "postPagePhoto", PagePhotoUpdate.class , "pagePhotoUpdate"),
    POST_PAGE_PHOTO_WITH_PAGEID(String.class, "postPagePhoto", String.class, "pageId", PagePhotoUpdate.class , "pagePhotoUpdate"),
    GET_PAGE_TAGGED(ResponseList.class, "getPageTagged", String.class, "pageId"),
    GET_PAGE_TAGGED_WITH_READING(ResponseList.class, "getPageTagged", String.class, "pageId", Reading.class , FacebookConstants.READING_PPROPERTY),
    GET_PAGE(Page.class, "getPage"),
    GET_PAGE_WITH_ID(Page.class, "getPage", String.class, "pageId"),
    GET_PAGE_WITH_READING(Page.class, "getPage", Reading.class, FacebookConstants.READING_PPROPERTY),
    GET_PAGE_WITH_ID_AND_READING(Page.class, "getPage", String.class, "pageId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GET_PAGE_PICTURE_URL(URL.class, "getPagePictureURL"),
    GET_PAGE_PICTURE_URL_WITH_SIZE(URL.class, "getPagePictureURL", PictureSize.class, "pictureSize"),
    GET_PAGE_PICTURE_URL_WITH_PAGEID(URL.class, "getPagePictureURL", String.class, "pageId"),
    GET_PAGE_PICTURE_URL_WITH_PAGEID_AND_SIZE(URL.class, "getPagePictureURL", String.class, "pageId", PictureSize.class, "pictureSize"),
    UPDATE_PAGE_BASIC_ATTRIBUTES(Boolean.class, "updatePageBasicAttributes", PageUpdate.class, "pageUpdate"),
    UPDATE_PAGE_BASIC_ATTRIBUTES_WITH_PAGEID(boolean.class, "updatePageBasicAttributes", String.class, "pageId", PageUpdate.class, "pageUpdate"),
    UPDATE_PAGE_PROFILE_PHOTO(boolean.class, "updatePageProfilePhoto", Media.class, "source"),
    UPDATE_PAGE_PROFILE_PHOTO_WITH_PAGEID(boolean.class, "updatePageProfilePhoto", String.class, "pageId", Media.class, "source"),
    UPDATE_PAGE_PROFILE_PHOTO_WITH_PAGEID_AND_URL(boolean.class, "updatePageProfilePhoto", String.class, "pageId", URL.class, "picture"),
    UPDATE_PAGE_PROFILE_PHOTO_WITH_URL(boolean.class, "updatePageProfilePhoto", URL.class, "picture"),
    UPDATE_PAGE_COVER_PHOTO(boolean.class, "updatePageCoverPhoto", PageCoverUpdate.class, "pageCoverUpdate"),
    UPDATE_PAGE_COVER_PHOTO_WITH_PAGEID(boolean.class, "updatePageCoverPhoto", String.class, "pageId", PageCoverUpdate.class, "pageCoverUpdate"),
    DISPLAY_PAGE_POST(boolean.class, "displayPagePost", String.class, "pageId", boolean.class, "isHidden"),
    GET_PAGE_SETTINGS(ResponseList.class, "getPageSettings"),
    GET_PAGE_SETTINGS_WITH_PAGEID(ResponseList.class, "getPageSettings", String.class, "pageId"),
    UPDATE_PAGE_SETTING(boolean.class, "updatePageSetting", PageSettingUpdate.class, "pageSettingUpdate"),
    UPDATE_PAGE_SETTING_WITH_PAGEID(boolean.class, "updatePageSetting", String.class, "pageId", PageSettingUpdate.class, "pageSettingUpdate"),
    POST_BACKDATING_FEED(String.class, "postBackdatingFeed", BackdatingPostUpdate.class, "backdatingPostUpdate"),
    POST_BACKDATING_FEED_WITH_PAGEID(String.class, "postBackdatingFeed", String.class, "pageId", BackdatingPostUpdate.class, "backdatingPostUpdate"),
    GET_GLOBAL_BRAND_CHILDREN(ResponseList.class, "getGlobalBrandChildren", String.class, "pageId"),
    GET_GLOBAL_BRAND_CHILDREN_WITH_READING(ResponseList.class, "getGlobalBrandChildren", String.class, "pageId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GET_PAGE_INSIGHTS(ResponseList.class, "getPageInsights", String.class, "pageId"),
    GET_PAGE_INSIGHTS_WITH_READING(ResponseList.class, "getPageInsights", String.class, "pageId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GET_MILESTONES(ResponseList.class, "getMilestones"),
    GET_MILESTONES_WITH_PAGEID(ResponseList.class, "getMilestones", String.class, "pageId"),
    GET_MILESTONES_WITH_READING(ResponseList.class, "getMilestones", Reading.class, FacebookConstants.READING_PPROPERTY),
    GET_PAGE_INSIGHTS_WITH_PAGEID_AND_READING(ResponseList.class, "getMilestones", String.class, "pageId", Reading.class, FacebookConstants.READING_PPROPERTY),
    CREATE_MILESTONE(String.class, "createMilestone", MilestoneUpdate.class, "milestoneUpdate"),
    CREATE_MILESTONE_WITH_PAGEID(String.class, "createMilestone", String.class, "pageId", MilestoneUpdate.class, "milestoneUpdate"),
    DELETE_MILESTONE(boolean.class, "deleteMilestone", String.class, "milestoneId"),
    GET_PAGE_ADMINS(ResponseList.class, "getPageAdmins"),
    GET_PAGE_ADMINS_WITH_PAGEID(ResponseList.class, "getPageAdmins", String.class, "pageId"),
    GET_PAGE_ADMINS_WITH_READING(ResponseList.class, "getPageAdmins", Reading.class, FacebookConstants.READING_PPROPERTY),
    GET_PAGE_ADMINS_WITH_PAGEID_AND_READING(ResponseList.class, "getPageAdmins", String.class, "pageId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GET_TABS(ResponseList.class, "getTabs"),
    GET_TABS_WITH_PAGEID(ResponseList.class, "getTabs", String.class, "pageId"),
    GET_TABS_WITH_READING(ResponseList.class, "getTabs", Reading.class, FacebookConstants.READING_PPROPERTY),
    GET_TABS_WITH_PAGEID_AND_READING(ResponseList.class, "getTabs", String.class, "pageId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GET_INSTALLED_TABS(ResponseList.class, "getInstalledTabs", List.class, "appIds"),
    GET_INSTALLED_TABS_WITH_PAGEID(ResponseList.class, "getInstalledTabs", String.class, "pageId",  List.class, "appIds"),
    GET_INSTALLED_TABS_WITH_READING(ResponseList.class, "getInstalledTabs",  List.class, "appIds", Reading.class, FacebookConstants.READING_PPROPERTY),
    GET_INSTALLED_TABS_WITH_PAGEID_AND_READING(ResponseList.class, "getInstalledTabs", String.class, "pageId", List.class, "appIds", Reading.class, FacebookConstants.READING_PPROPERTY),
    INSTALL_TAB(boolean.class, "installTab", String.class, "appId"),
    INSTALL_TAB_WITH_PAGEID(boolean.class, "installTab", String.class, "pageId", String.class, "appId"),
    UPDATE_TAB(boolean.class, "updateTab", String.class, "tabId", TabUpdate.class, "tabUpdate"),
    UPDATE_TAB_WITH_PAGEID(boolean.class, "updateTab", String.class, "pageId", String.class, "tabId", TabUpdate.class, "tabUpdate"),
    DELETE_TAB(boolean.class, "deleteTab", String.class, "tabId"),
    DELETE_TAB_WITH_PAGEID(boolean.class, "deleteTab", String.class, "pageId", String.class, "tabId"),
    GET_BLOCKED(ResponseList.class, "getBlocked"),
    GET_BLOCKED_WITH_PAGEID(ResponseList.class, "getBlocked", String.class, "pageId"),
    GET_BLOCKEDS_WITH_READING(ResponseList.class, "getBlocked", Reading.class, FacebookConstants.READING_PPROPERTY),
    GET_BLOCKED_WITH_PAGEID_AND_READING(ResponseList.class, "getBlocked", String.class, "pageId", Reading.class, FacebookConstants.READING_PPROPERTY),
    UNBLOCK(boolean.class, "unblock", String.class, "userId"),
    UNBLOCK_WITH_PAGEID(boolean.class, "unblock", String.class, "pageId", String.class, "userId"),
    GET_OFFERS(ResponseList.class, "getOffers"),
    GET_OFFERS_WITH_PAGEID(ResponseList.class, "getOffers", String.class, "pageId"),
    GET_OFFERS_WITH_READING(ResponseList.class, "getOffers", Reading.class, FacebookConstants.READING_PPROPERTY),
    GET_OFFERS_WITH_PAGEID_AND_READING(ResponseList.class, "getOffers", String.class, "pageId", Reading.class, FacebookConstants.READING_PPROPERTY),
    CREATE_OFFER(String.class, "createOffer", OfferUpdate.class, "offerUpdate"),
    CREATE_OFFER_WITH_PAGEID(ResponseList.class, "createOffer", String.class, "pageId", OfferUpdate.class, "offerUpdate"),
    DELETE_OFFER(boolean.class, "deleteOffer", String.class, "offerId"),
    GET_OFFER(Offer.class, "getOffer", String.class, "offerId"),
    GET_LIKED_PAGE(Page.class, "getLikedPage", String.class, "pageId"),
    GET_LIKED_PAGE_WITH_READING(Page.class, "getLikedPage", String.class, "pageId", Reading.class, FacebookConstants.READING_PPROPERTY),
    GET_LIKED_PAGE_WITH_USERID(Page.class, "getLikedPage", String.class, "userId", String.class, "pageId"),
    GET_LIKED_PAGE_WITH_USERID_AND_READING(Page.class, "getLikedPage", String.class, "userId", String.class, "pageId", Reading.class, FacebookConstants.READING_PPROPERTY),
    DELETE_PERMISSION(boolean.class, "deletePermission", String.class, "permissionName"),
    DELETE_PERMISSION_WITH_USERID(boolean.class, "deletePermission", String.class, "userId", String.class, "permissionName"),
    GET_UPLOADED_PHOTOS(ResponseList.class, "getUploadedPhotos"),
    GET_UPLOADED_PHOTOS_WITH_PAGEID(ResponseList.class, "getUploadedPhotos", String.class, "pageId"),
    GET_UPLOADED_PHOTOS_WITH_READING(ResponseList.class, "getUploadedPhotos", Reading.class, FacebookConstants.READING_PPROPERTY),
    GET_UPLOADED_PHOTOS_WITH_PAGEID_AND_READING(ResponseList.class, "getUploadedPhotos", String.class, "pageId", Reading.class, FacebookConstants.READING_PPROPERTY),
    POSTPHOTO_UPDATE(String.class, "postPhoto", PhotoUpdate.class, "photoUpdate"),
    POSTPHOTO_UPDATE_WITH_USERID(String.class, "postPhoto", String.class, "userId", PhotoUpdate.class, "photoUpdate"),
    DELETE_TAG_ON_PHOTO(boolean.class, "deleteTagOnPhoto", String.class, "photoId", String.class, "toUserId"),
    CREATE_QUESTION(String.class, "createQuestion", QuestionUpdate.class, "questionUpdate"),
    CREATE_QUESTION_WITH_ID(String.class, "createQuestion", String.class, "id", QuestionUpdate.class, "questionUpdate"),
    POST_VIDEO(String.class, "postVideo", VideoUpdate.class, "videoUpdate"),
    POST_VIDEO_WITH_ID(String.class, "postVideo", String.class, "id", VideoUpdate.class, "videoUpdate"),
    SEARCH_PAGES(ResponseList.class, "searchPages", String.class, "query"),
    SEARCH_PAGES_WITH_READING(ResponseList.class, "searchPages", String.class, "query", Reading.class, FacebookConstants.READING_PPROPERTY),
    CREATETESTUSER_WITH_INSTALLED(TestUser.class,  "createTestUser", String.class, "appId", String.class, "name", String.class, "userLocale", String.class, "permissions", boolean.class, "installed"),
    GETTESTUSERS_WITH_LIMIT(ResponseList.class, "getTestUsers", String.class, "appId", Integer.class, "limit"),
    EXECUTE_BATCH(List.class, "executeBatch", BatchRequests.class, "requests"),
    CALL_POST_API(RawAPIResponse.class, "callPostAPI", String.class, "relativeUrl"),
    CALL_POST_API_WITH_PARAMS(RawAPIResponse.class, "callPostAPI", String.class, "relativeUrl", facebook4j.internal.http.HttpParameter[].class, "parametersH"),
    CALL_POST_API_WITH_MAP(RawAPIResponse.class, "callPostAPI", String.class, "relativeUrl", Map.class, "parameters"),
    CALL_GET_API(RawAPIResponse.class, "callGetAPI", String.class, "relativeUrl"),
    CALL_GET_API_WITH_PARAMS(RawAPIResponse.class, "callGetAPI", String.class, "relativeUrl", facebook4j.internal.http.HttpParameter[].class, "parametersH"),
    CALL_GET_API_WITH_MAP(RawAPIResponse.class, "callGetAPI", String.class, "relativeUrl", Map.class, "parameters"),
    CALL_DELETE_API(RawAPIResponse.class, "callDeleteAPI", String.class, "relativeUrl"),
    CALL_DELETE_API_WITH_MAP(RawAPIResponse.class, "callDeleteAPI", String.class, "relativeUrl", Map.class, "parameters");
    
    // name, result class, ordered argument names and classes, and Method to invoke
    private final String name;
    private final Class<?> resultType;
    private final List<String> argNames;
    private final List<Class<?>> argTypes;
    private final Method method;

    private FacebookMethodsType(Class<?> resultType, String name, Object... args) throws IllegalArgumentException {
        this.name = name;
        this.resultType = resultType;

        if (args.length % 2 != 0) {
            throw new IllegalArgumentException("Invalid parameter list, "
                + "must be of the form 'Class arg1, String arg1Name, Class arg2, String arg2Name...");
        }
        int nArgs = args.length / 2;
        this.argNames = new ArrayList<String>(nArgs);
        this.argTypes = new ArrayList<Class<?>>(nArgs);
        for (int i = 0; i < nArgs; i++) {
            this.argTypes.add((Class<?>) args[i * 2]);
            this.argNames.add((String) args[i * 2 + 1]);
        }

        // find method in Facebook type
        try {
            this.method = Facebook.class.getMethod(name, argTypes.toArray(new Class[nArgs]));
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(
                String.format("Missing method %s %s", name, argTypes.toString().replace('[', '(').replace(']', ')')),
                e);
        }
    }

    /**
     * Find method type by name and argument types.
     * @param name method name
     * @param args ordered argument types
     * @return matching method, null if not found
     */
    public static FacebookMethodsType findMethod(String name, Class<?>... args) {
        for (FacebookMethodsType method : values()) {
            if (method.name.equals(name)) {
                if ((method.argTypes.isEmpty() && (args == null || args.length == 0))
                    || Arrays.equals(method.argTypes.toArray(), args)) {
                    return method;
                }
            }
        }

        return null;
    }

    public String getName() {
        return name;
    }

    public Class<?> getResultType() {
        return resultType;
    }

    public List<String> getArgNames() {
        return Collections.unmodifiableList(argNames);
    }

    public List<Class<?>> getArgTypes() {
        return Collections.unmodifiableList(argTypes);
    }

    public Method getMethod() {
        return method;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("{")
            .append("name=").append(name)
            .append(", resultType=").append(resultType)
            .append(", argNames=").append(argNames)
            .append(", argTypes=").append(argTypes)
            .append("}");
        return builder.toString();
    }

}
