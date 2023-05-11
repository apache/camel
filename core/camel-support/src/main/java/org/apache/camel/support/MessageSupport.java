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

import java.util.EnumMap;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.TypeConverter;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.DataTypeAware;
import org.apache.camel.trait.message.MessageTrait;

/**
 * A base class for implementation inheritance providing the core {@link Message} body handling features but letting the
 * derived class deal with headers.
 *
 * Unless a specific provider wishes to do something particularly clever with headers you probably want to just derive
 * from {@link DefaultMessage}
 */
public abstract class MessageSupport implements Message, CamelContextAware, DataTypeAware {
    protected CamelContext camelContext;
    protected TypeConverter typeConverter;
    private Exchange exchange;
    private Object body;
    private String messageId;
    private long messageTimestamp;

    private final EnumMap<MessageTrait, Object> traits = new EnumMap<>(MessageTrait.class);

    @Override
    public void reset() {
        body = null;
        messageId = null;
        traits.clear();
    }

    @Override
    public String toString() {
        // do not output information about the message as it may contain sensitive information
        if (messageId != null) {
            return "Message[" + messageId + "]";
        } else {
            return "Message";
        }
    }

    @Override
    public Object getBody() {
        if (body == null) {
            body = createBody();
        }
        return body;
    }

    @Override
    public <T> T getBody(Class<T> type) {
        return getBody(type, getBody());
    }

    @Override
    public Object getMandatoryBody() throws InvalidPayloadException {
        Object answer = getBody();
        if (answer == null) {
            throw new InvalidPayloadException(getExchange(), Object.class, this);
        }
        return answer;
    }

    protected <T> T getBody(Class<T> type, Object body) {
        // eager same instance type test to avoid the overhead of invoking the type converter
        // if already same type
        if (type.isInstance(body)) {
            return (T) body;
        }

        Exchange e = getExchange();
        if (e != null) {
            // lets first try converting the body itself first
            // as for some types like InputStream v Reader its more efficient to do the transformation
            // from the body itself as its got efficient implementations of them, before trying the message
            T answer = typeConverter.convertTo(type, e, body);
            if (answer != null) {
                return answer;
            }

            // fallback and try the message itself (e.g. used in camel-http)
            answer = typeConverter.tryConvertTo(type, e, this);
            if (answer != null) {
                return answer;
            }
        }

        // not possible to convert
        return null;
    }

    @Override
    public <T> T getMandatoryBody(Class<T> type) throws InvalidPayloadException {
        // eager same instance type test to avoid the overhead of invoking the type converter
        // if already same type
        if (type.isInstance(body)) {
            return (T) body;
        }

        Exchange e = getExchange();
        if (e != null) {
            try {
                return typeConverter.mandatoryConvertTo(type, e, getBody());
            } catch (Exception cause) {
                throw new InvalidPayloadException(e, type, this, cause);
            }
        }
        // TODO Null value in e. Is it expected?
        throw new InvalidPayloadException(e, type, this);
    }

    @Override
    public void setBody(Object body) {
        this.body = body;
        // set data type if in use
        if (body != null && camelContext != null && camelContext.isUseDataType()) {
            setPayloadForTrait(MessageTrait.DATA_AWARE, new DataType(body.getClass()));
        }
    }

    @Override
    public <T> void setBody(Object value, Class<T> type) {
        Exchange e = getExchange();
        if (e != null) {
            T v = typeConverter.convertTo(type, e, value);
            if (v != null) {
                value = v;
            }
        }
        setBody(value);
    }

    @Override
    public void setBody(Object body, DataType type) {
        this.body = body;
        setPayloadForTrait(MessageTrait.DATA_AWARE, type);
    }

    @Override
    public DataType getDataType() {
        Object payload = getPayloadForTrait(MessageTrait.DATA_AWARE);
        return (DataType) payload;
    }

    @Override
    public void setDataType(DataType type) {
        setPayloadForTrait(MessageTrait.DATA_AWARE, type);
    }

    @Override
    public boolean hasDataType() {
        return hasTrait(MessageTrait.DATA_AWARE);
    }

    @Override
    public Message copy() {
        Message answer = newInstance();

        answer.copyFrom(this);
        return answer;
    }

    @Override
    public void copyFrom(Message that) {
        if (that == this) {
            // it's the same instance, so do not need to copy
            return;
        }

        copyFromWithNewBody(that, that.getBody());
        // Preserve the DataType
        if (that.hasTrait(MessageTrait.DATA_AWARE)) {
            setPayloadForTrait(MessageTrait.DATA_AWARE, that.getPayloadForTrait(MessageTrait.DATA_AWARE));
        }
    }

    @Override
    public void copyFromWithNewBody(Message that, Object newBody) {
        if (that == this) {
            // it's the same instance, so do not need to copy
            return;
        }

        // cover over exchange if none has been assigned
        if (getExchange() == null) {
            setExchange(that.getExchange());
        }

        if (that.hasMessageId()) {
            setMessageId(that.getMessageId());
        }
        // should likely not set DataType as the new body may be a different type than the original body
        setBody(newBody);

        // the headers may be the same instance if the end user has made some mistake
        // and set the OUT message with the same header instance of the IN message etc
        boolean sameHeadersInstance = false;
        if (hasHeaders() && that.hasHeaders() && getHeaders() == that.getHeaders()) {
            sameHeadersInstance = true;
        }

        if (!sameHeadersInstance) {
            if (hasHeaders()) {
                // okay its safe to clear the headers
                getHeaders().clear();
            }
            if (that.hasHeaders()) {
                getHeaders().putAll(that.getHeaders());
            }
        }
    }

    @Override
    public Exchange getExchange() {
        return exchange;
    }

    public void setExchange(Exchange exchange) {
        this.exchange = exchange;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
        this.typeConverter = camelContext.getTypeConverter();
    }

    /**
     * Returns a new instance
     */
    public abstract Message newInstance();

    /**
     * A factory method to allow a provider to lazily create the message body for inbound messages from other sources
     *
     * @return the value of the message body or null if there is no value available
     */
    protected Object createBody() {
        return null;
    }

    @Override
    public String getMessageId() {
        if (messageId == null) {
            messageId = createMessageId();
        }
        return this.messageId;
    }

    @Override
    public long getMessageTimestamp() {
        if (messageTimestamp == 0) {
            // use -1 to indicate no timestamp exists
            messageTimestamp = getHeader(Exchange.MESSAGE_TIMESTAMP, -1L, long.class);
        }
        return messageTimestamp <= 0 ? 0 : messageTimestamp;
    }

    @Override
    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    @Override
    public boolean hasMessageId() {
        return messageId != null;
    }

    /**
     * Allow implementations to auto-create a messageId
     */
    protected String createMessageId() {
        if (exchange != null) {
            // optimize and reuse exchange id
            return exchange.getExchangeId();
        } else {
            return null;
        }
    }

    @Override
    public boolean hasTrait(MessageTrait trait) {
        return traits.containsKey(trait);
    }

    @Override
    public Object getPayloadForTrait(MessageTrait trait) {
        return traits.get(trait);
    }

    @Override
    public void setPayloadForTrait(MessageTrait trait, Object object) {
        traits.put(trait, object);
    }
}
