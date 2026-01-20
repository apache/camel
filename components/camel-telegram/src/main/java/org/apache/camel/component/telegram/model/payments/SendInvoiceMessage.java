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

package org.apache.camel.component.telegram.model.payments;

import java.io.Serial;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.camel.component.telegram.model.OutgoingMessage;
import org.apache.camel.component.telegram.model.ReplyMarkup;

/**
 * Object to send invoices.
 *
 * @see <a href="https://core.telegram.org/bots/api#sendinvoice"> https://core.telegram.org/bots/api#sendinvoice</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SendInvoiceMessage extends OutgoingMessage {

    @Serial
    private static final long serialVersionUID = 2243358307498153269L;

    @JsonProperty("message_thread_id")
    protected Integer messageThreadId;

    @JsonProperty("direct_messages_topic_id")
    protected Integer directMessagesTopicId;

    private String title;

    private String description;

    private String payload;

    @JsonProperty("provider_token")
    private String providerToken;

    private String currency;

    private List<LabeledPrice> prices;

    @JsonProperty("max_tip_amount")
    private Integer maxTipAmount;

    @JsonProperty("suggested_tip_amounts")
    private List<Integer> suggestedTipAmounts;

    @JsonProperty("start_parameter")
    private String startParameter;

    @JsonProperty("provider_data")
    private String providerData;

    @JsonProperty("photo_url")
    private String photoUrl;

    @JsonProperty("photo_size")
    private Integer photoSize;

    @JsonProperty("photo_width")
    private Integer photoWidth;

    @JsonProperty("photo_height")
    private Integer photoHeight;

    @JsonProperty("need_name")
    private Boolean needName;

    @JsonProperty("need_phone_number")
    private Boolean needPhoneNumber;

    @JsonProperty("need_email")
    private Boolean needEmail;

    @JsonProperty("need_shipping_address")
    private Boolean needShippingAddress;

    @JsonProperty("send_phone_number_to_provider")
    private Boolean sendPhoneNumberToProvider;

    @JsonProperty("send_email_to_provider")
    private Boolean sendEmailToProvider;

    @JsonProperty("is_flexible")
    private Boolean isFlexible;

    @JsonProperty("protect_content")
    private Boolean protectContent;

    @JsonProperty("allow_paid_broadcast")
    private Boolean allowPaidBroadcast;

    @JsonProperty("message_effect_id")
    private String messageEffectId;

    @JsonProperty("reply_markup")
    private ReplyMarkup replyMarkup;

    public SendInvoiceMessage() {
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getProviderToken() {
        return providerToken;
    }

    public void setProviderToken(String providerToken) {
        this.providerToken = providerToken;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public List<LabeledPrice> getPrices() {
        return prices;
    }

    public void setPrices(List<LabeledPrice> prices) {
        this.prices = prices;
    }

    public String getStartParameter() {
        return startParameter;
    }

    public void setStartParameter(String startParameter) {
        this.startParameter = startParameter;
    }

    public String getProviderData() {
        return providerData;
    }

    public void setProviderData(String providerData) {
        this.providerData = providerData;
    }

    public Boolean getNeedPhoneNumber() {
        return needPhoneNumber;
    }

    public void setNeedPhoneNumber(Boolean needPhoneNumber) {
        this.needPhoneNumber = needPhoneNumber;
    }

    public Boolean getNeedEmail() {
        return needEmail;
    }

    public void setNeedEmail(Boolean needEmail) {
        this.needEmail = needEmail;
    }

    public Boolean getNeedShippingAddress() {
        return needShippingAddress;
    }

    public void setNeedShippingAddress(Boolean needShippingAddress) {
        this.needShippingAddress = needShippingAddress;
    }

    public Boolean getSendPhoneNumberToProvider() {
        return sendPhoneNumberToProvider;
    }

    public void setSendPhoneNumberToProvider(Boolean sendPhoneNumberToProvider) {
        this.sendPhoneNumberToProvider = sendPhoneNumberToProvider;
    }

    public Boolean getSendEmailToProvider() {
        return sendEmailToProvider;
    }

    public void setSendEmailToProvider(Boolean sendEmailToProvider) {
        this.sendEmailToProvider = sendEmailToProvider;
    }

    public Integer getMaxTipAmount() {
        return maxTipAmount;
    }

    public void setMaxTipAmount(Integer maxTipAmount) {
        this.maxTipAmount = maxTipAmount;
    }

    public List<Integer> getSuggestedTipAmounts() {
        return suggestedTipAmounts;
    }

    public void setSuggestedTipAmounts(List<Integer> suggestedTipAmounts) {
        this.suggestedTipAmounts = suggestedTipAmounts;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public Integer getPhotoSize() {
        return photoSize;
    }

    public void setPhotoSize(Integer photoSize) {
        this.photoSize = photoSize;
    }

    public Integer getPhotoWidth() {
        return photoWidth;
    }

    public void setPhotoWidth(Integer photoWidth) {
        this.photoWidth = photoWidth;
    }

    public Integer getPhotoHeight() {
        return photoHeight;
    }

    public void setPhotoHeight(Integer photoHeight) {
        this.photoHeight = photoHeight;
    }

    public Boolean getNeedName() {
        return needName;
    }

    public void setNeedName(Boolean needName) {
        this.needName = needName;
    }

    public Boolean getFlexible() {
        return isFlexible;
    }

    public void setFlexible(Boolean flexible) {
        isFlexible = flexible;
    }

    public Boolean getProtectContent() {
        return protectContent;
    }

    public void setProtectContent(Boolean protectContent) {
        this.protectContent = protectContent;
    }

    public Boolean getAllowPaidBroadcast() {
        return allowPaidBroadcast;
    }

    public void setAllowPaidBroadcast(Boolean allowPaidBroadcast) {
        this.allowPaidBroadcast = allowPaidBroadcast;
    }

    public String getMessageEffectId() {
        return messageEffectId;
    }

    public void setMessageEffectId(String messageEffectId) {
        this.messageEffectId = messageEffectId;
    }

    public ReplyMarkup getReplyMarkup() {
        return replyMarkup;
    }

    public void setReplyMarkup(ReplyMarkup replyMarkup) {
        this.replyMarkup = replyMarkup;
    }

    public Integer getMessageThreadId() {
        return messageThreadId;
    }

    public void setMessageThreadId(Integer messageThreadId) {
        this.messageThreadId = messageThreadId;
    }

    public Integer getDirectMessagesTopicId() {
        return directMessagesTopicId;
    }

    public void setDirectMessagesTopicId(Integer directMessagesTopicId) {
        this.directMessagesTopicId = directMessagesTopicId;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("SendInvoiceMessage{");
        sb.append("chatId=").append(chatId);
        sb.append(", messageThreadId=").append(messageThreadId);
        sb.append(", directMessagesTopicId=").append(directMessagesTopicId);
        sb.append(", title='").append(title).append('\'');
        sb.append(", description='").append(description).append('\'');
        sb.append(", payload='").append(payload).append('\'');
        sb.append(", providerToken='").append(providerToken).append('\'');
        sb.append(", currency='").append(currency).append('\'');
        sb.append(", prices=").append(prices);
        sb.append(", maxTipAmount=").append(maxTipAmount);
        sb.append(", suggestedTipAmounts=").append(suggestedTipAmounts);
        sb.append(", startParameter='").append(startParameter).append('\'');
        sb.append(", providerData='").append(providerData).append('\'');
        sb.append(", photoUrl='").append(photoUrl).append('\'');
        sb.append(", photoSize=").append(photoSize);
        sb.append(", photoWidth=").append(photoWidth);
        sb.append(", photoHeight=").append(photoHeight);
        sb.append(", needName=").append(needName);
        sb.append(", needPhoneNumber=").append(needPhoneNumber);
        sb.append(", needEmail=").append(needEmail);
        sb.append(", needShippingAddress=").append(needShippingAddress);
        sb.append(", sendPhoneNumberToProvider=").append(sendPhoneNumberToProvider);
        sb.append(", sendEmailToProvider=").append(sendEmailToProvider);
        sb.append(", isFlexible=").append(isFlexible);
        sb.append(", protectContent=").append(protectContent);
        sb.append(", allowPaidBroadcast=").append(allowPaidBroadcast);
        sb.append(", messageEffectId='").append(messageEffectId).append('\'');
        sb.append(", replyMarkup=").append(replyMarkup);
        sb.append('}');
        return sb.toString();
    }
}
