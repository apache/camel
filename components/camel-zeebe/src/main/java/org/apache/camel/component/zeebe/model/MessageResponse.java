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

package org.apache.camel.component.zeebe.model;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageResponse extends AbstractZeebeResponse {
    @JsonProperty("correlation_key")
    private String correlationKey;

    @JsonProperty("message_key")
    private long messageKey = -1;

    public String getCorrelationKey() {
        return correlationKey;
    }

    public void setCorrelationKey(String correlationKey) {
        this.correlationKey = correlationKey;
    }

    public long getMessageKey() {
        return messageKey;
    }

    public void setMessageKey(long messageKey) {
        this.messageKey = messageKey;
    }

    @Override
    public String toString() {
        return "MessageResponse{" + "correlationKey='" + correlationKey + '\'' +
               ", messageKey=" + messageKey +
               '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        MessageResponse that = (MessageResponse) o;
        return messageKey == that.messageKey && correlationKey.equals(that.correlationKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), correlationKey, messageKey);
    }
}
