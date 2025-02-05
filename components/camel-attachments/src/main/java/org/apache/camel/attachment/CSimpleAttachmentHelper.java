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
package org.apache.camel.attachment;

import java.util.Map;

import jakarta.activation.DataHandler;

import org.apache.camel.Exchange;

public class CSimpleAttachmentHelper {

    public static Map<String, DataHandler> attachments(Exchange exchange) {
        if (exchange.getMessage() instanceof AttachmentMessage am) {
            return am.getAttachments();
        }
        return null;
    }

    public static int attachmentsSize(Exchange exchange) {
        if (exchange.getMessage() instanceof AttachmentMessage am) {
            return am.getAttachments().size();
        }
        return 0;
    }

    public static Object attachmentContent(Exchange exchange, String key) throws Exception {
        if (exchange.getMessage() instanceof AttachmentMessage am) {
            var dh = am.getAttachments().get(key);
            if (dh != null) {
                return dh.getContent();
            }
        }
        return null;
    }

    public static Object attachmentContentAsText(Exchange exchange, String key) throws Exception {
        Object data = attachmentContent(exchange, key);
        if (data != null) {
            return exchange.getContext().getTypeConverter().convertTo(String.class, exchange, data);
        }
        return null;
    }

    public static <T> T attachmentContentAs(Exchange exchange, String key, Class<T> type) throws Exception {
        Object data = attachmentContent(exchange, key);
        if (data != null) {
            return exchange.getContext().getTypeConverter().convertTo(type, exchange, data);
        }
        return null;
    }

    public static String attachmentContentType(Exchange exchange, String key) {
        if (exchange.getMessage() instanceof AttachmentMessage am) {
            var dh = am.getAttachments().get(key);
            if (dh != null) {
                return dh.getContentType();
            }
        }
        return null;
    }

    public static String attachmentHeader(Exchange exchange, String key, String name) {
        if (exchange.getMessage() instanceof AttachmentMessage am) {
            var ao = am.getAttachmentObjects().get(key);
            if (ao != null) {
                return ao.getHeader(name);
            }
        }
        return null;
    }

    public static <T> T attachmentHeaderAs(Exchange exchange, String key, String name, Class<T> type) {
        String data = attachmentHeader(exchange, key, name);
        if (data != null) {
            return exchange.getContext().getTypeConverter().convertTo(type, exchange, data);
        }
        return null;
    }

}
