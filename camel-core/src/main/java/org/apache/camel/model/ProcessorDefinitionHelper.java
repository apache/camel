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
package org.apache.camel.model;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Helper class for ProcessorDefinition and the other model classes.
 */
public final class ProcessorDefinitionHelper {

    private ProcessorDefinitionHelper() {
    }

    /**
     * Looks for the given type in the list of outputs and recurring all the children as well.
     *
     * @param outputs  list of outputs, can be null or empty.
     * @param type     the type to look for
     * @return         the found definitions, or <tt>null</tt> if not found
     */
    public static <T> Iterator<T> filterTypeInOutputs(List<ProcessorDefinition> outputs, Class<T> type) {
        List<T> found = new ArrayList<T>();
        doFindType(outputs, type, found);
        return found.iterator();
    }

    /**
     * Looks for the given type in the list of outputs and recurring all the children as well.
     * Will stop at first found and return it.
     *
     * @param outputs  list of outputs, can be null or empty.
     * @param type     the type to look for
     * @return         the first found type, or <tt>null</tt> if not found
     */
    public static <T> T findFirstTypeInOutputs(List<ProcessorDefinition> outputs, Class<T> type) {
        List<T> found = new ArrayList<T>();
        doFindType(outputs, type, found);
        if (found.isEmpty()) {
            return null;
        }
        return found.iterator().next();
    }

    /**
     * Is the given child the first in the outputs from the parent?
     *
     * @param parentType the type the parent must be
     * @param node the node
     * @return <tt>true</tt> if first child, <tt>false</tt> otherwise
     */
    public static boolean isFirstChildOfType(Class<?> parentType, ProcessorDefinition<?> node) {
        if (node == null || node.getParent() == null) {
            return false;
        }

        if (node.getParent().getOutputs().isEmpty()) {
            return false;
        }

        if (!(node.getParent().getClass().equals(parentType))) {
            return false;
        }

        return node.getParent().getOutputs().get(0).equals(node);
    }

    /**
     * Is the given node parent(s) of the given type
     * @param parentType   the parent type
     * @param node         the current node
     * @param recursive    whether or not to check grand parent(s) as well
     * @return <tt>true</tt> if parent(s) is of given type, <tt>false</tt> otherwise
     */
    public static boolean isParentOfType(Class<?> parentType, ProcessorDefinition<?> node, boolean recursive) {
        if (node == null || node.getParent() == null) {
            return false;
        }

        if (parentType.isAssignableFrom(node.getParent().getClass())) {
            return true;
        } else if (recursive) {
            // recursive up the tree of parents
            return isParentOfType(parentType, node.getParent(), true);
        } else {
            // no match
            return false;
        }
    }

    /**
     * Gets the route definition the given node belongs to.
     *
     * @param node the node
     * @return the route, or <tt>null</tt> if not possible to find
     */
    public static RouteDefinition getRoute(ProcessorDefinition<?> node) {
        if (node == null) {
            return null;
        }

        ProcessorDefinition def = node;
        // drill to the top
        while (def != null && def.getParent() != null) {
            def = def.getParent();
        }

        if (def instanceof RouteDefinition) {
            return (RouteDefinition) def;
        } else {
            // not found
            return null;
        }

    }

    @SuppressWarnings("unchecked")
    private static <T> void doFindType(List<ProcessorDefinition> outputs, Class<T> type, List<T> found) {
        if (outputs == null || outputs.isEmpty()) {
            return;
        }

        for (ProcessorDefinition out : outputs) {
            if (type.isInstance(out)) {
                found.add((T)out);
            }

            // send is much common
            if (out instanceof SendDefinition) {
                SendDefinition send = (SendDefinition) out;
                List<ProcessorDefinition> children = send.getOutputs();
                doFindType(children, type, found);
            }

            // special for choice
            if (out instanceof ChoiceDefinition) {
                ChoiceDefinition choice = (ChoiceDefinition) out;
                for (WhenDefinition when : choice.getWhenClauses()) {
                    List<ProcessorDefinition> children = when.getOutputs();
                    doFindType(children, type, found);
                }

                // otherwise is optional
                if (choice.getOtherwise() != null) {
                    List<ProcessorDefinition> children = choice.getOtherwise().getOutputs();
                    doFindType(children, type, found);
                }
            }

            // try children as well
            List<ProcessorDefinition> children = out.getOutputs();
            doFindType(children, type, found);
        }
    }

}
