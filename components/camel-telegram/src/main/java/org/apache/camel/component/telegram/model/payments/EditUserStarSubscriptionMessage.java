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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.camel.component.telegram.TelegramMessage;

/**
 * Allows the bot to cancel or re-enable extension of a subscription paid in Telegram Stars. Returns True on success.
 *
 * @see <a href=
 *      "https://core.telegram.org/bots/api#edituserstarsubscription">https://core.telegram.org/bots/api#edituserstarsubscription</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EditUserStarSubscriptionMessage implements TelegramMessage {

    @Serial
    private static final long serialVersionUID = 3817353864912714422L;

    /**
     * Identifier of the user whose subscription will be edited.
     */
    @JsonProperty("user_id")
    private Long userId;

    /**
     * Telegram payment identifier for the subscription.
     */
    @JsonProperty("telegram_payment_charge_id")
    private String telegramPaymentChargeId;

    /**
     * Pass True to cancel extension of the user subscription; the subscription must be active up to the end of the
     * current subscription period. Pass False to allow the user to re-enable it.
     */
    @JsonProperty("is_canceled")
    private Boolean isCanceled;

    public EditUserStarSubscriptionMessage() {
    }

    public EditUserStarSubscriptionMessage(Long userId, String telegramPaymentChargeId, Boolean isCanceled) {
        this.userId = userId;
        this.telegramPaymentChargeId = telegramPaymentChargeId;
        this.isCanceled = isCanceled;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getTelegramPaymentChargeId() {
        return telegramPaymentChargeId;
    }

    public void setTelegramPaymentChargeId(String telegramPaymentChargeId) {
        this.telegramPaymentChargeId = telegramPaymentChargeId;
    }

    public Boolean getIsCanceled() {
        return isCanceled;
    }

    public void setIsCanceled(Boolean isCanceled) {
        this.isCanceled = isCanceled;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("EditUserStarSubscriptionMessage{");
        sb.append("userId=").append(userId);
        sb.append(", telegramPaymentChargeId='").append(telegramPaymentChargeId).append('\'');
        sb.append(", isCanceled=").append(isCanceled);
        sb.append('}');
        return sb.toString();
    }
}
