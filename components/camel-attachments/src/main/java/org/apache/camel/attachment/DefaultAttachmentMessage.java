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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import jakarta.activation.DataHandler;

import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.trait.message.MessageTrait;

public final class DefaultAttachmentMessage implements AttachmentMessage {
    /*
     * Attachments are stores as a property on the {@link Exchange} which ensures they are propagated
     * during routing and we dont have to pollute the generic {@link Message} with attachment APIs
     */
    private static final String ATTACHMENT_OBJECTS = "CamelAttachmentObjects";

    private final Message delegate;
    private final Exchange exchange;

    public DefaultAttachmentMessage(Message delegate) {
        this.delegate = delegate;
        this.exchange = delegate.getExchange();
    }

    @Override
    public void reset() {
        delegate.reset();
    }

    @Override
    public String getMessageId() {
        return delegate.getMessageId();
    }

    @Override
    public void setMessageId(String messageId) {
        delegate.setMessageId(messageId);
    }

    @Override
    public boolean hasMessageId() {
        return delegate.getMessageId() != null;
    }

    @Override
    public long getMessageTimestamp() {
        return delegate.getMessageTimestamp();
    }

    @Override
    public Exchange getExchange() {
        return delegate.getExchange();
    }

    @Override
    public Object getHeader(String name) {
        return delegate.getHeader(name);
    }

    @Override
    public Object getHeader(String name, Object defaultValue) {
        return delegate.getHeader(name, defaultValue);
    }

    @Override
    public Object getHeader(String name, Supplier<Object> defaultValueSupplier) {
        return delegate.getHeader(name, defaultValueSupplier);
    }

    @Override
    public <T> T getHeader(String name, Class<T> type) {
        return delegate.getHeader(name, type);
    }

    @Override
    public <T> T getHeader(String name, Object defaultValue, Class<T> type) {
        return delegate.getHeader(name, defaultValue, type);
    }

    @Override
    public <T> T getHeader(String name, Supplier<Object> defaultValueSupplier, Class<T> type) {
        return delegate.getHeader(name, defaultValueSupplier, type);
    }

    @Override
    public void setHeader(String name, Object value) {
        delegate.setHeader(name, value);
    }

    @Override
    public Object removeHeader(String name) {
        return delegate.removeHeader(name);
    }

    @Override
    public boolean removeHeaders(String pattern) {
        return delegate.removeHeaders(pattern);
    }

    @Override
    public boolean removeHeaders(String pattern, String... excludePatterns) {
        return delegate.removeHeaders(pattern, excludePatterns);
    }

    @Override
    public Map<String, Object> getHeaders() {
        return delegate.getHeaders();
    }

    @Override
    public void setHeaders(Map<String, Object> headers) {
        delegate.setHeaders(headers);
    }

    @Override
    public boolean hasHeaders() {
        return delegate.hasHeaders();
    }

    @Override
    public Object getBody() {
        return delegate.getBody();
    }

    @Override
    public Object getMandatoryBody() throws InvalidPayloadException {
        return delegate.getMandatoryBody();
    }

    @Override
    public <T> T getBody(Class<T> type) {
        return delegate.getBody(type);
    }

    @Override
    public <T> T getMandatoryBody(Class<T> type) throws InvalidPayloadException {
        return delegate.getMandatoryBody(type);
    }

    @Override
    public void setBody(Object body) {
        delegate.setBody(body);
    }

    @Override
    public <T> void setBody(Object body, Class<T> type) {
        delegate.setBody(body, type);
    }

    @Override
    public Message copy() {
        return delegate.copy();
    }

    @Override
    public void copyFrom(Message message) {
        delegate.copyFrom(message);
    }

    @Override
    public void copyFromWithNewBody(Message message, Object newBody) {
        delegate.copyFromWithNewBody(message, newBody);
    }

    @Override
    @SuppressWarnings("unchecked")
    public DataHandler getAttachment(String id) {
        AttachmentMap map = exchange.getExchangeExtension().getSafeCopyProperty(ATTACHMENT_OBJECTS, AttachmentMap.class);
        if (map != null) {
            Attachment att = map.get(id);
            if (att != null) {
                return att.getDataHandler();
            }
        }
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Attachment getAttachmentObject(String id) {
        AttachmentMap map = exchange.getExchangeExtension().getSafeCopyProperty(ATTACHMENT_OBJECTS, AttachmentMap.class);
        if (map != null) {
            return map.get(id);
        }
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Set<String> getAttachmentNames() {
        AttachmentMap map = exchange.getExchangeExtension().getSafeCopyProperty(ATTACHMENT_OBJECTS, AttachmentMap.class);
        if (map != null) {
            return map.keySet();
        }
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void removeAttachment(String id) {
        AttachmentMap map = exchange.getExchangeExtension().getSafeCopyProperty(ATTACHMENT_OBJECTS, AttachmentMap.class);
        if (map != null) {
            map.remove(id);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void addAttachment(String id, DataHandler content) {
        AttachmentMap map = exchange.getExchangeExtension().getSafeCopyProperty(ATTACHMENT_OBJECTS, AttachmentMap.class);
        if (map == null) {
            map = new AttachmentMap();
            exchange.getExchangeExtension().setSafeCopyProperty(ATTACHMENT_OBJECTS, map);
        }
        map.put(id, new DefaultAttachment(content));
    }

    @Override
    @SuppressWarnings("unchecked")
    public void addAttachmentObject(String id, Attachment content) {
        AttachmentMap map = exchange.getExchangeExtension().getSafeCopyProperty(ATTACHMENT_OBJECTS, AttachmentMap.class);
        if (map == null) {
            map = new AttachmentMap();
            exchange.getExchangeExtension().setSafeCopyProperty(ATTACHMENT_OBJECTS, map);
        }
        map.put(id, content);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, DataHandler> getAttachments() {
        Map<String, Attachment> map = exchange.getExchangeExtension().getSafeCopyProperty(ATTACHMENT_OBJECTS, Map.class);
        if (map != null) {
            Map<String, DataHandler> answer = new LinkedHashMap<>();
            map.forEach((id, att) -> answer.put(id, att.getDataHandler()));
            return answer;
        }
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Attachment> getAttachmentObjects() {
        return exchange.getExchangeExtension().getSafeCopyProperty(ATTACHMENT_OBJECTS, Map.class);
    }

    @Override
    public void setAttachments(Map<String, DataHandler> attachments) {
        AttachmentMap map = new AttachmentMap();
        attachments.forEach((id, dh) -> map.put(id, new DefaultAttachment(dh)));
        exchange.getExchangeExtension().setSafeCopyProperty(ATTACHMENT_OBJECTS, map);
    }

    @Override
    public void setAttachmentObjects(Map<String, Attachment> attachments) {
        AttachmentMap map = new AttachmentMap();
        map.putAll(attachments);
        exchange.getExchangeExtension().setSafeCopyProperty(ATTACHMENT_OBJECTS, map);
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean hasAttachments() {
        AttachmentMap map = exchange.getExchangeExtension().getSafeCopyProperty(ATTACHMENT_OBJECTS, AttachmentMap.class);
        return map != null && !map.isEmpty();
    }

    @Override
    public boolean hasTrait(MessageTrait trait) {
        return delegate.hasTrait(trait);
    }

    @Override
    public Object getPayloadForTrait(MessageTrait trait) {
        return delegate.getPayloadForTrait(trait);
    }

    @Override
    public void setPayloadForTrait(MessageTrait trait, Object object) {
        delegate.setPayloadForTrait(trait, object);
    }
}
