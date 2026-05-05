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
package org.apache.camel.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.NamedNode;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.ResourceAware;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.FileUtil;

/**
 * Helper class for ProcessorDefinition and the other model classes.
 */
public final class ProcessorDefinitionHelper {

    private ProcessorDefinitionHelper() {
    }

    /**
     * Looks for the given type in the list of outputs and recurring all the children as well.
     *
     * @param  outputs list of outputs, can be null or empty.
     * @param  type    the type to look for
     * @return         the found definitions, or <tt>null</tt> if not found
     */
    public static <T> Collection<T> filterTypeInOutputs(List<ProcessorDefinition<?>> outputs, Class<T> type) {
        return filterTypeInOutputs(outputs, type, -1);
    }

    /**
     * Looks for the given type in the list of outputs and recurring all the children as well.
     *
     * @param  outputs list of outputs, can be null or empty.
     * @param  type    the type to look for
     * @param  maxDeep maximum levels deep to traverse
     * @return         the found definitions, or <tt>null</tt> if not found
     */
    public static <T> Collection<T> filterTypeInOutputs(List<ProcessorDefinition<?>> outputs, Class<T> type, int maxDeep) {
        List<T> found = new ArrayList<>();
        doFindType(outputs, type, found, maxDeep);
        return found;
    }

    /**
     * Looks for the given type in the list of outputs and recurring all the children as well. Will stop at first found
     * and return it.
     *
     * @param  outputs list of outputs, can be null or empty.
     * @param  type    the type to look for
     * @return         the first found type, or <tt>null</tt> if not found
     */
    public static <T> T findFirstTypeInOutputs(List<ProcessorDefinition<?>> outputs, Class<T> type) {
        List<T> found = new ArrayList<>();
        doFindType(outputs, type, found, -1);
        if (found.isEmpty()) {
            return null;
        }
        return found.iterator().next();
    }

    /**
     * Is the given child the first in the outputs from the parent?
     *
     * @param  parentType the type the parent must be
     * @param  node       the node
     * @return            <tt>true</tt> if first child, <tt>false</tt> otherwise
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
     *
     * @param  parentType the parent type
     * @param  node       the current node
     * @param  recursive  whether or not to check grand parent(s) as well
     * @return            <tt>true</tt> if parent(s) is of given type, <tt>false</tt> otherwise
     */
    public static boolean isParentOfType(
            Class<? extends ProcessorDefinition> parentType, ProcessorDefinition<?> node, boolean recursive) {
        return findFirstParentOfType(parentType, node, recursive) != null;
    }

    /**
     * Is the given node parent(s) of the given type
     *
     * @param  parentType the parent type
     * @param  node       the current node
     * @param  recursive  whether or not to check grand parent(s) as well
     * @return            <tt>true</tt> if parent(s) is of given type, <tt>false</tt> otherwise
     */
    public static <T extends ProcessorDefinition> T findFirstParentOfType(
            Class<T> parentType, ProcessorDefinition<?> node, boolean recursive) {
        if (node == null || node.getParent() == null) {
            return null;
        }

        if (parentType.isAssignableFrom(node.getParent().getClass())) {
            return parentType.cast(node.getParent());
        } else if (recursive) {
            // recursive up the tree of parents
            return findFirstParentOfType(parentType, node.getParent(), true);
        } else {
            // no match
            return null;
        }
    }

    /**
     * Gets the route definition the given node belongs to.
     *
     * @param  node the node
     * @return      the route, or <tt>null</tt> if not possible to find
     */
    public static RouteDefinition getRoute(NamedNode node) {
        if (node == null) {
            return null;
        }

        NamedNode def = node;
        // drill to the top
        while (def != null && def.getParent() != null) {
            def = def.getParent();
        }

        if (def instanceof RouteDefinition rd) {
            return rd;
        } else {
            // not found
            return null;
        }
    }

    /**
     * Gets the route id the given node belongs to.
     *
     * @param  node the node
     * @return      the route id, or <tt>null</tt> if not possible to find
     */
    public static String getRouteId(NamedNode node) {
        RouteDefinition route = getRoute(node);
        return route != null ? route.getId() : null;
    }

    /**
     * Traverses the node, including its children (recursive), and gathers all the node ids.
     *
     * @param  node            the target node
     * @param  set             list to store ids, if <tt>null</tt> a new list will be created
     * @param  onlyCustomId    whether to only store custom assigned ids (ie.
     *                         {@link org.apache.camel.model.OptionalIdentifiedDefinition#hasCustomIdAssigned()}
     * @param  includeAbstract whether to include abstract nodes (ie.
     *                         {@link org.apache.camel.model.ProcessorDefinition#isAbstract()}
     * @return                 the list with the found ids.
     */
    public static List<String> gatherAllNodeIds(
            ProcessorDefinition<?> node, List<String> set, boolean onlyCustomId, boolean includeAbstract) {
        if (node == null) {
            return set;
        }

        // skip abstract
        if (node.isAbstract() && !includeAbstract) {
            return set;
        }

        if (set == null) {
            set = new ArrayList<>();
        }

        // add ourselves
        if (node.getId() != null) {
            if (!onlyCustomId || node.hasCustomIdAssigned()) {
                set.add(node.getId());
            }
        }

        // traverse children (including non-ProcessorDefinition nodes like WhenDefinition, OtherwiseDefinition)
        for (NamedNode child : node.getChildren()) {
            if (child instanceof ProcessorDefinition<?> pd) {
                gatherAllNodeIds(pd, set, onlyCustomId, includeAbstract);
            } else {
                gatherAllNodeIdsFromNode(child, set, onlyCustomId);
            }
        }

        return set;
    }

    private static void gatherAllNodeIdsFromNode(NamedNode node, List<String> set, boolean onlyCustomId) {
        if (node instanceof OptionalIdentifiedDefinition<?> oid) {
            if (oid.getId() != null) {
                if (!onlyCustomId || oid.hasCustomIdAssigned()) {
                    set.add(oid.getId());
                }
            }
        }
        for (NamedNode child : node.getChildren()) {
            if (child instanceof ProcessorDefinition<?> pd) {
                gatherAllNodeIds(pd, set, onlyCustomId, false);
            } else {
                gatherAllNodeIdsFromNode(child, set, onlyCustomId);
            }
        }
    }

    /**
     * Resets (nulls) all the auto assigned ids on the node and the nested children (outputs)
     */
    public static void resetAllAutoAssignedNodeIds(ProcessorDefinition<?> node) {
        if (node == null) {
            return;
        }

        // skip abstract
        if (node.isAbstract()) {
            return;
        }

        if (node.getId() != null) {
            if (!node.hasCustomIdAssigned()) {
                node.setId(null);
            }
        }

        // traverse children (including non-ProcessorDefinition nodes like WhenDefinition, OtherwiseDefinition)
        for (NamedNode child : node.getChildren()) {
            if (child instanceof ProcessorDefinition<?> pd) {
                resetAllAutoAssignedNodeIds(pd);
            } else {
                resetAllAutoAssignedNodeIdsFromNode(child);
            }
        }
    }

    private static void resetAllAutoAssignedNodeIdsFromNode(NamedNode node) {
        if (node instanceof OptionalIdentifiedDefinition<?> oid) {
            if (oid.getId() != null && !oid.hasCustomIdAssigned()) {
                oid.setId(null);
            }
        }
        for (NamedNode child : node.getChildren()) {
            if (child instanceof ProcessorDefinition<?> pd) {
                resetAllAutoAssignedNodeIds(pd);
            } else {
                resetAllAutoAssignedNodeIdsFromNode(child);
            }
        }
    }

    private static <T> void doFindType(List<ProcessorDefinition<?>> outputs, Class<T> type, List<T> found, int maxDeep) {

        // do we have any top level abstracts, then we should max deep one more
        // level down
        // as that is really what we want to traverse as well
        if (maxDeep > 0) {
            for (ProcessorDefinition<?> out : outputs) {
                if (out.isAbstract() && out.isTopLevelOnly()) {
                    maxDeep = maxDeep + 1;
                    break;
                }
            }
        }

        // start from level 1
        doFindTypeInNodes(outputs, type, found, 1, maxDeep);
    }

    private static <T> void doFindTypeInNodes(
            List<? extends NamedNode> nodes, Class<T> type, List<T> found, int current, int maxDeep) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }

        // break out
        if (maxDeep > 0 && current > maxDeep) {
            return;
        }

        for (NamedNode node : nodes) {
            if (type.isInstance(node)) {
                found.add(type.cast(node));
            }
            // structural nodes (non-ProcessorDefinition children like WhenDefinition, OtherwiseDefinition)
            // are transparent for depth counting — they don't consume a maxDeep level
            int childLevel = node instanceof ProcessorDefinition ? current + 1 : current;
            doFindTypeInNodes(node.getChildren(), type, found, childLevel, maxDeep);
        }
    }

    /**
     * Prepares the output to gather source location:line-number if possible. This operation is slow as it uses
     * StackTrace so this should only be used when Camel Debugger is enabled.
     *
     * @param node the node
     */
    public static void prepareSourceLocation(Resource resource, NamedNode node) {
        if (resource != null) {
            node.setLocation(resource.getLocation());

            String ext = FileUtil.onlyExt(resource.getLocation(), true);
            if ("groovy".equals(ext) || "js".equals(ext)) {
                // we cannot get line number for groovy/java-script/java-shell
                return;
            }
        }

        // line number may already be set if parsed via XML, YAML etc.
        int number = node.getLineNumber();
        if (number < 0) {
            StackTraceElement[] st = Thread.currentThread().getStackTrace();
            // skip first stack as that is this method
            for (int i = 1; i < st.length; i++) {
                StackTraceElement e = st[i];
                if (!e.getClassName().startsWith("org.apache.camel.model") &&
                        !e.getClassName().startsWith("org.apache.camel.builder.RouteBuilder") &&
                        !e.getClassName().startsWith("org.apache.camel.reifier.RouteReifier") &&
                        !e.getClassName().startsWith("org.apache.camel.impl") &&
                        !e.getClassName().startsWith("org.apache.camel.support") &&
                        !e.getClassName().startsWith("org.apache.camel.dsl")) {
                    // when we are no longer in model/RouteBuilder, we have found the location:line-number
                    node.setLineNumber(e.getLineNumber());
                    if (node.getLocation() == null) {
                        String name = e.getFileName();
                        if (name == null) {
                            name = e.getClassName();
                        }
                        // find out what scheme for location as it can be file, classpath etc
                        if (!ResourceHelper.hasScheme(name)) {
                            String scheme = findParentSourceLocationScheme(node);
                            if (scheme != null) {
                                name = scheme + name;
                            }
                        }
                        node.setLocation(name);
                    }
                    return;
                }
            }
        }
    }

    /**
     * Returns the level of the node in the route tree. Level 1 is the root level, level 2 is a child of an EIP, and so
     * forth\
     *
     * @deprecated use {@link NamedNode#getLevel()}
     */
    @Deprecated(since = "4.17.0")
    public static int getNodeLevel(NamedNode node) {
        return node.getLevel();
    }

    /**
     * Finds the source location scheme from the parent nodes.
     */
    public static String findParentSourceLocationScheme(NamedNode node) {
        while (node != null && node.getParent() != null) {
            String location = node.getLocation();
            if (ResourceHelper.hasScheme(location)) {
                return ResourceHelper.getScheme(location);
            }
            node = node.getParent();
        }
        return null;
    }

    /**
     * Performs a depp copy of the list of model classes
     *
     * @param  models list of model classes
     * @return        a new list containing a deep copy of the model classes
     */
    public static List deepCopyDefinitions(List models) {
        var answer = new ArrayList();
        if (models != null) {
            for (var def : models) {
                if (def instanceof CopyableDefinition<?> copy) {
                    def = copy.copyDefinition();
                }
                answer.add(def);
            }
        }
        return answer;
    }

    /**
     * Whether the model should be wrapped in an error handler or not.
     *
     * Some EIPs like try/catch, circuit breaker, multicast, and kamelets have impact on whether the model should be
     * wrapped or not.
     */
    public static boolean shouldWrapInErrorHandler(
            CamelContext context, ProcessorDefinition<?> definition,
            ProcessorDefinition<?> child, Boolean inheritErrorHandler) {
        boolean wrap = false;

        // set the error handler, must be done after init as we can set the
        // error handler as first in the chain
        if (definition instanceof TryDefinition || definition instanceof CatchDefinition
                || definition instanceof FinallyDefinition) {
            // do not use error handler for try .. catch .. finally blocks as it
            // will handle errors itself
        } else if (ProcessorDefinitionHelper.isParentOfType(TryDefinition.class, definition, true)
                || ProcessorDefinitionHelper.isParentOfType(CatchDefinition.class, definition, true)
                || ProcessorDefinitionHelper.isParentOfType(FinallyDefinition.class, definition, true)) {
            // do not use error handler for try .. catch .. finally blocks as it
            // will handle errors itself
            // by checking that any of our parent(s) is not a try .. catch or
            // finally type
        } else if (definition instanceof OnExceptionDefinition
                || ProcessorDefinitionHelper.isParentOfType(OnExceptionDefinition.class, definition, true)) {
            // do not use error handler for onExceptions blocks as it will
            // handle errors itself
        } else if (definition instanceof CircuitBreakerDefinition
                || ProcessorDefinitionHelper.isParentOfType(CircuitBreakerDefinition.class, definition, true)) {
            // do not use error handler for circuit breaker
            // however if inherit error handler is enabled, we need to wrap an error handler on the parent
            if (inheritErrorHandler != null && inheritErrorHandler && child == null) {
                // only wrap the parent (not the children of the circuit breaker)
                wrap = true;
            }
        } else if (definition instanceof MulticastDefinition def) {
            // do not use error handler for multicast as it offers fine-grained
            // error handlers for its outputs
            // however if share unit of work is enabled, we need to wrap an
            // error handler on the multicast parent
            Boolean isShareUnitOfWork = CamelContextHelper.parseBoolean(context, def.getShareUnitOfWork());
            if (isShareUnitOfWork != null && isShareUnitOfWork && child == null) {
                // only wrap the parent (not the children of the multicast)
                wrap = true;
            }
        } else {
            // use error handler by default or if configured to do so
            wrap = true;
        }
        return wrap;
    }

    /**
     * Gets the resource the given node belongs to.
     *
     * @param  node the node
     * @return      the resource, or <tt>null</tt> if not possible to find
     */
    public static Resource getResource(NamedNode node) {
        if (node == null) {
            return null;
        }
        if (node instanceof ResourceAware ra) {
            return ra.getResource();
        }

        NamedNode def = node;
        while (def != null && def.getParent() != null) {
            def = def.getParent();
            if (def instanceof ResourceAware ra) {
                return ra.getResource();
            }
        }

        // not found
        return null;
    }

}
