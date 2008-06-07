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
package org.apache.camel.converter.jaxb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlMixed;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Message;

/**
 * Represents a JAXB2 representation of a Camel {@link Message} - <b>Important</b>: work in progress!
 *
 * @version $Revision$
 */
@XmlRootElement(name = "message")
@XmlAccessorType(value = XmlAccessType.FIELD)
public class MessageType {
    // TODO: XmlElementRef to the abstrac class HeaderType does not work (CAMEL-583)
    //@XmlElementRef(type = HeaderType.class)
    @XmlAnyElement(lax = true)
    @XmlMixed
    List<HeaderType> headers = new ArrayList<HeaderType>();
    @XmlAnyElement(lax = true)
    @XmlMixed
    private List content = new ArrayList();
    @XmlTransient
    private Object body;

    public Object getBody() {
        if (body == null) {
            if (content != null) {
                if (content.size() == 1) {
                    return content.get(0);
                } else {
                    return content;
                }
            }
        }
        return body;
    }

    public void setBody(Object body) {
        this.body = body;
        if (body instanceof List) {
            content = (List)body;
        } else {
            content = new ArrayList();
            content.add(body);
        }
    }

    public List<HeaderType> getHeaders() {
        return headers;
    }

    public void setHeaders(List<HeaderType> headers) {
        this.headers = headers;
    }

    public Map<String, Object> getHeaderMap() {
        Map<String, Object> answer = new HashMap<String, Object>();
        for (HeaderType header : headers) {
            answer.put(header.getName(), header.getValue());
        }
        return answer;
    }

    /**
     * Copies the headers and body of this object from the given Camel message
     *
     * @param message the Camel message to read the headers and body from
     */
    public void copyFrom(Message message) {
        headers.clear();
        Set<Map.Entry<String, Object>> entries = message.getHeaders().entrySet();
        for (Map.Entry<String, Object> entry : entries) {
            Object value = entry.getValue();
            if (value != null) {
                headers.add(createHeader(entry.getKey(), value));
            }
        }
        setBody(message.getBody());
    }

    /**
     * Copies the headers and body of this object to the given Camel message
     *
     * @param message the camel message to overwrite its headers and body
     */
    public void copyTo(Message message) {
        message.setHeaders(getHeaderMap());
        message.setBody(getBody());
    }

    protected HeaderType createHeader(String key, Object value) {
        if (value instanceof String) {
            return new StringHeader(key, (String)value);
        } else if (value instanceof Integer) {
            return new IntegerHeader(key, (Integer)value);
        } else if (value instanceof Long) {
            return new LongHeader(key, (Long)value);
        } else {
            // lets convert to a String
            return new StringHeader(key, value.toString());

            //return new ObjectHeader(key, value);
        }
    }
}
