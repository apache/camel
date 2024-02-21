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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.camel.NamedNode;
import org.apache.camel.spi.Resource;
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

        ProcessorDefinition<?> def = (ProcessorDefinition) node;
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
     * @param  set             set to store ids, if <tt>null</tt> a new set will be created
     * @param  onlyCustomId    whether to only store custom assigned ids (ie.
     *                         {@link org.apache.camel.model.OptionalIdentifiedDefinition#hasCustomIdAssigned()}
     * @param  includeAbstract whether to include abstract nodes (ie.
     *                         {@link org.apache.camel.model.ProcessorDefinition#isAbstract()}
     * @return                 the set with the found ids.
     */
    public static Set<String> gatherAllNodeIds(
            ProcessorDefinition<?> node, Set<String> set, boolean onlyCustomId, boolean includeAbstract) {
        if (node == null) {
            return set;
        }

        // skip abstract
        if (node.isAbstract() && !includeAbstract) {
            return set;
        }

        if (set == null) {
            set = new LinkedHashSet<>();
        }

        // add ourselves
        if (node.getId() != null) {
            if (!onlyCustomId || node.hasCustomIdAssigned() && onlyCustomId) {
                set.add(node.getId());
            }
        }

        // traverse outputs and recursive children as well
        List<ProcessorDefinition<?>> children = node.getOutputs();
        if (children != null && !children.isEmpty()) {
            for (ProcessorDefinition<?> child : children) {
                // traverse children also
                gatherAllNodeIds(child, set, onlyCustomId, includeAbstract);
            }
        }

        return set;
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

        // traverse outputs and recursive children as well
        List<ProcessorDefinition<?>> children = node.getOutputs();
        if (children != null && !children.isEmpty()) {
            for (ProcessorDefinition<?> child : children) {
                // traverse children also
                resetAllAutoAssignedNodeIds(child);
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
        doFindType(outputs, type, found, 1, maxDeep);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static <
            T> void doFindType(List<ProcessorDefinition<?>> outputs, Class<T> type, List<T> found, int current, int maxDeep) {
        if (outputs == null || outputs.isEmpty()) {
            return;
        }

        // break out
        if (maxDeep > 0 && current > maxDeep) {
            return;
        }

        for (ProcessorDefinition out : outputs) {

            // send is much common
            if (out instanceof SendDefinition) {
                SendDefinition send = (SendDefinition) out;
                List<ProcessorDefinition<?>> children = send.getOutputs();
                doFindType(children, type, found, ++current, maxDeep);
            }

            // special for choice
            if (out instanceof ChoiceDefinition) {
                ChoiceDefinition choice = (ChoiceDefinition) out;

                // ensure to add ourself if we match also
                if (type.isInstance(choice)) {
                    found.add((T) choice);
                }

                // only look at when/otherwise if current < maxDeep (or max deep
                // is disabled)
                if (maxDeep < 0 || current < maxDeep) {
                    for (WhenDefinition when : choice.getWhenClauses()) {
                        if (type.isInstance(when)) {
                            found.add((T) when);
                        }
                        List<ProcessorDefinition<?>> children = when.getOutputs();
                        doFindType(children, type, found, ++current, maxDeep);
                    }

                    // otherwise is optional
                    if (choice.getOtherwise() != null) {
                        List<ProcessorDefinition<?>> children = choice.getOtherwise().getOutputs();
                        doFindType(children, type, found, ++current, maxDeep);
                    }
                }

                // do not check children as we already did that
                continue;
            }

            // special for try ... catch ... finally
            if (out instanceof TryDefinition) {
                TryDefinition doTry = (TryDefinition) out;

                // ensure to add ourself if we match also
                if (type.isInstance(doTry)) {
                    found.add((T) doTry);
                }

                // only look at children if current < maxDeep (or max deep is
                // disabled)
                if (maxDeep < 0 || current < maxDeep) {
                    List<ProcessorDefinition<?>> doTryOut = doTry.getOutputsWithoutCatches();
                    doFindType(doTryOut, type, found, ++current, maxDeep);

                    List<CatchDefinition> doTryCatch = doTry.getCatchClauses();
                    for (CatchDefinition doCatch : doTryCatch) {
                        // ensure to add ourself if we match also
                        if (type.isInstance(doCatch)) {
                            found.add((T) doCatch);
                        }
                        doFindType(doCatch.getOutputs(), type, found, ++current, maxDeep);
                    }

                    if (doTry.getFinallyClause() != null) {
                        // ensure to add ourself if we match also
                        FinallyDefinition doFinally = doTry.getFinallyClause();
                        if (type.isInstance(doFinally)) {
                            found.add((T) doFinally);
                        }
                        doFindType(doFinally.getOutputs(), type, found, ++current, maxDeep);
                    }
                }

                // do not check children as we already did that
                continue;
            }

            // special for some types which has special outputs
            if (out instanceof OutputDefinition) {
                OutputDefinition outDef = (OutputDefinition) out;

                // ensure to add ourself if we match also
                if (type.isInstance(outDef)) {
                    found.add((T) outDef);
                }

                List<ProcessorDefinition<?>> outDefOut = outDef.getOutputs();
                doFindType(outDefOut, type, found, ++current, maxDeep);

                // do not check children as we already did that
                continue;
            }

            if (type.isInstance(out)) {
                found.add((T) out);
            }

            // try children as well
            List<ProcessorDefinition<?>> children = out.getOutputs();
            doFindType(children, type, found, ++current, maxDeep);
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
            if ("groovy".equals(ext) || "js".equals(ext) || "jsh".equals(ext)) {
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
     * forth
     */
    public static int getNodeLevel(NamedNode node) {
        int level = 0;
        while (node != null && node.getParent() != null) {
            boolean shallow = node instanceof WhenDefinition || node instanceof OtherwiseDefinition;
            node = node.getParent();
            if (!shallow) {
                level++;
            }
        }
        return level;
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

}
