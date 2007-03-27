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
package org.apache.camel.component.cxf;

import org.apache.camel.impl.DefaultMessage;
import org.apache.cxf.message.Message;

import java.util.Map;
import java.util.Set;

/**
 * An Apache CXF {@link Message} which provides access to the underlying CXF features
 *
 * @version $Revision$
 */
public class CxfMessage extends DefaultMessage {
    private Message cxfMessage;

    public CxfMessage() {
    }

    public CxfMessage(Message cxfMessage) {
        this.cxfMessage = cxfMessage;
    }

    @Override
    public String toString() {
        if (cxfMessage != null) {
            return "CxfMessage: " + cxfMessage;
        }
        else {
            return "CxfMessage: " + getBody();
        }
    }

    @Override
    public CxfExchange getExchange() {
        return (CxfExchange) super.getExchange();
    }

    /**
     * Returns the underlying CXF message
     *
     * @return the CXF message
     */
    public Message getMessage() {
        return cxfMessage;
    }

    public void setMessage(Message cxfMessage) {
        this.cxfMessage = cxfMessage;
    }

    public Object getHeader(String name) {
        Object answer = null;
        if (cxfMessage != null) {
            answer = cxfMessage.get(name);
        }
        if (answer == null) {
            answer = super.getHeader(name);
        }
        return answer;
    }

    @Override
    public CxfMessage newInstance() {
        return new CxfMessage();
    }

    @Override
    protected Object createBody() {
        if (cxfMessage != null) {
            return getExchange().getBinding().extractBodyFromCxf(getExchange(), cxfMessage);
        }
        return null;
    }

    @Override
    protected void populateInitialHeaders(Map<String, Object> map) {
        if (cxfMessage != null) {
            Set<Map.Entry<String, Object>> entries = cxfMessage.entrySet();
            for (Map.Entry<String, Object> entry : entries) {
                String name = entry.getKey();
                Object value = entry.getValue();
                map.put(name, value);
            }
        }
    }
}
