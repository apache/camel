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

package org.apache.camel.component.cxf.headers;

import java.util.List;

import org.apache.cxf.headers.Header;

/*
 * Relays the wire message headers from one CXF endpoint to another CXF endpoint
 * Direction.OUT specifies when headers are relayed from a CXF consumer to producer (request)
 * Direction.IN specifies when headers are relayed from a CXF producer to CXF consumer (reply)
 * */
public interface MessageHeadersRelay {

    /*
     * @return a list of binding name spaces that this relay can service
     */
    List<String> getActivationNamespaces();

    /* 
     *  Given a route:
     *      from(cxf:cxf:bean:A).to(cxf:bean:B)
     *  
     *  A message flow (request) from A to B will be treated as Direction.OUT direction;
     *  @param direction = Direction.OUT
     *  @param from is message headers for endpoint A
     *  @param to is message headers for endpoint B
     *  
     *  A message flow (reply) from B to A will be treated as Direction.IN direction  
     *  @param direction = Direction.IN
     *  @param from is message headers for endpoint B
     *  @param to is message headers for endpoint A
     *  
     */
    void relay(Direction direction, List<Header> from, List<Header> to);
}
