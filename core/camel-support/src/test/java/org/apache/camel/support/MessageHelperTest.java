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

package org.apache.camel.support;

import java.util.Map;
import java.util.function.Supplier;

import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.spi.DataType;
import org.apache.camel.trait.message.MessageTrait;
import org.junit.jupiter.api.Test;

import static org.apache.camel.support.MessageHelper.copyBody;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The unit test for the class {@link MessageHelper}.
 */
class MessageHelperTest {

    @Test
    void shouldCopyBodyIfBothDataTypeAwareWithDataTypeSet() {
        Object body = new Object();
        DataType type = new DataType("foo");
        DefaultMessage m1 = new DefaultMessage((Exchange) null);
        m1.setBody(body, type);
        DefaultMessage m2 = new DefaultMessage((Exchange) null);
        copyBody(m1, m2);
        assertSame(body, m2.getBody());
        assertSame(type, m2.getDataType());
    }

    @Test
    void shouldCopyBodyIfBothDataTypeAwareWithoutDataTypeSet() {
        Object body = new Object();
        DefaultMessage m1 = new DefaultMessage((Exchange) null);
        m1.setBody(body, (DataType) null);
        DefaultMessage m2 = new DefaultMessage((Exchange) null);
        copyBody(m1, m2);
        assertSame(body, m2.getBody());
    }

    @Test
    void shouldCopyBodyIfBothNotDataTypeAware() {
        Object body = new Object();
        Message m1 = new MyMessageType(body);
        Message m2 = new MyMessageType(new Object());
        copyBody(m1, m2);
        assertSame(body, m2.getBody());
    }

    @Test
    void shouldCopyBodyIfSourceNotDataTypeAware() {
        Object body = new Object();
        Message m1 = new DefaultMessage((Exchange) null);
        m1.setBody(body);
        Message m2 = new MyMessageType(new Object());
        copyBody(m1, m2);
        assertSame(body, m2.getBody());
    }

    @Test
    void shouldCopyBodyIfTargetNotDataTypeAware() {
        Object body = new Object();
        Message m1 = new MyMessageType(body);
        Message m2 = new DefaultMessage((Exchange) null);
        copyBody(m1, m2);
        assertSame(body, m2.getBody());
    }

    private static class MyMessageType implements Message {

        Object body;

        MyMessageType(Object body) {
            this.body = body;
        }

        @Override
        public void reset() {

        }

        @Override
        public String getMessageId() {
            return null;
        }

        @Override
        public long getMessageTimestamp() {
            return 0;
        }

        @Override
        public void setMessageId(String messageId) {

        }

        @Override
        public boolean hasMessageId() {
            return false;
        }

        @Override
        public Exchange getExchange() {
            return null;
        }

        @Override
        public Object getHeader(String name) {
            return null;
        }

        @Override
        public Object getHeader(String name, Object defaultValue) {
            return null;
        }

        @Override
        public Object getHeader(String name, Supplier<Object> defaultValueSupplier) {
            return null;
        }

        @Override
        public <T> T getHeader(String name, Class<T> type) {
            return null;
        }

        @Override
        public <T> T getHeader(String name, Object defaultValue, Class<T> type) {
            return null;
        }

        @Override
        public <T> T getHeader(String name, Supplier<Object> defaultValueSupplier, Class<T> type) {
            return null;
        }

        @Override
        public void setHeader(String name, Object value) {

        }

        @Override
        public Object removeHeader(String name) {
            return null;
        }

        @Override
        public boolean removeHeaders(String pattern) {
            return false;
        }

        @Override
        public boolean removeHeaders(String pattern, String... excludePatterns) {
            return false;
        }

        @Override
        public Map<String, Object> getHeaders() {
            return null;
        }

        @Override
        public void setHeaders(Map<String, Object> headers) {

        }

        @Override
        public boolean hasHeaders() {
            return false;
        }

        @Override
        public Object getBody() {
            return body;
        }

        @Override
        public Object getMandatoryBody() throws InvalidPayloadException {
            return null;
        }

        @Override
        public <T> T getBody(Class<T> type) {
            return null;
        }

        @Override
        public <T> T getMandatoryBody(Class<T> type) throws InvalidPayloadException {
            return null;
        }

        @Override
        public void setBody(Object body) {
            this.body = body;
        }

        @Override
        public <T> void setBody(Object body, Class<T> type) {

        }

        @Override
        public Message copy() {
            return null;
        }

        @Override
        public void copyFrom(Message message) {

        }

        @Override
        public void copyFromWithNewBody(Message message, Object newBody) {

        }

        @Override
        public boolean hasTrait(MessageTrait trait) {
            return false;
        }

        @Override
        public Object getPayloadForTrait(MessageTrait trait) {
            return null;
        }

        @Override
        public void setPayloadForTrait(MessageTrait trait, Object object) {

        }
    }
}
