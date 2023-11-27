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
 * Represents a node in the {@link org.apache.camel.model routes} which is identified as a route.
 */
public interface NamedRoute {

    /**
     * Gets the route id.
     */
    String getRouteId();

    /**
     * Gets the node prefix id.
     */
    String getNodePrefixId();

    /**
     * Gets the route endpoint url.
     */
    String getEndpointUrl();

    /**
     * Is the route created from template;
     */
    boolean isCreatedFromTemplate();

    /**
     * Is the route created from Rest DSL
     */
    boolean isCreatedFromRest();

    /**
     * Gets the route input
     */
    NamedNode getInput();

}
