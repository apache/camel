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

import java.io.Serial;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * This object represents a gift that can be sent by the bot.
 *
 * @see <a href="https://core.telegram.org/bots/api#gift">https://core.telegram.org/bots/api#gift</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Gift implements Serializable {

    @Serial
    private static final long serialVersionUID = 1472694263846269401L;

    /**
     * Unique identifier of the gift.
     */
    private String id;

    /**
     * The sticker that represents the gift.
     */
    private IncomingSticker sticker;

    /**
     * The number of Telegram Stars that must be paid to send the sticker.
     */
    @JsonProperty("star_count")
    private Integer starCount;

    /**
     * The number of Telegram Stars that must be paid to upgrade the gift to a unique one.
     */
    @JsonProperty("upgrade_star_count")
    private Integer upgradeStarCount;

    /**
     * True, if the gift can be purchased only by Telegram Premium subscribers.
     */
    @JsonProperty("is_premium")
    private Boolean isPremium;

    /**
     * True, if the gift is a avatar decoration that can customize the user's appearance.
     */
    @JsonProperty("has_colors")
    private Boolean hasColors;

    /**
     * The total number of the gifts of this type that can be sent; for limited gifts only.
     */
    @JsonProperty("total_count")
    private Integer totalCount;

    /**
     * The number of remaining gifts of this type that can be sent; for limited gifts only.
     */
    @JsonProperty("remaining_count")
    private Integer remainingCount;

    /**
     * The total number of the gifts of this type that can be sent by the bot; for limited gifts only.
     */
    @JsonProperty("personal_total_count")
    private Integer personalTotalCount;

    /**
     * The number of remaining gifts of this type that can be sent by the bot; for limited gifts only.
     */
    @JsonProperty("personal_remaining_count")
    private Integer personalRemainingCount;

    /**
     * The gift's background.
     */
    private GiftBackground background;

    /**
     * The total number of unique gift variants that can be upgraded from this gift.
     */
    @JsonProperty("unique_gift_variant_count")
    private Integer uniqueGiftVariantCount;

    /**
     * Information about the chat that published the gift.
     */
    @JsonProperty("publisher_chat")
    private Chat publisherChat;

    public Gift() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public IncomingSticker getSticker() {
        return sticker;
    }

    public void setSticker(IncomingSticker sticker) {
        this.sticker = sticker;
    }

    public Integer getStarCount() {
        return starCount;
    }

    public void setStarCount(Integer starCount) {
        this.starCount = starCount;
    }

    public Integer getUpgradeStarCount() {
        return upgradeStarCount;
    }

    public void setUpgradeStarCount(Integer upgradeStarCount) {
        this.upgradeStarCount = upgradeStarCount;
    }

    public Boolean getIsPremium() {
        return isPremium;
    }

    public void setIsPremium(Boolean isPremium) {
        this.isPremium = isPremium;
    }

    public Boolean getHasColors() {
        return hasColors;
    }

    public void setHasColors(Boolean hasColors) {
        this.hasColors = hasColors;
    }

    public Integer getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Integer totalCount) {
        this.totalCount = totalCount;
    }

    public Integer getRemainingCount() {
        return remainingCount;
    }

    public void setRemainingCount(Integer remainingCount) {
        this.remainingCount = remainingCount;
    }

    public Integer getPersonalTotalCount() {
        return personalTotalCount;
    }

    public void setPersonalTotalCount(Integer personalTotalCount) {
        this.personalTotalCount = personalTotalCount;
    }

    public Integer getPersonalRemainingCount() {
        return personalRemainingCount;
    }

    public void setPersonalRemainingCount(Integer personalRemainingCount) {
        this.personalRemainingCount = personalRemainingCount;
    }

    public GiftBackground getBackground() {
        return background;
    }

    public void setBackground(GiftBackground background) {
        this.background = background;
    }

    public Integer getUniqueGiftVariantCount() {
        return uniqueGiftVariantCount;
    }

    public void setUniqueGiftVariantCount(Integer uniqueGiftVariantCount) {
        this.uniqueGiftVariantCount = uniqueGiftVariantCount;
    }

    public Chat getPublisherChat() {
        return publisherChat;
    }

    public void setPublisherChat(Chat publisherChat) {
        this.publisherChat = publisherChat;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Gift{");
        sb.append("id='").append(id).append('\'');
        sb.append(", sticker=").append(sticker);
        sb.append(", starCount=").append(starCount);
        sb.append(", upgradeStarCount=").append(upgradeStarCount);
        sb.append(", isPremium=").append(isPremium);
        sb.append(", hasColors=").append(hasColors);
        sb.append(", totalCount=").append(totalCount);
        sb.append(", remainingCount=").append(remainingCount);
        sb.append(", personalTotalCount=").append(personalTotalCount);
        sb.append(", personalRemainingCount=").append(personalRemainingCount);
        sb.append(", background=").append(background);
        sb.append(", uniqueGiftVariantCount=").append(uniqueGiftVariantCount);
        sb.append(", publisherChat=").append(publisherChat);
        sb.append('}');
        return sb.toString();
    }
}
