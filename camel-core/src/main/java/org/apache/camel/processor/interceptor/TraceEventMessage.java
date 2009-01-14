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

/**
 * A trace event message that contains decomposited information about the traced
 * {@link org.apache.camel.Exchange} at the point of interception. The information is stored as snapshot copies
 * using String types.
 */
public interface TraceEventMessage {

    /**
     * Gets the timestamp when the interception occured
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
     * instread to indicate the start
     */
    String getPreviousNode();

    /**
     * Gets the current node that just have been intercepted and processed
     * <p/>
     * Is never null.
     */
    String getToNode();

    String getExchangeId();

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

    /**
     * Gets the caused by exception (ie {@link org.apache.camel.Exchange#getException() Exchange#getException()}.
     */
    String getCausedByException();
}
