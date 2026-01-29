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

import java.io.File;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;

import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;

import org.apache.camel.Exchange;
import org.apache.camel.support.ResourceHelper;

public class CSimpleAttachmentHelper {

    private static AttachmentMessage toAttachmentMessage(Exchange exchange) {
        AttachmentMessage answer;
        if (exchange.getMessage() instanceof AttachmentMessage am) {
            answer = am;
        } else {
            answer = new DefaultAttachmentMessage(exchange.getMessage());
        }
        return answer;
    }

    public static Map<String, DataHandler> attachments(Exchange exchange) {
        return toAttachmentMessage(exchange).getAttachments();
    }

    public static int attachmentsSize(Exchange exchange) {
        return toAttachmentMessage(exchange).getAttachments().size();
    }

    public static Set<String> attachmentsKeys(Exchange exchange) {
        return toAttachmentMessage(exchange).getAttachmentNames();
    }

    public static Object attachmentContent(Exchange exchange, String key) throws Exception {
        var dh = toAttachmentMessage(exchange).getAttachments().get(key);
        if (dh != null) {
            return dh.getContent();
        }
        return null;
    }

    public static String attachmentContentAsText(Exchange exchange, String key) throws Exception {
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
        var dh = toAttachmentMessage(exchange).getAttachments().get(key);
        if (dh != null) {
            return dh.getContentType();
        }
        return null;
    }

    public static String attachmentHeader(Exchange exchange, String key, String name) {
        var ao = toAttachmentMessage(exchange).getAttachmentObjects().get(key);
        if (ao != null) {
            return ao.getHeader(name);
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

    public static Object setAttachment(Exchange exchange, String attachmentName, Object value) throws Exception {
        if (value != null) {
            AttachmentMessage am = toAttachmentMessage(exchange);
            DataSource ds;
            if (value instanceof File f) {
                ds = new CamelFileDataSource(f, attachmentName);
            } else if (value instanceof String str) {
                byte[] data;
                if (ResourceHelper.hasScheme(str)) {
                    InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(exchange.getContext(), str);
                    data = exchange.getContext().getTypeConverter().convertTo(byte[].class, is);
                } else {
                    data = str.getBytes();
                }
                ds = new ByteArrayDataSource(attachmentName, data);
            } else {
                byte[] data = exchange.getContext().getTypeConverter().convertTo(byte[].class, value);
                ds = new ByteArrayDataSource(attachmentName, data);
            }
            am.addAttachment(attachmentName, new DataHandler(ds));
        } else {
            AttachmentMessage am = toAttachmentMessage(exchange);
            am.removeAttachment(attachmentName);
        }
        return null;
    }

    public static Object clearAttachments(Exchange exchange) {
        AttachmentMessage am = toAttachmentMessage(exchange);
        am.clearAttachments();
        return null;
    }

}
