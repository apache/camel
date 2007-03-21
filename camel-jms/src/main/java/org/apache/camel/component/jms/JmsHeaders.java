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
package org.apache.camel.component.jms;

import org.apache.camel.InvalidHeaderTypeException;
import org.apache.camel.impl.HeadersSupport;

import javax.jms.JMSException;
import javax.jms.Message;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * @version $Revision$
 */
public class JmsHeaders extends HeadersSupport {
    private final DefaultJmsMessage message;
    private Map<String, Object> lazyHeaders;

    public JmsHeaders(DefaultJmsMessage message) {
        this.message = message;
    }

    public Object getHeader(String name) {
        Message request = message.getJmsMessage();
        if (request != null) {
            try {
                Object value = request.getObjectProperty(name);
                try {
                    return value;
                }
                catch (ClassCastException e) {
                    throw new InvalidHeaderTypeException(e.getMessage(), value);
                }
            }
            catch (JMSException e) {
                throw new MessagePropertyAcessException(name, e);
            }
        }
        return null;
    }

    public void setHeader(String name, Object value) {
        Message request = message.getJmsMessage();
        if (request != null) {
            try {
                request.setObjectProperty(name, value);
            }
            catch (JMSException e) {
                throw new MessagePropertyAcessException(name, e);
            }
        }
        else {
            if (lazyHeaders == null) {
                lazyHeaders = new HashMap<String, Object>();
            }
            lazyHeaders.put(name, value);
        }
    }

    public Map<String, Object> getHeaders() {
        Message request = message.getJmsMessage();
        if (request != null) {
            Map<String, Object> answer = new HashMap<String, Object>();
            Enumeration names;
            try {
                names = request.getPropertyNames();
            }
            catch (JMSException e) {
                throw new MessagePropertyNamesAcessException(e);
            }
            while (names.hasMoreElements()) {
                String name = names.nextElement().toString();
                try {
                    Object value = request.getObjectProperty(name);
                    answer.put(name, value);
                }
                catch (JMSException e) {
                    throw new MessagePropertyAcessException(name, e);
                }
            }
            return answer;
        }
        else {
            return lazyHeaders;
        }
    }

}
