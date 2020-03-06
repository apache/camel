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

import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.ErrorHandlerBuilder;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.VerbDefinition;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.EndpointHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;

import static org.apache.camel.model.ProcessorDefinitionHelper.filterTypeInOutputs;

/**
 * Helper for {@link RouteDefinition}
 * <p/>
 * Utility methods to help preparing {@link RouteDefinition} before they are
 * added to {@link org.apache.camel.CamelContext}.
 */
@SuppressWarnings({"unchecked", "rawtypes", "deprecation"})
public final class RouteDefinitionHelper {

    private RouteDefinitionHelper() {
    }

    /**
     * Gather all the endpoint uri's the route is using from the EIPs that has a
     * static endpoint defined.
     *
     * @param route the route
     * @param includeInputs whether to include inputs
     * @param includeOutputs whether to include outputs
     * @return the endpoints uris
     */
    public static Set<String> gatherAllStaticEndpointUris(CamelContext camelContext, RouteDefinition route, boolean includeInputs, boolean includeOutputs) {
        return gatherAllEndpointUris(camelContext, route, includeInputs, includeOutputs, false);
    }

    /**
     * Gather all the endpoint uri's the route is using from the EIPs that has a
     * static or dynamic endpoint defined.
     *
     * @param route the route
     * @param includeInput whether to include inputs
     * @param includeOutputs whether to include outputs
     * @param includeDynamic whether to include dynamic outputs which has been
     *            in use during routing at runtime, gathered from the
     *            {@link org.apache.camel.spi.RuntimeEndpointRegistry}.
     * @return the endpoints uris
     */
    public static Set<String> gatherAllEndpointUris(CamelContext camelContext, RouteDefinition route, boolean includeInput, boolean includeOutputs, boolean includeDynamic) {
        Set<String> answer = new LinkedHashSet<>();

        if (includeInput) {
            String uri = normalizeUri(route.getInput().getEndpointUri());
            if (uri != null) {
                answer.add(uri);
            }
        }

        if (includeOutputs) {
            Iterator<EndpointRequiredDefinition> it = filterTypeInOutputs(route.getOutputs(), EndpointRequiredDefinition.class);
            while (it.hasNext()) {
                String uri = normalizeUri(it.next().getEndpointUri());
                if (uri != null) {
                    answer.add(uri);
                }
            }
            if (includeDynamic && camelContext.getRuntimeEndpointRegistry() != null) {
                List<String> endpoints = camelContext.getRuntimeEndpointRegistry().getEndpointsPerRoute(route.getId(), false);
                for (String uri : endpoints) {
                    if (uri != null) {
                        answer.add(uri);
                    }
                }
            }
        }

        return answer;
    }

    private static String normalizeUri(String uri) {
        try {
            return URISupport.normalizeUri(uri);
        } catch (UnsupportedEncodingException e) {
            // ignore
        } catch (URISyntaxException e) {
            // ignore
        }
        return null;
    }

    /**
     * Force assigning ids to the routes
     *
     * @param context the camel context
     * @param routes the routes
     * @throws Exception is thrown if error force assign ids to the routes
     */
    public static void forceAssignIds(CamelContext context, List<RouteDefinition> routes) throws Exception {
        // handle custom assigned id's first, and then afterwards assign auto
        // generated ids
        Set<String> customIds = new HashSet<>();

        for (final RouteDefinition route : routes) {
            // if there was a custom id assigned, then make sure to support
            // property placeholders
            if (route.hasCustomIdAssigned()) {
                final String originalId = route.getId();
                final String id = context.resolvePropertyPlaceholders(originalId);
                // only set id if its changed, such as we did property
                // placeholder
                if (!originalId.equals(id)) {
                    route.setId(id);
                }
                customIds.add(id);
            } else {
                RestDefinition rest = route.getRestDefinition();
                if (rest != null && route.isRest()) {
                    VerbDefinition verb = findVerbDefinition(rest, route.getInput().getEndpointUri());
                    if (verb != null) {
                        String id = verb.getId();
                        if (verb.hasCustomIdAssigned() && ObjectHelper.isNotEmpty(id) && !customIds.contains(id)) {
                            route.setId(id);
                            customIds.add(id);
                        }
                    }
                }
            }
        }

        // auto assign route ids
        for (final RouteDefinition route : routes) {
            if (route.getId() == null) {
                // keep assigning id's until we find a free name

                boolean done = false;
                String id = null;
                int attempts = 0;
                while (!done && attempts < 1000) {
                    attempts++;
                    id = route.idOrCreate(context.adapt(ExtendedCamelContext.class).getNodeIdFactory());
                    if (customIds.contains(id)) {
                        // reset id and try again
                        route.setId(null);
                    } else {
                        done = true;
                    }
                }
                if (!done) {
                    throw new IllegalArgumentException("Cannot auto assign id to route: " + route);
                }
                route.setId(id);
                route.setCustomId(false);
                customIds.add(route.getId());
            }
            RestDefinition rest = route.getRestDefinition();
            if (rest != null && route.isRest()) {
                VerbDefinition verb = findVerbDefinition(rest, route.getInput().getEndpointUri());
                if (verb != null) {
                    String id = verb.idOrCreate(context.adapt(ExtendedCamelContext.class).getNodeIdFactory());
                    if (!verb.getUsedForGeneratingNodeId()) {
                        id = route.getId();
                    }
                    verb.setRouteId(id);
                }

                // if its the rest/rest-api endpoints then they should include
                // the route id as well
                if (ObjectHelper.isNotEmpty(route.getInput())) {
                    FromDefinition fromDefinition = route.getInput();
                    String endpointUri = fromDefinition.getEndpointUri();
                    if (ObjectHelper.isNotEmpty(endpointUri) && (endpointUri.startsWith("rest:") || endpointUri.startsWith("rest-api:"))) {
                        Map<String, Object> options = new HashMap<>(1);
                        options.put("routeId", route.getId());
                        endpointUri = URISupport.appendParametersToURI(endpointUri, options);

                        // replace uri with new routeId
                        fromDefinition.setUri(endpointUri);
                        route.setInput(fromDefinition);
                    }
                }
            }
        }
    }

    /**
     * Find verb associated with the route by mapping uri
     */
    private static VerbDefinition findVerbDefinition(RestDefinition rest, String endpointUri) {
        VerbDefinition ret = null;
        String preVerbUri = "";
        for (VerbDefinition verb : rest.getVerbs()) {
            String verbUri = rest.buildFromUri(verb);
            if (endpointUri.startsWith(verbUri) && preVerbUri.length() < verbUri.length()) {
                // if there are multiple verb uri match, select the most
                // specific one
                // for example if the endpoint Uri is
                // rest:get:/user:/{id}/user?produces=text%2Fplain
                // then the verbUri rest:get:/user:/{id}/user should overweigh
                // the est:get:/user:/{id}
                preVerbUri = verbUri;
                ret = verb;
            }
        }
        return ret;
    }

    /**
     * Validates that the target route has no duplicate id's from any of the
     * existing routes.
     *
     * @param target the target route
     * @param routes the existing routes
     * @return <tt>null</tt> if no duplicate id's detected, otherwise the first
     *         found duplicate id is returned.
     */
    public static String validateUniqueIds(RouteDefinition target, List<RouteDefinition> routes) {
        Set<String> routesIds = new LinkedHashSet<>();
        // gather all ids for the existing route, but only include custom ids,
        // and no abstract ids
        // as abstract nodes is cross-cutting functionality such as interceptors
        // etc
        for (RouteDefinition route : routes) {
            // skip target route as we gather ids in a separate set
            if (route == target) {
                continue;
            }
            ProcessorDefinitionHelper.gatherAllNodeIds(route, routesIds, true, false);
        }

        // gather all ids for the target route, but only include custom ids, and
        // no abstract ids
        // as abstract nodes is cross-cutting functionality such as interceptors
        // etc
        Set<String> targetIds = new LinkedHashSet<>();
        ProcessorDefinitionHelper.gatherAllNodeIds(target, targetIds, true, false);

        // now check for clash with the target route
        for (String id : targetIds) {
            if (routesIds.contains(id)) {
                return id;
            }
        }

        return null;
    }

    public static void initParent(ProcessorDefinition parent) {
        List<ProcessorDefinition<?>> children = parent.getOutputs();
        for (ProcessorDefinition child : children) {
            child.setParent(parent);
            if (child.getOutputs() != null && !child.getOutputs().isEmpty()) {
                // recursive the children
                initParent(child);
            }
        }
    }

    public static void prepareRouteForInit(RouteDefinition route, List<ProcessorDefinition<?>> abstracts, List<ProcessorDefinition<?>> lower) {
        // filter the route into abstracts and lower
        for (ProcessorDefinition output : route.getOutputs()) {
            if (output.isAbstract()) {
                abstracts.add(output);
            } else {
                lower.add(output);
            }
        }
    }

    /**
     * Prepares the route.
     * <p/>
     * This method does <b>not</b> mark the route as prepared afterwards.
     *
     * @param context the camel context
     * @param route the route
     */
    public static void prepareRoute(CamelContext context, RouteDefinition route) {
        prepareRoute(context, route, null, null, null, null, null);
    }

    /**
     * Prepares the route which supports context scoped features such as
     * onException, interceptors and onCompletions
     * <p/>
     * This method does <b>not</b> mark the route as prepared afterwards.
     *
     * @param context the camel context
     * @param route the route
     * @param onExceptions optional list of onExceptions
     * @param intercepts optional list of interceptors
     * @param interceptFromDefinitions optional list of interceptFroms
     * @param interceptSendToEndpointDefinitions optional list of
     *            interceptSendToEndpoints
     * @param onCompletions optional list onCompletions
     */
    public static void prepareRoute(CamelContext context, RouteDefinition route, List<OnExceptionDefinition> onExceptions, List<InterceptDefinition> intercepts,
                                    List<InterceptFromDefinition> interceptFromDefinitions, List<InterceptSendToEndpointDefinition> interceptSendToEndpointDefinitions,
                                    List<OnCompletionDefinition> onCompletions) {

        prepareRouteImp(context, route, onExceptions, intercepts, interceptFromDefinitions, interceptSendToEndpointDefinitions, onCompletions);
    }

    /**
     * Prepares the route which supports context scoped features such as
     * onException, interceptors and onCompletions
     * <p/>
     * This method does <b>not</b> mark the route as prepared afterwards.
     *
     * @param context the camel context
     * @param route the route
     * @param onExceptions optional list of onExceptions
     * @param intercepts optional list of interceptors
     * @param interceptFromDefinitions optional list of interceptFroms
     * @param interceptSendToEndpointDefinitions optional list of
     *            interceptSendToEndpoints
     * @param onCompletions optional list onCompletions
     */
    private static void prepareRouteImp(CamelContext context, RouteDefinition route, List<OnExceptionDefinition> onExceptions, List<InterceptDefinition> intercepts,
                                        List<InterceptFromDefinition> interceptFromDefinitions, List<InterceptSendToEndpointDefinition> interceptSendToEndpointDefinitions,
                                        List<OnCompletionDefinition> onCompletions) {

        // init the route inputs
        initRouteInput(context, route.getInput());

        // abstracts is the cross cutting concerns
        List<ProcessorDefinition<?>> abstracts = new ArrayList<>();

        // upper is the cross cutting concerns such as interceptors, error
        // handlers etc
        List<ProcessorDefinition<?>> upper = new ArrayList<>();

        // lower is the regular route
        List<ProcessorDefinition<?>> lower = new ArrayList<>();

        RouteDefinitionHelper.prepareRouteForInit(route, abstracts, lower);

        // parent and error handler builder should be initialized first
        initParentAndErrorHandlerBuilder(context, route, abstracts, onExceptions);
        // validate top-level violations
        validateTopLevel(route.getOutputs());
        // then interceptors
        initInterceptors(context, route, abstracts, upper, intercepts, interceptFromDefinitions, interceptSendToEndpointDefinitions);
        // then on completion
        initOnCompletions(abstracts, upper, onCompletions);
        // then sagas
        initSagas(abstracts, lower);
        // then transactions
        initTransacted(abstracts, lower);
        // then on exception
        initOnExceptions(abstracts, upper, onExceptions);

        // rebuild route as upper + lower
        route.clearOutput();
        route.getOutputs().addAll(lower);
        route.getOutputs().addAll(0, upper);
    }

    /**
     * Sanity check the route, that it has input(s) and outputs.
     *
     * @param route the route
     * @throws IllegalArgumentException is thrown if the route is invalid
     */
    public static void sanityCheckRoute(RouteDefinition route) {
        ObjectHelper.notNull(route, "route");

        if (route.getInput() == null) {
            String msg = "Route has no inputs: " + route;
            if (route.getId() != null) {
                msg = "Route " + route.getId() + " has no inputs: " + route;
            }
            throw new IllegalArgumentException(msg);
        }

        if (route.getOutputs() == null || route.getOutputs().isEmpty()) {
            String msg = "Route has no outputs: " + route;
            if (route.getId() != null) {
                msg = "Route " + route.getId() + " has no outputs: " + route;
            }
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     * Validates that top-level only definitions is not added in the wrong
     * places, such as nested inside a splitter etc.
     */
    private static void validateTopLevel(List<ProcessorDefinition<?>> children) {
        for (ProcessorDefinition child : children) {
            // validate that top-level is only added on the route (eg top level)
            RouteDefinition route = ProcessorDefinitionHelper.getRoute(child);
            boolean parentIsRoute = child.getParent() == route;
            if (child.isTopLevelOnly() && !parentIsRoute) {
                throw new IllegalArgumentException("The output must be added as top-level on the route. Try moving " + child + " to the top of route.");
            }
            if (child.getOutputs() != null && !child.getOutputs().isEmpty()) {
                validateTopLevel(child.getOutputs());
            }
        }
    }

    private static void initRouteInput(CamelContext camelContext, FromDefinition input) {
        // noop
    }

    private static void initParentAndErrorHandlerBuilder(CamelContext context, RouteDefinition route, List<ProcessorDefinition<?>> abstracts,
                                                         List<OnExceptionDefinition> onExceptions) {

        if (context != null) {
            // let the route inherit the error handler builder from camel
            // context if none already set

            // must clone to avoid side effects while building routes using
            // multiple RouteBuilders
            ErrorHandlerFactory builder = context.adapt(ExtendedCamelContext.class).getErrorHandlerFactory();
            if (builder != null) {
                if (builder instanceof ErrorHandlerBuilder) {
                    builder = ((ErrorHandlerBuilder)builder).cloneBuilder();
                    route.setErrorHandlerFactoryIfNull(builder);
                } else {
                    throw new UnsupportedOperationException("The ErrorHandlerFactory must implement ErrorHandlerBuilder");
                }
            }
        }

        // init parent and error handler builder on the route
        initParent(route);

        // set the parent and error handler builder on the global on exceptions
        if (onExceptions != null) {
            for (OnExceptionDefinition global : onExceptions) {
                initParent(global);
            }
        }
    }

    private static void initOnExceptions(List<ProcessorDefinition<?>> abstracts, List<ProcessorDefinition<?>> upper, List<OnExceptionDefinition> onExceptions) {
        // add global on exceptions if any
        if (onExceptions != null && !onExceptions.isEmpty()) {
            for (OnExceptionDefinition output : onExceptions) {
                // these are context scoped on exceptions so set this flag
                output.setRouteScoped(false);
                abstracts.add(output);
            }
        }

        // now add onExceptions to the route
        for (ProcessorDefinition output : abstracts) {
            if (output instanceof OnExceptionDefinition) {
                // on exceptions must be added at top, so the route flow is
                // correct as
                // on exceptions should be the first outputs

                // find the index to add the on exception, it should be in the
                // top
                // but it should add itself after any existing onException
                int index = 0;
                for (int i = 0; i < upper.size(); i++) {
                    ProcessorDefinition up = upper.get(i);
                    if (!(up instanceof OnExceptionDefinition)) {
                        index = i;
                        break;
                    } else {
                        index++;
                    }
                }
                upper.add(index, output);
            }
        }
    }

    private static void initInterceptors(CamelContext context, RouteDefinition route, List<ProcessorDefinition<?>> abstracts, List<ProcessorDefinition<?>> upper,
                                         List<InterceptDefinition> intercepts, List<InterceptFromDefinition> interceptFromDefinitions,
                                         List<InterceptSendToEndpointDefinition> interceptSendToEndpointDefinitions) {

        // move the abstracts interceptors into the dedicated list
        for (ProcessorDefinition processor : abstracts) {
            if (processor instanceof InterceptSendToEndpointDefinition) {
                if (interceptSendToEndpointDefinitions == null) {
                    interceptSendToEndpointDefinitions = new ArrayList<>();
                }
                interceptSendToEndpointDefinitions.add((InterceptSendToEndpointDefinition)processor);
            } else if (processor instanceof InterceptFromDefinition) {
                if (interceptFromDefinitions == null) {
                    interceptFromDefinitions = new ArrayList<>();
                }
                interceptFromDefinitions.add((InterceptFromDefinition)processor);
            } else if (processor instanceof InterceptDefinition) {
                if (intercepts == null) {
                    intercepts = new ArrayList<>();
                }
                intercepts.add((InterceptDefinition)processor);
            }
        }

        doInitInterceptors(context, route, upper, intercepts, interceptFromDefinitions, interceptSendToEndpointDefinitions);
    }

    private static void doInitInterceptors(CamelContext context, RouteDefinition route, List<ProcessorDefinition<?>> upper, List<InterceptDefinition> intercepts,
                                           List<InterceptFromDefinition> interceptFromDefinitions, List<InterceptSendToEndpointDefinition> interceptSendToEndpointDefinitions) {

        // configure intercept
        if (intercepts != null && !intercepts.isEmpty()) {
            for (InterceptDefinition intercept : intercepts) {
                intercept.afterPropertiesSet();
                // init the parent
                initParent(intercept);
                // add as first output so intercept is handled before the actual
                // route and that gives
                // us the needed head start to init and be able to intercept all
                // the remaining processing steps
                upper.add(0, intercept);
            }
        }

        // configure intercept from
        if (interceptFromDefinitions != null && !interceptFromDefinitions.isEmpty()) {
            for (InterceptFromDefinition intercept : interceptFromDefinitions) {

                // should we only apply interceptor for a given endpoint uri
                boolean match = true;
                if (intercept.getUri() != null) {

                    // the uri can have property placeholders so resolve them
                    // first
                    String pattern;
                    try {
                        pattern = context.resolvePropertyPlaceholders(intercept.getUri());
                    } catch (Exception e) {
                        throw RuntimeCamelException.wrapRuntimeCamelException(e);
                    }
                    boolean isRefPattern = pattern.startsWith("ref*") || pattern.startsWith("ref:");

                    match = false;

                    // a bit more logic to lookup the endpoint as it can be
                    // uri/ref based
                    String uri = route.getInput().getEndpointUri();
                    // if the pattern is not a ref itself, then resolve the ref
                    // uris, so we can match the actual uri's with each other
                    if (!isRefPattern) {
                        if (uri != null && uri.startsWith("ref:")) {
                            // its a ref: so lookup the endpoint to get its url
                            String ref = uri.substring(4);
                            uri = CamelContextHelper.getMandatoryEndpoint(context, ref).getEndpointUri();
                        }
                    }
                    if (EndpointHelper.matchEndpoint(context, uri, pattern)) {
                        match = true;
                    }
                }

                if (match) {
                    intercept.afterPropertiesSet();
                    // init the parent
                    initParent(intercept);
                    // add as first output so intercept is handled before the
                    // actual route and that gives
                    // us the needed head start to init and be able to intercept
                    // all the remaining processing steps
                    upper.add(0, intercept);
                }
            }
        }

        // configure intercept send to endpoint
        if (interceptSendToEndpointDefinitions != null && !interceptSendToEndpointDefinitions.isEmpty()) {
            for (InterceptSendToEndpointDefinition intercept : interceptSendToEndpointDefinitions) {
                intercept.afterPropertiesSet();
                // init the parent
                initParent(intercept);
                // add as first output so intercept is handled before the actual
                // route and that gives
                // us the needed head start to init and be able to intercept all
                // the remaining processing steps
                upper.add(0, intercept);
            }
        }
    }

    private static void initOnCompletions(List<ProcessorDefinition<?>> abstracts, List<ProcessorDefinition<?>> upper, List<OnCompletionDefinition> onCompletions) {
        List<OnCompletionDefinition> completions = new ArrayList<>();

        // find the route scoped onCompletions
        for (ProcessorDefinition out : abstracts) {
            if (out instanceof OnCompletionDefinition) {
                completions.add((OnCompletionDefinition)out);
            }
        }

        // only add global onCompletion if there are no route already
        if (completions.isEmpty() && onCompletions != null) {
            completions = onCompletions;
            // init the parent
            for (OnCompletionDefinition global : completions) {
                initParent(global);
            }
        }

        // are there any completions to init at all?
        if (completions.isEmpty()) {
            return;
        }

        upper.addAll(completions);
    }

    private static void initSagas(List<ProcessorDefinition<?>> abstracts, List<ProcessorDefinition<?>> lower) {
        SagaDefinition saga = null;

        // add to correct type
        for (ProcessorDefinition<?> type : abstracts) {
            if (type instanceof SagaDefinition) {
                if (saga == null) {
                    saga = (SagaDefinition)type;
                } else {
                    throw new IllegalArgumentException("The route can only have one saga defined");
                }
            }
        }

        if (saga != null) {
            // the outputs should be moved to the transacted policy
            saga.getOutputs().addAll(lower);
            // and add it as the single output
            lower.clear();
            lower.add(saga);
        }
    }

    private static void initTransacted(List<ProcessorDefinition<?>> abstracts, List<ProcessorDefinition<?>> lower) {
        TransactedDefinition transacted = null;

        // add to correct type
        for (ProcessorDefinition<?> type : abstracts) {
            if (type instanceof TransactedDefinition) {
                if (transacted == null) {
                    transacted = (TransactedDefinition)type;
                } else {
                    throw new IllegalArgumentException("The route can only have one transacted defined");
                }
            }
        }

        if (transacted != null) {
            // the outputs should be moved to the transacted policy
            transacted.getOutputs().addAll(lower);
            // and add it as the single output
            lower.clear();
            lower.add(transacted);
        }
    }

    /**
     * Force assigning ids to the give node and all its children (recursively).
     * <p/>
     * This is needed when doing tracing or the likes, where each node should
     * have its id assigned so the tracing can pin point exactly.
     *
     * @param context the camel context
     * @param processor the node
     */
    public static void forceAssignIds(CamelContext context, final ProcessorDefinition processor) {
        // force id on the child
        processor.idOrCreate(context.adapt(ExtendedCamelContext.class).getNodeIdFactory());

        // if there was a custom id assigned, then make sure to support property
        // placeholders
        if (processor.hasCustomIdAssigned()) {
            try {
                final String originalId = processor.getId();
                String id = context.resolvePropertyPlaceholders(originalId);
                // only set id if its changed, such as we did property
                // placeholder
                if (!originalId.equals(id)) {
                    processor.setId(id);
                }
            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        }

        List<ProcessorDefinition<?>> children = processor.getOutputs();
        if (children != null && !children.isEmpty()) {
            for (ProcessorDefinition child : children) {
                forceAssignIds(context, child);
            }
        }
    }

    public static String getRouteMessage(String route) {
        // ensure to sanitize uri's in the route so we do not show sensitive
        // information such as passwords
        route = URISupport.sanitizeUri(route);
        // cut the route after 60 chars so it won't be too big in the message
        // users just need to be able to identify the route so they know where
        // to look
        if (route.length() > 60) {
            return route.substring(0, 60) + "...";
        } else {
            return route;
        }
    }
}
