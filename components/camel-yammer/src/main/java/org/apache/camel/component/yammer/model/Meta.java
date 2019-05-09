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
package org.apache.camel.component.yammer.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Meta {

    @JsonProperty("feed_desc")
    private String feedDesc;
    @JsonProperty("current_user_id")
    private Long currentUserId;
    @JsonProperty("requested_poll_interval")
    private Long requestedPollInterval;
    @JsonProperty("older_available")
    private Boolean olderAvailable;
    @JsonProperty("followed_references")
    private List<FollowedReference> followedReferences;
    private List<String> ymodules;
    @JsonProperty("liked_message_ids")
    private List<Long> likedMessageIds;
    @JsonProperty("feed_name")
    private String feedName;
    private Realtime realtime;
    @JsonProperty("direct_from_body")
    private Boolean directFromBody;

    public String getFeedDesc() {
        return feedDesc;
    }

    public void setFeedDesc(String feedDesc) {
        this.feedDesc = feedDesc;
    }

    public Long getCurrentUserId() {
        return currentUserId;
    }

    public void setCurrentUserId(Long currentUserId) {
        this.currentUserId = currentUserId;
    }

    public Long getRequestedPollInterval() {
        return requestedPollInterval;
    }

    public void setRequestedPollInterval(Long requestedPollInterval) {
        this.requestedPollInterval = requestedPollInterval;
    }

    public Boolean getOlderAvailable() {
        return olderAvailable;
    }

    public void setOlderAvailable(Boolean olderAvailable) {
        this.olderAvailable = olderAvailable;
    }

    public List<FollowedReference> getFollowedReferences() {
        return followedReferences;
    }

    public void setFollowedReferences(List<FollowedReference> followedReferences) {
        this.followedReferences = followedReferences;
    }

    public List<String> getYmodules() {
        return ymodules;
    }

    public void setYmodules(List<String> ymodules) {
        this.ymodules = ymodules;
    }

    public List<Long> getLikedMessageIds() {
        return likedMessageIds;
    }

    public void setLikedMessageIds(List<Long> likedMessageIds) {
        this.likedMessageIds = likedMessageIds;
    }

    public String getFeedName() {
        return feedName;
    }

    public void setFeedName(String feedName) {
        this.feedName = feedName;
    }

    public Realtime getRealtime() {
        return realtime;
    }

    public void setRealtime(Realtime realtime) {
        this.realtime = realtime;
    }

    public Boolean getDirectFromBody() {
        return directFromBody;
    }

    public void setDirectFromBody(Boolean directFromBody) {
        this.directFromBody = directFromBody;
    }

    @Override
    public String toString() {
        return "Meta [feedDesc=" + feedDesc + ", currentUserId=" + currentUserId + ", requestedPollInterval=" + requestedPollInterval + ", olderAvailable=" + olderAvailable + ", followedReferences="
                + followedReferences + ", ymodules=" + ymodules + ", likedMessageIds=" + likedMessageIds + ", feedName=" + feedName + ", realtime=" + realtime + ", directFromBody=" + directFromBody
                + "]";
    }

}
