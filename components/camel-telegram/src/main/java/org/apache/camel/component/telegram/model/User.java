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
package org.apache.camel.component.telegram.model;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * This object represents a Telegram user or bot.
 *
 * @see <a href="https://core.telegram.org/bots/api#user">https://core.telegram.org/bots/api#user</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class User implements Serializable {

    private static final long serialVersionUID = -6233453333504612269L;

    private Long id;

    @JsonProperty("first_name")
    private String firstName;

    @JsonProperty("last_name")
    private String lastName;

    private String username;

    @JsonProperty("is_bot")
    private boolean isBot;

    @JsonProperty("language_code")
    private String languageCode;

    @JsonProperty("is_premium")
    private Boolean isPremium;

    @JsonProperty("added_to_attachment_menu")
    private Boolean addedToAttachmentMenu;

    @JsonProperty("can_join_groups")
    private Boolean canJoinGroups;

    @JsonProperty("can_read_all_group_messages")
    private Boolean canReadAllGroupMessages;

    @JsonProperty("supports_inline_queries")
    private Boolean supportsInlineQueries;

    @JsonProperty("can_connect_to_business")
    private Boolean canConnectToBusiness;

    @JsonProperty("has_main_web_app")
    private Boolean hasMainWebApp;

    @JsonProperty("has_topics_enabled")
    private Boolean hasTopicsEnabled;

    public User() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public boolean isBot() {
        return isBot;
    }

    public void setBot(boolean bot) {
        isBot = bot;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public void setLanguageCode(String languageCode) {
        this.languageCode = languageCode;
    }

    public Boolean getIsPremium() {
        return isPremium;
    }

    public void setIsPremium(Boolean isPremium) {
        this.isPremium = isPremium;
    }

    public Boolean getAddedToAttachmentMenu() {
        return addedToAttachmentMenu;
    }

    public void setAddedToAttachmentMenu(Boolean addedToAttachmentMenu) {
        this.addedToAttachmentMenu = addedToAttachmentMenu;
    }

    public Boolean getCanJoinGroups() {
        return canJoinGroups;
    }

    public void setCanJoinGroups(Boolean canJoinGroups) {
        this.canJoinGroups = canJoinGroups;
    }

    public Boolean getCanReadAllGroupMessages() {
        return canReadAllGroupMessages;
    }

    public void setCanReadAllGroupMessages(Boolean canReadAllGroupMessages) {
        this.canReadAllGroupMessages = canReadAllGroupMessages;
    }

    public Boolean getSupportsInlineQueries() {
        return supportsInlineQueries;
    }

    public void setSupportsInlineQueries(Boolean supportsInlineQueries) {
        this.supportsInlineQueries = supportsInlineQueries;
    }

    public Boolean getCanConnectToBusiness() {
        return canConnectToBusiness;
    }

    public void setCanConnectToBusiness(Boolean canConnectToBusiness) {
        this.canConnectToBusiness = canConnectToBusiness;
    }

    public Boolean getHasMainWebApp() {
        return hasMainWebApp;
    }

    public void setHasMainWebApp(Boolean hasMainWebApp) {
        this.hasMainWebApp = hasMainWebApp;
    }

    public Boolean getHasTopicsEnabled() {
        return hasTopicsEnabled;
    }

    public void setHasTopicsEnabled(Boolean hasTopicsEnabled) {
        this.hasTopicsEnabled = hasTopicsEnabled;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("User{");
        sb.append("id=").append(id);
        sb.append(", is_bot='").append(isBot).append('\'');
        sb.append(", firstName='").append(firstName).append('\'');
        sb.append(", lastName='").append(lastName).append('\'');
        sb.append(", username='").append(username).append('\'');
        sb.append(", languageCode='").append(languageCode).append('\'');
        sb.append(", isPremium=").append(isPremium);
        sb.append(", addedToAttachmentMenu=").append(addedToAttachmentMenu);
        sb.append(", canJoinGroups=").append(canJoinGroups);
        sb.append(", canReadAllGroupMessages=").append(canReadAllGroupMessages);
        sb.append(", supportsInlineQueries=").append(supportsInlineQueries);
        sb.append(", canConnectToBusiness=").append(canConnectToBusiness);
        sb.append(", hasMainWebApp=").append(hasMainWebApp);
        sb.append(", hasTopicsEnabled=").append(hasTopicsEnabled);
        sb.append('}');
        return sb.toString();
    }
}
