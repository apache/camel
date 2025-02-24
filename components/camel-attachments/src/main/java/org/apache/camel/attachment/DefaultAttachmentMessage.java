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

    private final Message delegate;

    public DefaultAttachmentMessage(Message delegate) {
        this.delegate = delegate;
    }

    @Override
    public Message getDelegateMessage() {
        return delegate;
    }

    @Override
    public Message newInstance() {
        return new DefaultAttachmentMessage(delegate);
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
        return new DefaultAttachmentMessage(delegate.copy());
    }

    @Override
    public void copyFrom(Message message) {

    }

    @Override
    public void copyFromWithNewBody(Message message, Object newBody) {
        delegate.copyFromWithNewBody(message, newBody);
    }

    @Override
    public DataHandler getAttachment(String id) {
        Attachment att = (Attachment) getAttachmentsMap().get(id);
        if (att != null) {
            return att.getDataHandler();
        }
        return null;
    }

    @Override
    public Attachment getAttachmentObject(String id) {
        return (Attachment) getAttachmentsMap().get(id);
    }

    @Override
    public Set<String> getAttachmentNames() {
        return getAttachmentsMap().keySet();
    }

    @Override
    public void removeAttachment(String id) {
        getAttachmentsMap().remove(id);
    }

    @Override
    public void addAttachment(String id, DataHandler content) {
        getAttachmentsMap().put(id, new DefaultAttachment(content));
    }

    @Override
    public void addAttachmentObject(String id, Attachment content) {
        getAttachmentsMap().put(id, content);
    }

    @Override
    public Map<String, DataHandler> getAttachments() {
        Map<String, DataHandler> answer = new LinkedHashMap<>();
        getAttachmentsMap().forEach((id, att) -> {
            Attachment a = (Attachment) att;
            answer.put(id, a.getDataHandler());
        });
        return answer;
    }

    @Override
    public Map<String, Attachment> getAttachmentObjects() {
        Map<String, Attachment> answer = new LinkedHashMap<>();
        getAttachmentsMap().forEach((id, att) -> {
            Attachment a = (Attachment) att;
            answer.put(id, a);
        });
        return answer;
    }

    @Override
    public void setAttachments(Map<String, DataHandler> attachments) {
        attachments.forEach((id, dh) -> getAttachmentsMap().put(id, new DefaultAttachment(dh)));
    }

    @Override
    public void setAttachmentObjects(Map<String, Attachment> attachments) {
        getAttachmentsMap().putAll(attachments);
    }

    @Override
    public boolean hasAttachments() {
        return delegate.hasAttachments();
    }

    @Override
    public Map<String, Object> getAttachmentsMap() {
        return delegate.getAttachmentsMap();
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
