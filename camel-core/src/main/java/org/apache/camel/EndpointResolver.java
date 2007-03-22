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
package org.apache.camel;

/**
 * A resolver of endpoints from a String URI
 *
 * @version $Revision$
 */
public interface EndpointResolver<E> {

    /**
     * Resolves the component for a given uri or returns null if now component handles it.
     */
    public Component resolveComponent(CamelContext container, String uri) throws Exception;


    /**
     * Resolves the endpoint for a given uri or returns null if no endpoint could be found
     */
    public Endpoint<E> resolveEndpoint(CamelContext container, String uri) throws Exception;
    
}
