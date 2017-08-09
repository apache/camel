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
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import javax.xml.namespace.QName;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for ProcessorDefinition and the other model classes.
 */
public final class ProcessorDefinitionHelper {

    private static final Logger LOG = LoggerFactory.getLogger(ProcessorDefinitionHelper.class);
    private static final ThreadLocal<RestoreAction> CURRENT_RESTORE_ACTION = new ThreadLocal<RestoreAction>();

    private ProcessorDefinitionHelper() {
    }

    /**
     * Looks for the given type in the list of outputs and recurring all the children as well.
     *
     * @param outputs list of outputs, can be null or empty.
     * @param type    the type to look for
     * @return the found definitions, or <tt>null</tt> if not found
     */
    public static <T> Iterator<T> filterTypeInOutputs(List<ProcessorDefinition<?>> outputs, Class<T> type) {
        return filterTypeInOutputs(outputs, type, -1);
    }

    /**
     * Looks for the given type in the list of outputs and recurring all the children as well.
     *
     * @param outputs list of outputs, can be null or empty.
     * @param type    the type to look for
     * @param maxDeep maximum levels deep to traverse
     * @return the found definitions, or <tt>null</tt> if not found
     */
    public static <T> Iterator<T> filterTypeInOutputs(List<ProcessorDefinition<?>> outputs, Class<T> type, int maxDeep) {
        List<T> found = new ArrayList<T>();
        doFindType(outputs, type, found, maxDeep);
        return found.iterator();
    }

    /**
     * Looks for the given type in the list of outputs and recurring all the children as well.
     * Will stop at first found and return it.
     *
     * @param outputs list of outputs, can be null or empty.
     * @param type    the type to look for
     * @return the first found type, or <tt>null</tt> if not found
     */
    public static <T> T findFirstTypeInOutputs(List<ProcessorDefinition<?>> outputs, Class<T> type) {
        List<T> found = new ArrayList<T>();
        doFindType(outputs, type, found, -1);
        if (found.isEmpty()) {
            return null;
        }
        return found.iterator().next();
    }

    /**
     * Is the given child the first in the outputs from the parent?
     *
     * @param parentType the type the parent must be
     * @param node       the node
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
     *
     * @param parentType the parent type
     * @param node       the current node
     * @param recursive  whether or not to check grand parent(s) as well
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

    /**
     * Gets the route id the given node belongs to.
     *
     * @param node the node
     * @return the route id, or <tt>null</tt> if not possible to find
     */
    public static String getRouteId(ProcessorDefinition<?> node) {
        RouteDefinition route = getRoute(node);
        return route != null ? route.getId() : null;
    }

    /**
     * Traverses the node, including its children (recursive), and gathers all the node ids.
     *
     * @param node            the target node
     * @param set             set to store ids, if <tt>null</tt> a new set will be created
     * @param onlyCustomId    whether to only store custom assigned ids (ie. {@link org.apache.camel.model.OptionalIdentifiedDefinition#hasCustomIdAssigned()}
     * @param includeAbstract whether to include abstract nodes (ie. {@link org.apache.camel.model.ProcessorDefinition#isAbstract()}
     * @return the set with the found ids.
     */
    public static Set<String> gatherAllNodeIds(ProcessorDefinition<?> node, Set<String> set,
                                               boolean onlyCustomId, boolean includeAbstract) {
        if (node == null) {
            return set;
        }

        // skip abstract
        if (node.isAbstract() && !includeAbstract) {
            return set;
        }

        if (set == null) {
            set = new LinkedHashSet<String>();
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

    private static <T> void doFindType(List<ProcessorDefinition<?>> outputs, Class<T> type, List<T> found, int maxDeep) {

        // do we have any top level abstracts, then we should max deep one more level down
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> void doFindType(List<ProcessorDefinition<?>> outputs, Class<T> type, List<T> found, int current, int maxDeep) {
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

                // only look at when/otherwise if current < maxDeep (or max deep is disabled)
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

                // only look at children if current < maxDeep (or max deep is disabled)
                if (maxDeep < 0 || current < maxDeep) {
                    List<ProcessorDefinition<?>> doTryOut = doTry.getOutputsWithoutCatches();
                    doFindType(doTryOut, type, found, ++current, maxDeep);

                    List<CatchDefinition> doTryCatch = doTry.getCatchClauses();
                    for (CatchDefinition doCatch : doTryCatch) {
                        doFindType(doCatch.getOutputs(), type, found, ++current, maxDeep);
                    }

                    if (doTry.getFinallyClause() != null) {
                        doFindType(doTry.getFinallyClause().getOutputs(), type, found, ++current, maxDeep);
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
     * Is there any outputs in the given list.
     * <p/>
     * Is used for check if the route output has any real outputs (non abstracts)
     *
     * @param outputs         the outputs
     * @param excludeAbstract whether or not to exclude abstract outputs (e.g. skip onException etc.)
     * @return <tt>true</tt> if has outputs, otherwise <tt>false</tt> is returned
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static boolean hasOutputs(List<ProcessorDefinition<?>> outputs, boolean excludeAbstract) {
        if (outputs == null || outputs.isEmpty()) {
            return false;
        }
        if (!excludeAbstract) {
            return !outputs.isEmpty();
        }
        for (ProcessorDefinition output : outputs) {
            if (output.isWrappingEntireOutput()) {
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
     * Determines whether a new thread pool will be created or not.
     * <p/>
     * This is used to know if a new thread pool will be created, and therefore is not shared by others, and therefore
     * exclusive to the definition.
     *
     * @param routeContext the route context
     * @param definition   the node definition which may leverage executor service.
     * @param useDefault   whether to fallback and use a default thread pool, if no explicit configured
     * @return <tt>true</tt> if a new thread pool will be created, <tt>false</tt> if not
     * @see #getConfiguredExecutorService(org.apache.camel.spi.RouteContext, String, ExecutorServiceAwareDefinition, boolean)
     */
    public static boolean willCreateNewThreadPool(RouteContext routeContext, ExecutorServiceAwareDefinition<?> definition, boolean useDefault) {
        ExecutorServiceManager manager = routeContext.getCamelContext().getExecutorServiceManager();
        ObjectHelper.notNull(manager, "ExecutorServiceManager", routeContext.getCamelContext());

        if (definition.getExecutorService() != null) {
            // no there is a custom thread pool configured
            return false;
        } else if (definition.getExecutorServiceRef() != null) {
            ExecutorService answer = routeContext.getCamelContext().getRegistry().lookupByNameAndType(definition.getExecutorServiceRef(), ExecutorService.class);
            // if no existing thread pool, then we will have to create a new thread pool
            return answer == null;
        } else if (useDefault) {
            return true;
        }

        return false;
    }

    /**
     * Will lookup in {@link org.apache.camel.spi.Registry} for a {@link ExecutorService} registered with the given
     * <tt>executorServiceRef</tt> name.
     * <p/>
     * This method will lookup for configured thread pool in the following order
     * <ul>
     * <li>from the {@link org.apache.camel.spi.Registry} if found</li>
     * <li>from the known list of {@link org.apache.camel.spi.ThreadPoolProfile ThreadPoolProfile(s)}.</li>
     * <li>if none found, then <tt>null</tt> is returned.</li>
     * </ul>
     *
     * @param routeContext       the route context
     * @param name               name which is appended to the thread name, when the {@link java.util.concurrent.ExecutorService}
     *                           is created based on a {@link org.apache.camel.spi.ThreadPoolProfile}.
     * @param source             the source to use the thread pool
     * @param executorServiceRef reference name of the thread pool
     * @return the executor service, or <tt>null</tt> if none was found.
     */
    public static ExecutorService lookupExecutorServiceRef(RouteContext routeContext, String name,
                                                           Object source, String executorServiceRef) {

        ExecutorServiceManager manager = routeContext.getCamelContext().getExecutorServiceManager();
        ObjectHelper.notNull(manager, "ExecutorServiceManager", routeContext.getCamelContext());
        ObjectHelper.notNull(executorServiceRef, "executorServiceRef");

        // lookup in registry first and use existing thread pool if exists
        ExecutorService answer = routeContext.getCamelContext().getRegistry().lookupByNameAndType(executorServiceRef, ExecutorService.class);
        if (answer == null) {
            // then create a thread pool assuming the ref is a thread pool profile id
            answer = manager.newThreadPool(source, name, executorServiceRef);
        }
        return answer;
    }

    /**
     * Will lookup and get the configured {@link java.util.concurrent.ExecutorService} from the given definition.
     * <p/>
     * This method will lookup for configured thread pool in the following order
     * <ul>
     * <li>from the definition if any explicit configured executor service.</li>
     * <li>from the {@link org.apache.camel.spi.Registry} if found</li>
     * <li>from the known list of {@link org.apache.camel.spi.ThreadPoolProfile ThreadPoolProfile(s)}.</li>
     * <li>if none found, then <tt>null</tt> is returned.</li>
     * </ul>
     * The various {@link ExecutorServiceAwareDefinition} should use this helper method to ensure they support
     * configured executor services in the same coherent way.
     *
     * @param routeContext the route context
     * @param name         name which is appended to the thread name, when the {@link java.util.concurrent.ExecutorService}
     *                     is created based on a {@link org.apache.camel.spi.ThreadPoolProfile}.
     * @param definition   the node definition which may leverage executor service.
     * @param useDefault   whether to fallback and use a default thread pool, if no explicit configured
     * @return the configured executor service, or <tt>null</tt> if none was configured.
     * @throws IllegalArgumentException is thrown if lookup of executor service in {@link org.apache.camel.spi.Registry} was not found
     */
    public static ExecutorService getConfiguredExecutorService(RouteContext routeContext, String name,
                                                               ExecutorServiceAwareDefinition<?> definition,
                                                               boolean useDefault) throws IllegalArgumentException {
        ExecutorServiceManager manager = routeContext.getCamelContext().getExecutorServiceManager();
        ObjectHelper.notNull(manager, "ExecutorServiceManager", routeContext.getCamelContext());

        // prefer to use explicit configured executor on the definition
        if (definition.getExecutorService() != null) {
            return definition.getExecutorService();
        } else if (definition.getExecutorServiceRef() != null) {
            // lookup in registry first and use existing thread pool if exists
            ExecutorService answer = lookupExecutorServiceRef(routeContext, name, definition, definition.getExecutorServiceRef());
            if (answer == null) {
                throw new IllegalArgumentException("ExecutorServiceRef " + definition.getExecutorServiceRef() + " not found in registry (as an ExecutorService instance) or as a thread pool profile.");
            }
            return answer;
        } else if (useDefault) {
            return manager.newDefaultThreadPool(definition, name);
        }

        return null;
    }

    /**
     * Will lookup in {@link org.apache.camel.spi.Registry} for a {@link ScheduledExecutorService} registered with the given
     * <tt>executorServiceRef</tt> name.
     * <p/>
     * This method will lookup for configured thread pool in the following order
     * <ul>
     * <li>from the {@link org.apache.camel.spi.Registry} if found</li>
     * <li>from the known list of {@link org.apache.camel.spi.ThreadPoolProfile ThreadPoolProfile(s)}.</li>
     * <li>if none found, then <tt>null</tt> is returned.</li>
     * </ul>
     *
     * @param routeContext       the route context
     * @param name               name which is appended to the thread name, when the {@link java.util.concurrent.ExecutorService}
     *                           is created based on a {@link org.apache.camel.spi.ThreadPoolProfile}.
     * @param source             the source to use the thread pool
     * @param executorServiceRef reference name of the thread pool
     * @return the executor service, or <tt>null</tt> if none was found.
     */
    public static ScheduledExecutorService lookupScheduledExecutorServiceRef(RouteContext routeContext, String name,
                                                                             Object source, String executorServiceRef) {

        ExecutorServiceManager manager = routeContext.getCamelContext().getExecutorServiceManager();
        ObjectHelper.notNull(manager, "ExecutorServiceManager", routeContext.getCamelContext());
        ObjectHelper.notNull(executorServiceRef, "executorServiceRef");

        // lookup in registry first and use existing thread pool if exists
        ScheduledExecutorService answer = routeContext.getCamelContext().getRegistry().lookupByNameAndType(executorServiceRef, ScheduledExecutorService.class);
        if (answer == null) {
            // then create a thread pool assuming the ref is a thread pool profile id
            answer = manager.newScheduledThreadPool(source, name, executorServiceRef);
        }
        return answer;
    }

    /**
     * Will lookup and get the configured {@link java.util.concurrent.ScheduledExecutorService} from the given definition.
     * <p/>
     * This method will lookup for configured thread pool in the following order
     * <ul>
     * <li>from the definition if any explicit configured executor service.</li>
     * <li>from the {@link org.apache.camel.spi.Registry} if found</li>
     * <li>from the known list of {@link org.apache.camel.spi.ThreadPoolProfile ThreadPoolProfile(s)}.</li>
     * <li>if none found, then <tt>null</tt> is returned.</li>
     * </ul>
     * The various {@link ExecutorServiceAwareDefinition} should use this helper method to ensure they support
     * configured executor services in the same coherent way.
     *
     * @param routeContext the rout context
     * @param name         name which is appended to the thread name, when the {@link java.util.concurrent.ExecutorService}
     *                     is created based on a {@link org.apache.camel.spi.ThreadPoolProfile}.
     * @param definition   the node definition which may leverage executor service.
     * @param useDefault   whether to fallback and use a default thread pool, if no explicit configured
     * @return the configured executor service, or <tt>null</tt> if none was configured.
     * @throws IllegalArgumentException is thrown if the found instance is not a ScheduledExecutorService type,
     *                                  or lookup of executor service in {@link org.apache.camel.spi.Registry} was not found
     */
    public static ScheduledExecutorService getConfiguredScheduledExecutorService(RouteContext routeContext, String name,
                                                                                 ExecutorServiceAwareDefinition<?> definition,
                                                                                 boolean useDefault) throws IllegalArgumentException {
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
            ScheduledExecutorService answer = lookupScheduledExecutorServiceRef(routeContext, name, definition, definition.getExecutorServiceRef());
            if (answer == null) {
                throw new IllegalArgumentException("ExecutorServiceRef " + definition.getExecutorServiceRef() 
                        + " not found in registry (as an ScheduledExecutorService instance) or as a thread pool profile.");
            }
            return answer;
        } else if (useDefault) {
            return manager.newDefaultScheduledThreadPool(definition, name);
        }

        return null;
    }

    /**
     * The RestoreAction is used to track all the undo/restore actions
     * that need to be performed to undo any resolution to property placeholders
     * that have been applied to the camel route defs.  This class is private
     * so it does not get used directly.  It's mainly used by the {@see createPropertyPlaceholdersChangeReverter()}
     * method.
     */
    private static final class RestoreAction implements Runnable {

        private final RestoreAction prevChange;
        private final ArrayList<Runnable> actions = new ArrayList<Runnable>();

        private RestoreAction(RestoreAction prevChange) {
            this.prevChange = prevChange;
        }

        @Override
        public void run() {
            for (Runnable action : actions) {
                action.run();
            }
            actions.clear();
            if (prevChange == null) {
                CURRENT_RESTORE_ACTION.remove();
            } else {
                CURRENT_RESTORE_ACTION.set(prevChange);
            }
        }
    }

    /**
     * Creates a Runnable which when run will revert property placeholder
     * updates to the camel route definitions that were done after this method
     * is called.  The Runnable MUST be executed and MUST be executed in the
     * same thread this method is called from.  Therefore it's recommend you
     * use it in try/finally block like in the following example:
     * <p/>
     * <pre>
     *   Runnable undo = ProcessorDefinitionHelper.createPropertyPlaceholdersChangeReverter();
     *   try {
     *       // All property resolutions in this block will be reverted.
     *   } finally {
     *       undo.run();
     *   }
     * </pre>
     *
     * @return a Runnable that when run, will revert any property place holder
     * changes that occurred on the current thread .
     */
    public static Runnable createPropertyPlaceholdersChangeReverter() {
        RestoreAction prevChanges = CURRENT_RESTORE_ACTION.get();
        RestoreAction rc = new RestoreAction(prevChanges);
        CURRENT_RESTORE_ACTION.set(rc);
        return rc;
    }

    private static void addRestoreAction(final Object target, final Map<String, Object> properties) {
        addRestoreAction(null, target, properties);
    }
    
    private static void addRestoreAction(final CamelContext context, final Object target, final Map<String, Object> properties) {
        if (properties.isEmpty()) {
            return;
        }

        RestoreAction restoreAction = CURRENT_RESTORE_ACTION.get();
        if (restoreAction == null) {
            return;
        }

        restoreAction.actions.add(new Runnable() {
            @Override
            public void run() {
                try {
                    IntrospectionSupport.setProperties(context, null, target, properties);
                } catch (Exception e) {
                    LOG.warn("Could not restore definition properties", e);
                }
            }
        });
    }

    public static void addPropertyPlaceholdersChangeRevertAction(Runnable action) {
        RestoreAction restoreAction = CURRENT_RESTORE_ACTION.get();
        if (restoreAction == null) {
            return;
        }

        restoreAction.actions.add(action);
    }

    /**
     * Inspects the given definition and resolves any property placeholders from its properties.
     * <p/>
     * This implementation will check all the getter/setter pairs on this instance and for all the values
     * (which is a String type) will be property placeholder resolved.
     *
     * @param routeContext the route context
     * @param definition   the definition
     * @throws Exception is thrown if property placeholders was used and there was an error resolving them
     * @see org.apache.camel.CamelContext#resolvePropertyPlaceholders(String)
     * @see org.apache.camel.component.properties.PropertiesComponent
     * @deprecated use {@link #resolvePropertyPlaceholders(org.apache.camel.CamelContext, Object)}
     */
    @Deprecated
    public static void resolvePropertyPlaceholders(RouteContext routeContext, Object definition) throws Exception {
        resolvePropertyPlaceholders(routeContext.getCamelContext(), definition);
    }

    /**
     * Inspects the given definition and resolves any property placeholders from its properties.
     * <p/>
     * This implementation will check all the getter/setter pairs on this instance and for all the values
     * (which is a String type) will be property placeholder resolved. The definition should implement {@link OtherAttributesAware}
     *
     * @param camelContext the Camel context
     * @param definition   the definition which should implement {@link OtherAttributesAware}
     * @throws Exception is thrown if property placeholders was used and there was an error resolving them
     * @see org.apache.camel.CamelContext#resolvePropertyPlaceholders(String)
     * @see org.apache.camel.component.properties.PropertiesComponent
     */
    public static void resolvePropertyPlaceholders(CamelContext camelContext, Object definition) throws Exception {
        LOG.trace("Resolving property placeholders for: {}", definition);

        // find all getter/setter which we can use for property placeholders
        Map<String, Object> properties = new HashMap<String, Object>();
        IntrospectionSupport.getProperties(definition, properties, null);

        OtherAttributesAware other = null;
        if (definition instanceof OtherAttributesAware) {
            other = (OtherAttributesAware) definition;
        }
        // include additional properties which have the Camel placeholder QName
        // and when the definition parameter is this (otherAttributes belong to this)
        if (other != null && other.getOtherAttributes() != null) {
            for (QName key : other.getOtherAttributes().keySet()) {
                if (Constants.PLACEHOLDER_QNAME.equals(key.getNamespaceURI())) {
                    String local = key.getLocalPart();
                    Object value = other.getOtherAttributes().get(key);
                    if (value instanceof String) {
                        // enforce a properties component to be created if none existed
                        CamelContextHelper.lookupPropertiesComponent(camelContext, true);

                        // value must be enclosed with placeholder tokens
                        String s = (String) value;
                        String prefixToken = camelContext.getPropertyPrefixToken();
                        String suffixToken = camelContext.getPropertySuffixToken();
                        if (prefixToken == null) {
                            throw new IllegalArgumentException("Property with name [" + local + "] uses property placeholders; however, no properties component is configured.");
                        }

                        if (!s.startsWith(prefixToken)) {
                            s = prefixToken + s;
                        }
                        if (!s.endsWith(suffixToken)) {
                            s = s + suffixToken;
                        }
                        value = s;
                    }
                    properties.put(local, value);
                }
            }
        }

        Map<String, Object> changedProperties = new HashMap<String, Object>();
        if (!properties.isEmpty()) {
            LOG.trace("There are {} properties on: {}", properties.size(), definition);
            // lookup and resolve properties for String based properties
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                // the name is always a String
                String name = entry.getKey();
                Object value = entry.getValue();
                if (value instanceof String) {
                    // value must be a String, as a String is the key for a property placeholder
                    String text = (String) value;
                    text = camelContext.resolvePropertyPlaceholders(text);
                    if (text != value) {
                        // invoke setter as the text has changed
                        boolean changed = IntrospectionSupport.setProperty(camelContext.getTypeConverter(), definition, name, text);
                        if (!changed) {
                            throw new IllegalArgumentException("No setter to set property: " + name + " to: " + text + " on: " + definition);
                        }
                        changedProperties.put(name, value);
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Changed property [{}] from: {} to: {}", new Object[]{name, value, text});
                        }
                    }
                }
            }
        }
        addRestoreAction(camelContext, definition, changedProperties);
    }

    /**
     * Inspects the given definition and resolves known fields
     * <p/>
     * This implementation will check all the getter/setter pairs on this instance and for all the values
     * (which is a String type) will check if it refers to a known field (such as on Exchange).
     *
     * @param definition the definition
     */
    public static void resolveKnownConstantFields(Object definition) throws Exception {
        LOG.trace("Resolving known fields for: {}", definition);

        // find all String getter/setter
        Map<String, Object> properties = new HashMap<String, Object>();
        IntrospectionSupport.getProperties(definition, properties, null);

        Map<String, Object> changedProperties = new HashMap<String, Object>();
        if (!properties.isEmpty()) {
            LOG.trace("There are {} properties on: {}", properties.size(), definition);

            // lookup and resolve known constant fields for String based properties
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                String name = entry.getKey();
                Object value = entry.getValue();
                if (value instanceof String) {
                    // we can only resolve String typed values
                    String text = (String) value;

                    // is the value a known field (currently we only support constants from Exchange.class)
                    if (text.startsWith("Exchange.")) {
                        String field = ObjectHelper.after(text, "Exchange.");
                        String constant = ObjectHelper.lookupConstantFieldValue(Exchange.class, field);
                        if (constant != null) {
                            // invoke setter as the text has changed
                            IntrospectionSupport.setProperty(definition, name, constant);
                            changedProperties.put(name, value);
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("Changed property [{}] from: {} to: {}", new Object[]{name, value, constant});
                            }
                        } else {
                            throw new IllegalArgumentException("Constant field with name: " + field + " not found on Exchange.class");
                        }
                    }
                }
            }
        }
        addRestoreAction(definition, changedProperties);
    }

}
