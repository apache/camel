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
package org.apache.camel.component.cxf.common.header;

import java.util.List;

import org.apache.camel.spi.HeaderFilterStrategy.Direction;
import org.apache.cxf.headers.Header;

/**
 * Filter the wire message headers from one CXF endpoint to another CXF endpoint
 */
public interface MessageHeaderFilter {

    /**
     * @return a list of binding name spaces that this relay can service
     */
    List<String> getActivationNamespaces();

    /**
     *  This method filters (removes) headers from the given header list. 
     *  <i>Out</i> direction refers to processing headers from a Camel message to an 
     *  CXF message.  <i>In</i> direction is the reverse direction.
     *  
     *  @param direction the direction of the processing
     *  @param headers the origin list of headers
     */
    void filter(Direction direction, List<Header> headers);

}
