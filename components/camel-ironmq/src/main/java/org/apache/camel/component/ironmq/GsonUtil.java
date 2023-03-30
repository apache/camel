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
package org.apache.camel.component.ironmq;

import java.util.Map;

import com.google.gson.Gson;
import org.apache.camel.Message;

public final class GsonUtil {
    private static final Gson GSON = new Gson();

    private GsonUtil() {
    }

    static class IronMqMessage {
        private Map<String, Object> headers;
        private final String body;

        IronMqMessage(String body, Map<String, Object> headers) {
            this.headers = headers;
            this.body = body;
        }

        public Map<String, Object> getHeaders() {
            return headers;
        }

        public String getBody() {
            return body;
        }
    }

    static String getBodyFromMessage(Message message) {
        IronMqMessage ironMessage = new IronMqMessage(message.getBody(String.class), message.getHeaders());
        return GSON.toJson(ironMessage);
    }

    static void copyFrom(io.iron.ironmq.Message source, Message target) {
        IronMqMessage ironMqMessage = GSON.fromJson(source.getBody(), IronMqMessage.class);
        target.setBody(ironMqMessage.getBody());
        if (ironMqMessage.getHeaders() != null) {
            for (Map.Entry<String, Object> entry : ironMqMessage.getHeaders().entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (target.getHeader(key) == null) {
                    target.setHeader(key, value);
                }
            }
        }
    }
}
