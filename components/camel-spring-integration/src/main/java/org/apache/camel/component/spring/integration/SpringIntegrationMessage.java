/**
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
package org.apache.camel.component.spring.integration;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.impl.DefaultMessage;
import org.springframework.integration.message.MessageHeader;

/**
 * The Message {@link DefaultMessage} implementation
 * for accessing the SpringIntegrationMessage
 *
 * @version $Revision$
 */
public class SpringIntegrationMessage extends DefaultMessage {
    private org.springframework.integration.message.Message siMessage;

    public SpringIntegrationMessage(org.springframework.integration.message.Message message) {
        siMessage = message;
    }

    public SpringIntegrationMessage() {

    }

    public void setMessage(org.springframework.integration.message.Message message) {
        siMessage = message;
    }

    public org.springframework.integration.message.Message getMessage() {
        return siMessage;
    }

    @Override
    public void copyFrom(org.apache.camel.Message that) {
        setMessageId(that.getMessageId());
        setBody(that.getBody());
        getHeaders().putAll(that.getHeaders());
        if (that instanceof SpringIntegrationMessage) {
            SpringIntegrationMessage orig = (SpringIntegrationMessage) that;
            setMessage(orig.getMessage());
        }
    }

    @Override
    public String toString() {
        if (siMessage != null) {
            return "SpringIntegrationMessage: " + siMessage;
        } else {
            return "SpringIntegrationMessage: " + getBody();
        }
    }

    @Override
    public SpringIntegrationExchange getExchange() {
        return (SpringIntegrationExchange)super.getExchange();
    }


    public Object getHeader(String name) {
        if (siMessage != null) {
            return siMessage.getHeader().getAttribute(name);
        } else {
            return super.getHeader(name);
        }
    }

    @Override
    public void setHeader(String name, Object value) {
        if (siMessage != null) {
            siMessage.getHeader().setAttribute(name, value);
        } else {
            super.setHeader(name, value);
        }
    }

    @Override
    public Map<String, Object> getHeaders() {
        if (siMessage != null) {
            Map<String, Object> answer = new HashMap<String, Object>();
            MessageHeader header = siMessage.getHeader();
            for (String name : header.getAttributeNames()) {
                answer.put(name, header.getAttribute(name));
            }
            return answer;
        } else {
            return super.getHeaders();
        }
    }

    @Override
    public SpringIntegrationMessage newInstance() {
        return new SpringIntegrationMessage();
    }

    @Override
    protected Object createBody() {
        return siMessage.getPayload();
    }

}
