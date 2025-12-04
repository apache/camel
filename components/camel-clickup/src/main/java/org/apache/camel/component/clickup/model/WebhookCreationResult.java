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

package org.apache.camel.component.clickup.model;

import java.io.Serial;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class WebhookCreationResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 0L;

    @JsonProperty("id")
    private String id;

    @JsonProperty("webhook")
    private Webhook webhook;

    @JsonProperty("err")
    private String error;

    @JsonProperty("ECODE")
    private String errorCode;

    public String getId() {
        return id;
    }

    public Webhook getWebhook() {
        return webhook;
    }

    public String getError() {
        return error;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public boolean isError() {
        return this.error != null;
    }

    @Override
    public String toString() {
        return "WebhookCreationResult{" + "id='"
                + id + '\'' + ", webhook="
                + webhook + ", error='"
                + error + '\'' + ", errorCode='"
                + errorCode + '\'' + '}';
    }
}
