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
package org.apache.camel.component.spring.ws.bean;

import org.apache.camel.component.spring.ws.type.EndpointMappingKey;
import org.springframework.ws.server.EndpointMapping;
import org.springframework.ws.server.endpoint.MessageEndpoint;

/**
 * Allows to register different spring-ws endpoints for camel.
 */
public interface CamelSpringWSEndpointMapping extends EndpointMapping {

    /**
     * Used by Camel Spring Web Services endpoint to register consumers
     * 
     * @param key unique consumer key
     * @param endpoint consumer
     */
    void addConsumer(EndpointMappingKey key, MessageEndpoint endpoint);

    /**
     * Used by Camel Spring Web Services endpoint to unregister consumers
     * 
     * @param key unique consumer key
     */
    void removeConsumer(Object key);

}
