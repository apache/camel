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

import org.apache.camel.spi.Resource;
import org.jspecify.annotations.Nullable;

/**
 * Represents a route-level node in the Camel route model, identified by a route id.
 * <p/>
 * While {@link NamedNode} identifies any model node (EIP step, route, etc.) by a generic id, {@code NamedRoute} is more
 * specific: it marks a top-level route definition. Implementations expose the route id, an optional description, and
 * the source {@link org.apache.camel.spi.Resource} from which the route was loaded (e.g., a YAML file or an in-memory
 * string).
 * <p/>
 * This interface is used internally by the framework and by management / tooling layers to enumerate routes and map
 * them back to their definition source.
 *
 * @see   NamedNode
 * @see   Route
 * @since 3.0
 */
public interface NamedRoute {

    /**
     * Gets the route id.
     */
    String getRouteId();

    /**
     * Gets the route description.
     */
    String getDescription();

    /**
     * Gets the node prefix id.
     */
    @Nullable
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

    /**
     * Gets the {@link Resource}.
     */
    Resource getResource();

}
