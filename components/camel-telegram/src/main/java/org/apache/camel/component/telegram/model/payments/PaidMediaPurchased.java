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
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.camel.component.telegram.model.User;

/**
 * This object contains information about a paid media purchase.
 *
 * @see <a href=
 *      "https://core.telegram.org/bots/api#paidmediapurchased">https://core.telegram.org/bots/api#paidmediapurchased</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaidMediaPurchased implements Serializable {

    @Serial
    private static final long serialVersionUID = 69321874168775101L;

    /**
     * User who purchased the media.
     */
    private User from;

    /**
     * Bot-specified paid media payload.
     */
    @JsonProperty("paid_media_payload")
    private String paidMediaPayload;

    public PaidMediaPurchased() {
    }

    public PaidMediaPurchased(User from, String paidMediaPayload) {
        this.from = from;
        this.paidMediaPayload = paidMediaPayload;
    }

    public User getFrom() {
        return from;
    }

    public void setFrom(User from) {
        this.from = from;
    }

    public String getPaidMediaPayload() {
        return paidMediaPayload;
    }

    public void setPaidMediaPayload(String paidMediaPayload) {
        this.paidMediaPayload = paidMediaPayload;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("PaidMediaPurchased{");
        sb.append("from=").append(from);
        sb.append(", paidMediaPayload='").append(paidMediaPayload).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
