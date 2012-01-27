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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.NoSuchBeanException;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.util.ObjectHelper;

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
    @SuppressWarnings("rawtypes")
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
    @SuppressWarnings("rawtypes")
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

        ProcessorDefinition<?> def = node;
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

    @SuppressWarnings({"unchecked", "rawtypes"})
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

    /**
     * Is there any outputs in the given list.
     * <p/>
     * Is used for check if the route output has any real outputs (non abstracts)
     *
     * @param outputs           the outputs
     * @param excludeAbstract   whether or not to exclude abstract outputs (e.g. skip onException etc.)
     * @return <tt>true</tt> if has outputs, otherwise <tt>false</tt> is returned
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static boolean hasOutputs(List<ProcessorDefinition> outputs, boolean excludeAbstract) {
        if (outputs == null || outputs.isEmpty()) {
            return false;
        }
        if (!excludeAbstract) {
            return !outputs.isEmpty();
        }
        for (ProcessorDefinition output : outputs) {
            if (output instanceof TransactedDefinition || output instanceof PolicyDefinition) {
                // special for those as they wrap entire output, so we should just check its output
                return hasOutputs(output.getOutputs(), excludeAbstract);
            }
            if (!output.isAbstract()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Will lookup and get the configured {@link java.util.concurrent.ExecutorService} from the given definition.
     * <p/>
     * This method will lookup for configured thread pool in the following order
     * <ul>
     *   <li>from the definition if any explicit configured executor service.</li>
     *   <li>from the {@link org.apache.camel.spi.Registry} if found</li>
     *   <li>from the known list of {@link org.apache.camel.spi.ThreadPoolProfile ThreadPoolProfile(s)}.</li>
     *   <li>if none found, then <tt>null</tt> is returned.</li>
     * </ul>
     * The various {@link ExecutorServiceAwareDefinition} should use this helper method to ensure they support
     * configured executor services in the same coherent way.
     *
     * @param routeContext   the route context
     * @param name           name which is appended to the thread name, when the {@link java.util.concurrent.ExecutorService}
     *                       is created based on a {@link org.apache.camel.spi.ThreadPoolProfile}.
     * @param definition     the node definition which may leverage executor service.
     * @return the configured executor service, or <tt>null</tt> if none was configured.
     * @throws NoSuchBeanException is thrown if lookup of executor service in {@link org.apache.camel.spi.Registry} was not found
     */
    public static ExecutorService getConfiguredExecutorService(RouteContext routeContext, String name,
                                                               ExecutorServiceAwareDefinition<?> definition) throws NoSuchBeanException {
        ExecutorServiceManager manager = routeContext.getCamelContext().getExecutorServiceManager();
        ObjectHelper.notNull(manager, "ExecutorServiceManager", routeContext.getCamelContext());

        // prefer to use explicit configured executor on the definition
        if (definition.getExecutorService() != null) {
            return definition.getExecutorService();
        } else if (definition.getExecutorServiceRef() != null) {
            // lookup in registry first and use existing thread pool if exists
            ExecutorService answer = routeContext.getCamelContext().getRegistry().lookup(definition.getExecutorServiceRef(), ExecutorService.class);
            if (answer == null) {
                // then create a thread pool assuming the ref is a thread pool profile id
                answer = manager.newThreadPool(definition, name, definition.getExecutorServiceRef());
            }
            if (answer == null) {
                throw new NoSuchBeanException(definition.getExecutorServiceRef(), "ExecutorService");
            }
            return answer;
        }

        return null;
    }

    /**
     * Will lookup and get the configured {@link java.util.concurrent.ScheduledExecutorService} from the given definition.
     * <p/>
     * This method will lookup for configured thread pool in the following order
     * <ul>
     *   <li>from the definition if any explicit configured executor service.</li>
     *   <li>from the {@link org.apache.camel.spi.Registry} if found</li>
     *   <li>from the known list of {@link org.apache.camel.spi.ThreadPoolProfile ThreadPoolProfile(s)}.</li>
     *   <li>if none found, then <tt>null</tt> is returned.</li>
     * </ul>
     * The various {@link ExecutorServiceAwareDefinition} should use this helper method to ensure they support
     * configured executor services in the same coherent way.
     *
     * @param routeContext   the rout context
     * @param name           name which is appended to the thread name, when the {@link java.util.concurrent.ExecutorService}
     *                       is created based on a {@link org.apache.camel.spi.ThreadPoolProfile}.
     * @param definition     the node definition which may leverage executor service.
     * @return the configured executor service, or <tt>null</tt> if none was configured.
     * @throws IllegalArgumentException is thrown if the found instance is not a ScheduledExecutorService type.
     * @throws NoSuchBeanException is thrown if lookup of executor service in {@link org.apache.camel.spi.Registry} was not found
     */
    public static ScheduledExecutorService getConfiguredScheduledExecutorService(RouteContext routeContext, String name,
                                                               ExecutorServiceAwareDefinition<?> definition) throws IllegalArgumentException, NoSuchBeanException {
        ExecutorServiceManager manager = routeContext.getCamelContext().getExecutorServiceManager();
        ObjectHelper.notNull(manager, "ExecutorServiceManager", routeContext.getCamelContext());

        // prefer to use explicit configured executor on the definition
        if (definition.getExecutorService() != null) {
            ExecutorService executorService = definition.getExecutorService();
            if (executorService instanceof ScheduledExecutorService) {
                return (ScheduledExecutorService) executorService;
            }
            throw new IllegalArgumentException("ExecutorServiceRef " + definition.getExecutorServiceRef() + " is not an ScheduledExecutorService instance");
        } else if (definition.getExecutorServiceRef() != null) {
            ScheduledExecutorService answer = routeContext.getCamelContext().getRegistry().lookup(definition.getExecutorServiceRef(), ScheduledExecutorService.class);
            if (answer == null) {
                // then create a thread pool assuming the ref is a thread pool profile id
                ThreadPoolProfile profile = manager.getThreadPoolProfile(definition.getExecutorServiceRef());
                if (profile != null) {
                    answer = manager.newScheduledThreadPool(definition, name, profile);
                }
            }
            if (answer == null) {
                throw new NoSuchBeanException(definition.getExecutorServiceRef(), "ScheduledExecutorService");
            }
            return answer;
        }

        return null;
    }

}
