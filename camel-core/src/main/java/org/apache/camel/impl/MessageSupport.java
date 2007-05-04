/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.impl;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.util.UuidGenerator;

/**
 * A base class for implementation inheritence providing the core {@link Message} body
 * handling features but letting the derived class deal with headers.
 *
 * Unless a specific provider wishes to do something particularly clever with headers you probably
 * want to just derive from {@link DefaultMessage}
 *
 * @version $Revision$
 */
public abstract class MessageSupport implements Message {
    private static final UuidGenerator defaultIdGenerator = new UuidGenerator();
    private Exchange exchange;
    private Object body;
    private String messageId = defaultIdGenerator.generateId();
    

    public Object getBody() {
        if (body == null) {
            body = createBody();
        }
        return body;
    }

    @SuppressWarnings({"unchecked"})
    public <T> T getBody(Class<T> type) {
        Exchange e = getExchange();
        if (e != null) {
            return e.getContext().getTypeConverter().convertTo(type, getBody());
        }
        return (T) getBody();
    }

    public void setBody(Object body) {
        this.body = body;
    }

    public <T> void setBody(Object body, Class<T> type) {
        Exchange e = getExchange();
        if (e != null) {
            T value = e.getContext().getTypeConverter().convertTo(type, body);
            if (value != null) {
                body = value;
            }
        }
        setBody(body);
    }

    public Message copy() {
        Message answer = newInstance();
        answer.setMessageId(getMessageId());
        answer.setBody(getBody());
        answer.getHeaders().putAll(getHeaders());
        return answer;
    }

    public Exchange getExchange() {
        return exchange;
    }

    public void setExchange(Exchange exchange) {
        this.exchange = exchange;
    }

    /**
     * Returns a new instance
     *
     * @return
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

    
    /**
     * @return the messageId
     */
    public String getMessageId(){
        return this.messageId;
    }

    
    /**
     * @param messageId the messageId to set
     */
    public void setMessageId(String messageId){
        this.messageId=messageId;
    }
}
