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
public class Message {

    @JsonProperty("replied_to_id")
    private String repliedToId;
    @JsonProperty("network_id")
    private Long networkId;
    private String url;
    @JsonProperty("thread_id")
    private Long threadId;
    private Long id;
    @JsonProperty("message_type")
    private String messageType;
    @JsonProperty("chat_client_sequence")
    private String chatClientSequence;
    private Body body;
    @JsonProperty("client_url")
    private String clientUrl;
    @JsonProperty("content_excerpt")
    private String contentExcerpt;
    @JsonProperty("created_at")
    private String createdAt;
    @JsonProperty("client_type")
    private String clientType;
    private String privacy;
    @JsonProperty("sender_type")
    private String senderType;
    @JsonProperty("liked_by")
    private LikedBy likedBy;
    @JsonProperty("sender_id")
    private Long senderId;
    private String language;
    @JsonProperty("system_message")
    private Boolean systemMessage;
    private List<Attachment> attachments;
    @JsonProperty("direct_message")
    private Boolean directMessage;
    @JsonProperty("web_url")
    private String webUrl;

    public String getRepliedToId() {
        return repliedToId;
    }

    public void setRepliedToId(String repliedToId) {
        this.repliedToId = repliedToId;
    }

    public Long getNetworkId() {
        return networkId;
    }

    public void setNetworkId(Long networkId) {
        this.networkId = networkId;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Long getThreadId() {
        return threadId;
    }

    public void setThreadId(Long threadId) {
        this.threadId = threadId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getChatClientSequence() {
        return chatClientSequence;
    }

    public void setChatClientSequence(String chatClientSequence) {
        this.chatClientSequence = chatClientSequence;
    }

    public Body getBody() {
        return body;
    }

    public void setBody(Body body) {
        this.body = body;
    }

    public String getClientUrl() {
        return clientUrl;
    }

    public void setClientUrl(String clientUrl) {
        this.clientUrl = clientUrl;
    }

    public String getContentExcerpt() {
        return contentExcerpt;
    }

    public void setContentExcerpt(String contentExcerpt) {
        this.contentExcerpt = contentExcerpt;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getClientType() {
        return clientType;
    }

    public void setClientType(String clientType) {
        this.clientType = clientType;
    }

    public String getPrivacy() {
        return privacy;
    }

    public void setPrivacy(String privacy) {
        this.privacy = privacy;
    }

    public String getSenderType() {
        return senderType;
    }

    public void setSenderType(String senderType) {
        this.senderType = senderType;
    }

    public LikedBy getLikedBy() {
        return likedBy;
    }

    public void setLikedBy(LikedBy likedBy) {
        this.likedBy = likedBy;
    }

    public Long getSenderId() {
        return senderId;
    }

    public void setSenderId(Long senderId) {
        this.senderId = senderId;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public Boolean getSystemMessage() {
        return systemMessage;
    }

    public void setSystemMessage(Boolean systemMessage) {
        this.systemMessage = systemMessage;
    }

    public List<Attachment> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<Attachment> attachments) {
        this.attachments = attachments;
    }

    public Boolean getDirectMessage() {
        return directMessage;
    }

    public void setDirectMessage(Boolean directMessage) {
        this.directMessage = directMessage;
    }

    public String getWebUrl() {
        return webUrl;
    }

    public void setWebUrl(String webUrl) {
        this.webUrl = webUrl;
    }

    @Override
    public String toString() {
        return "Message [repliedToId=" + repliedToId + ", networkId=" + networkId + ", url=" + url + ", threadId=" + threadId + ", id=" + id + ", messageType=" + messageType + ", chatClientSequence="
                + chatClientSequence + ", body=" + body + ", clientUrl=" + clientUrl + ", contentExcerpt=" + contentExcerpt + ", createdAt=" + createdAt + ", clientType=" + clientType + ", privacy="
                + privacy + ", senderType=" + senderType + ", likedBy=" + likedBy + ", senderId=" + senderId + ", language=" + language + ", systemMessage=" + systemMessage + ", attachments="
                + attachments + ", directMessage=" + directMessage + ", webUrl=" + webUrl + "]";
    }
    
}
