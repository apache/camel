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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Upon receiving a message with this object, Telegram clients will display a reply interface to the user
 * (act as if the user has selected the bot‘s message and tapped ’Reply')
 *
 * @see <a href="https://core.telegram.org/bots/api#forcereply">https://core.telegram.org/bots/api#forcereply</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ForceReply implements Serializable, ReplyMarkup {

    private static final long serialVersionUID = 6593162429090060569L;

    @JsonProperty("force_reply")
    private Boolean forceReply;

    private Boolean selective = false;

    public ForceReply(Boolean forceReply, Boolean selective) {
        this.forceReply = forceReply;
        this.selective = selective;
    }

    public ForceReply() {
    }

    public ForceReply(Boolean forceReply) {
        this.forceReply = forceReply;
    }

    public Boolean getForceReply() {
        return forceReply;
    }

    public void setForceReply(Boolean forceReply) {
        this.forceReply = forceReply;
    }

    public Boolean getSelective() {
        return selective;
    }

    public void setSelective(Boolean selective) {
        this.selective = selective;
    }
}
