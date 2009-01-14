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
package org.apache.camel.processor.interceptor;

import java.util.Date;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * A JPA based {@link org.apache.camel.processor.interceptor.TraceEventMessage} that is capable of persisting
 * trace event into a database.
 */
@Entity
@Table(
    name = "CAMEL_MESSAGETRACED"
)
public class JpaTraceEventMessage implements TraceEventMessage {

    protected Long id;
    protected Date timestamp;
    protected String previousNode;
    protected String fromEndpointUri;
    protected String toNode;
    protected String exchangeId;
    protected String shortExchangeId;
    protected String exchangePattern;
    protected String properties;
    protected String headers;
    protected String body;
    protected String bodyType;
    protected String outBody;
    protected String outBodyType;
    protected String causedByException;

    public JpaTraceEventMessage() {
    }

    @Id
    @GeneratedValue
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public String getPreviousNode() {
        return previousNode;
    }

    public void setPreviousNode(String previousNode) {
        this.previousNode = previousNode;
    }

    public String getFromEndpointUri() {
        return fromEndpointUri;
    }

    public void setFromEndpointUri(String fromEndpointUri) {
        this.fromEndpointUri = fromEndpointUri;
    }

    public String getToNode() {
        return toNode;
    }

    public void setToNode(String toNode) {
        this.toNode = toNode;
    }

    public String getExchangeId() {
        return exchangeId;
    }

    public void setExchangeId(String exchangeId) {
        this.exchangeId = exchangeId;
    }

    public String getShortExchangeId() {
        return shortExchangeId;
    }

    public void setShortExchangeId(String shortExchangeId) {
        this.shortExchangeId = shortExchangeId;
    }

    public String getExchangePattern() {
        return exchangePattern;
    }

    public void setExchangePattern(String exchangePattern) {
        this.exchangePattern = exchangePattern;
    }

    public String getProperties() {
        return properties;
    }

    public void setProperties(String properties) {
        this.properties = properties;
    }

    public String getHeaders() {
        return headers;
    }

    public void setHeaders(String headers) {
        this.headers = headers;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getBodyType() {
        return bodyType;
    }

    public void setBodyType(String bodyType) {
        this.bodyType = bodyType;
    }

    public String getOutBody() {
        return outBody;
    }

    public void setOutBody(String outBody) {
        this.outBody = outBody;
    }

    public String getOutBodyType() {
        return outBodyType;
    }

    public void setOutBodyType(String outBodyType) {
        this.outBodyType = outBodyType;
    }

    public String getCausedByException() {
        return causedByException;
    }

    public void setCausedByException(String causedByException) {
        this.causedByException = causedByException;
    }

    @Override
    public String toString() {
        return "TraceEventMessage[" + getExchangeId() + "] on node: " + getToNode();   
    }

}
