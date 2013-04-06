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

import org.apache.camel.Exchange;

/**
 * A trace event message that contains decomposed information about the traced
 * {@link org.apache.camel.Exchange} at the point of interception. The information is stored as snapshot copies
 * using String types.
 * <p/>
 * Notice not all implementations may provide direct access to the traced {@link Exchange} using
 * the {@link #getTracedExchange()} method, and thus this method may return <tt>null</tt>.
 * For example the JPA implementation will return <tt>null</tt>.
 */
public interface TraceEventMessage {

    /**
     * Gets the timestamp when the interception occurred
     */
    Date getTimestamp();

    /**
     * Uri of the endpoint that started the {@link org.apache.camel.Exchange} currently being traced.
     */
    String getFromEndpointUri();

    /**
     * Gets the previous node.
     * <p/>
     * Will return <tt>null</tt> if this is the first node, then you can use the from endpoint uri
     * instead to indicate the start
     */
    String getPreviousNode();

    /**
     * Gets the current node that just have been intercepted and processed
     * <p/>
     * Is never <tt>null</tt>.
     */
    String getToNode();

    String getExchangeId();

    String getRouteId();

    /**
     * Gets the exchange id without the leading hostname
     */
    String getShortExchangeId();

    String getExchangePattern();

    String getProperties();

    String getHeaders();

    String getBody();

    String getBodyType();

    String getOutBody();

    String getOutBodyType();

    String getOutHeaders();

    /**
     * Gets the caused by exception (ie {@link org.apache.camel.Exchange#getException() Exchange#getException()}.
     */
    String getCausedByException();

    /**
     * Gets the traced {@link Exchange}.
     * <p/>
     * Not all implementations may provide direct access to the traced {@link Exchange} and thus this
     * method may return <tt>null</tt>. For example the JPA implementation will return <tt>null</tt>.
     *
     * @return the traced {@link Exchange}, however it can be <tt>null</tt> in some implementations.
     */
    Exchange getTracedExchange();
}
