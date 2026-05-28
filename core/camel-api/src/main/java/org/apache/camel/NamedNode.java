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

import java.util.Collections;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Represents a node in the {@link org.apache.camel.model routes} which is identified by an id.
 */
public interface NamedNode extends LineNumberAware {

    /**
     * Gets the value of the id property.
     */
    String getId();

    /**
     * Gets the node prefix id.
     */
    @Nullable
    String getNodePrefixId();

    /**
     * Returns a short name for this node which can be useful for ID generation or referring to related resources like
     * images
     *
     * @return defaults to "node" but derived nodes should overload this to provide a unique name
     */
    String getShortName();

    /**
     * Returns a label to describe this node such as the expression if some kind of expression node
     */
    String getLabel();

    /**
     * Returns the description text or null if there is no description text associated with this node
     */
    @Nullable
    String getDescriptionText();

    /**
     * Returns the parent
     */
    @Nullable
    NamedNode getParent();

    /**
     * Whether this node can accept debugging the current exchange. This allows flexibility for some EIPs that need to
     * compute whether to accept debugging or not
     *
     * @param  exchange the current exchange
     * @return          true to accept debugging this node, or false to skip
     */
    default boolean acceptDebugger(Exchange exchange) {
        return true;
    }

    /**
     * Processor Level in the route tree
     */
    default int getLevel() {
        int level = 0;
        NamedNode node = this;
        while (node != null && node.getParent() != null) {
            node = node.getParent();
            level++;
        }
        return level;
    }

    default @Nullable String getParentId() {
        NamedNode node = this;
        if (node.getParent() != null) {
            return node.getParent().getId();
        }
        return null;
    }

    /**
     * Returns the direct children of this node in the route model tree.
     *
     * This provides a uniform API for tree walkers to traverse the full model structure without needing to special-case
     * EIPs like Choice (whose when/otherwise children are not {@link org.apache.camel.model.ProcessorDefinition}s and
     * therefore invisible to {@code getOutputs()}).
     */
    default List<NamedNode> getChildren() {
        return Collections.emptyList();
    }

    /**
     * Special methods for Choice EIP
     */
    default @Nullable NamedNode findMatchingWhen(String id) {
        return null;
    }

    /**
     * Special methods for Choice EIP
     */
    default @Nullable NamedNode findMatchingOtherwise(String id) {
        return null;
    }

}
