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
package org.apache.camel.spi;

import org.apache.camel.Exchange;

/**
 * Interface to allow plug-able implementation to filter header to and from Camel message.
 *
 * @version 
 */
public interface HeaderFilterStrategy {

    /**
     * The direction is either <tt>IN</tt> or <tt>OUT</tt>.
     */
    enum Direction {
        IN, OUT
    }

    /**
     * Applies filtering logic to Camel Message header that is
     * going to be copied to target message such as CXF and JMS message.
     * <p/>
     * It returns <tt>true</tt> if the filtering logic return a match.
     * Otherwise, it returns <tt>false</tt>.
     * A match means the header should be excluded.
     *
     * @param headerName  the header name
     * @param headerValue the header value
     * @param exchange    the context to perform filtering
     * @return <tt>true</tt> if this header should be filtered out.
     */
    boolean applyFilterToCamelHeaders(String headerName, Object headerValue, Exchange exchange);


    /**
     * Applies filtering logic to an external message header such
     * as CXF and JMS message that is going to be copied to Camel
     * message header.
     * <p/>
     * It returns <tt>true</tt> if the filtering logic return a match.
     * Otherwise, it returns <tt>false</tt>.
     * A match means the header should be excluded.
     *
     * @param headerName  the header name
     * @param headerValue the header value
     * @param exchange    the context to perform filtering
     * @return <tt>true</tt> if this header should be filtered out.
     */
    boolean applyFilterToExternalHeaders(String headerName, Object headerValue, Exchange exchange);

}
