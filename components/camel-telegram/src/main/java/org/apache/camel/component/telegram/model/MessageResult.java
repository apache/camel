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
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MessageResult implements Serializable {

    private static final long serialVersionUID = -4560342931918215225L;

    private boolean ok;

    private IncomingMessage message;

    private boolean result;

    public MessageResult() {
    }

    public boolean isOk() {
        return ok;
    }

    public void setOk(boolean ok) {
        this.ok = ok;
    }

    public IncomingMessage getMessage() {
        return message;
    }

    public void setMessage(IncomingMessage message) {
        this.message = message;
    }

    @JsonSetter("result")
    public void setResult(JsonNode result) throws JsonProcessingException  {
        if (result != null) {
            if (result.isBoolean()) {
                this.result = result.asBoolean();
            } else if (result.isObject()) {
                ObjectMapper objectMapper = new ObjectMapper();
                this.message = objectMapper.treeToValue(result, IncomingMessage.class);
            }
        }
    }

    public boolean isResult() {
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("MessageResult{");
        sb.append("ok=").append(ok);
        sb.append(", message=").append(message);
        sb.append('}');
        return sb.toString();
    }
}
