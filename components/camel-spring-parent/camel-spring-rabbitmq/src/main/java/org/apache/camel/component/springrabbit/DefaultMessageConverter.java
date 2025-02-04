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
package org.apache.camel.component.springrabbit;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

import org.apache.camel.CamelContext;
import org.apache.camel.NoTypeConversionAvailableException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.AbstractMessageConverter;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.amqp.support.converter.MessageConverter;

public class DefaultMessageConverter extends AbstractMessageConverter implements MessageConverter {

    private final String defaultCharset = Charset.defaultCharset().name();
    private final CamelContext camelContext;

    public DefaultMessageConverter(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public Message createMessage(Object body, MessageProperties messageProperties) throws MessageConversionException {
        if (body == null) {
            throw new MessageConversionException(
                    "Cannot send message as message body is null, and option allowNullBody is false.");
        }

        boolean text = body instanceof String;
        byte[] data;
        try {
            if (body instanceof String) {
                String encoding = messageProperties.getContentEncoding();
                if (encoding != null) {
                    data = ((String) body).getBytes(encoding);
                } else {
                    data = ((String) body).getBytes(defaultCharset);
                    messageProperties.setContentEncoding(defaultCharset);
                }
            } else {
                data = camelContext.getTypeConverter().mandatoryConvertTo(byte[].class, body);
            }
        } catch (NoTypeConversionAvailableException | UnsupportedEncodingException e) {
            throw new MessageConversionException(
                    "failed to convert to byte[] for rabbitmq message", e);
        }
        messageProperties.setContentLength(data.length);
        Message answer = new Message(data, messageProperties);
        if (MessageProperties.DEFAULT_CONTENT_TYPE.equals(messageProperties.getContentType()) && text) {
            messageProperties.setContentType(MessageProperties.CONTENT_TYPE_TEXT_PLAIN);
        }
        return answer;
    }

    @Override
    public Object fromMessage(Message message) throws MessageConversionException {
        Object content = null;
        MessageProperties properties = message.getMessageProperties();
        if (properties != null) {
            String contentType = properties.getContentType();
            if (contentType != null && contentType.startsWith("text")) {
                String encoding = properties.getContentEncoding();
                if (encoding == null) {
                    encoding = defaultCharset;
                }
                try {
                    content = new String(message.getBody(), encoding);
                } catch (UnsupportedEncodingException e) {
                    throw new MessageConversionException(
                            "failed to convert text-based Message content", e);
                }
            }
        }
        if (content == null) {
            content = message.getBody();
        }
        return content;
    }
}
