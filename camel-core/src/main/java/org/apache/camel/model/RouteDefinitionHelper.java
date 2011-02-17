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
import java.util.List;

import org.apache.camel.impl.InterceptSendToEndpoint;
import org.apache.camel.util.EndpointHelper;

/**
 * Helper for {@link RouteDefinition}
 * <p/>
 * Utility methods to help preparing {@link RouteDefinition} before they are added to
 * {@link org.apache.camel.CamelContext}.
 *
 * @version 
 */
public final class RouteDefinitionHelper {

    private RouteDefinitionHelper() {
    }

    public static void initParent(RouteDefinition route) {
        for (ProcessorDefinition output : route.getOutputs()) {
            output.setParent(route);
            if (output.getOutputs() != null) {
                // recursive the outputs
                initParent(output);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static void initParent(ProcessorDefinition parent) {
        List<ProcessorDefinition> children = parent.getOutputs();
        for (ProcessorDefinition child : children) {
            child.setParent(parent);
            if (child.getOutputs() != null) {
                // recursive the children
                initParent(child);
            }
        }
    }

    public static void prepareRouteForInit(RouteDefinition route, List<ProcessorDefinition> abstracts,
                                           List<ProcessorDefinition> lower) {
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
     * @param route the route
     */
    public static void prepareRoute(RouteDefinition route) {
        prepareRoute(route, null, null, null, null, null);
    }

    /**
     * Prepares the route which supports context scoped features such as onException, interceptors and onCompletions
     * <p/>
     * This method does <b>not</b> mark the route as prepared afterwards.
     *
     * @param route                              the route
     * @param onExceptions                       optional list of onExceptions
     * @param intercepts                         optional list of interceptors
     * @param interceptFromDefinitions           optional list of interceptFroms
     * @param interceptSendToEndpointDefinitions optional list of interceptSendToEndpoints
     * @param onCompletions                      optional list onCompletions
     */
    public static void prepareRoute(RouteDefinition route,
                                    List<OnExceptionDefinition> onExceptions,
                                    List<InterceptDefinition> intercepts,
                                    List<InterceptFromDefinition> interceptFromDefinitions,
                                    List<InterceptSendToEndpointDefinition> interceptSendToEndpointDefinitions,
                                    List<OnCompletionDefinition> onCompletions) {

        // at first init the parent
        RouteDefinitionHelper.initParent(route);

        // abstracts is the cross cutting concerns
        List<ProcessorDefinition> abstracts = new ArrayList<ProcessorDefinition>();

        // upper is the cross cutting concerns such as interceptors, error handlers etc
        List<ProcessorDefinition> upper = new ArrayList<ProcessorDefinition>();

        // lower is the regular route
        List<ProcessorDefinition> lower = new ArrayList<ProcessorDefinition>();

        RouteDefinitionHelper.prepareRouteForInit(route, abstracts, lower);

        // interceptors should be first for the cross cutting concerns
        initInterceptors(route, abstracts, upper, intercepts, interceptFromDefinitions, interceptSendToEndpointDefinitions);
        // then on completion
        initOnCompletions(abstracts, upper, onCompletions);
        // then transactions
        initTransacted(abstracts, lower);
        // then on exception
        initOnExceptions(abstracts, upper, onExceptions);

        // rebuild route as upper + lower
        route.clearOutput();
        route.getOutputs().addAll(lower);
        route.getOutputs().addAll(0, upper);
    }

    private static void initOnExceptions(List<ProcessorDefinition> abstracts, List<ProcessorDefinition> upper,
                                         List<OnExceptionDefinition> onExceptions) {
        // add global on exceptions if any
        if (onExceptions != null && !onExceptions.isEmpty()) {
            // init the parent
            for (OnExceptionDefinition global : onExceptions) {
                RouteDefinitionHelper.initParent(global);
            }
            abstracts.addAll(onExceptions);
        }

        // now add onExceptions to the route
        for (ProcessorDefinition output : abstracts) {
            if (output instanceof OnExceptionDefinition) {
                // on exceptions must be added at top, so the route flow is correct as
                // on exceptions should be the first outputs
                upper.add(0, output);
            }
        }
    }

    private static void initInterceptors(RouteDefinition route, List<ProcessorDefinition> abstracts, List<ProcessorDefinition> upper,
                                         List<InterceptDefinition> intercepts,
                                         List<InterceptFromDefinition> interceptFromDefinitions,
                                         List<InterceptSendToEndpointDefinition> interceptSendToEndpointDefinitions) {

        // move the abstracts interceptors into the dedicated list
        for (ProcessorDefinition processor : abstracts) {
            if (processor instanceof InterceptSendToEndpointDefinition) {
                if (interceptSendToEndpointDefinitions == null) {
                    interceptSendToEndpointDefinitions = new ArrayList<InterceptSendToEndpointDefinition>();
                }
                interceptSendToEndpointDefinitions.add((InterceptSendToEndpointDefinition) processor);
            } else if (processor instanceof InterceptFromDefinition) {
                if (interceptFromDefinitions == null) {
                    interceptFromDefinitions = new ArrayList<InterceptFromDefinition>();
                }
                interceptFromDefinitions.add((InterceptFromDefinition) processor);
            } else if (processor instanceof InterceptDefinition) {
                if (intercepts == null) {
                    intercepts = new ArrayList<InterceptDefinition>();
                }
                intercepts.add((InterceptDefinition) processor);
            }
        }

        doInitInterceptors(route, upper, intercepts, interceptFromDefinitions, interceptSendToEndpointDefinitions);
    }

    private static void doInitInterceptors(RouteDefinition route, List<ProcessorDefinition> upper,
                                         List<InterceptDefinition> intercepts,
                                         List<InterceptFromDefinition> interceptFromDefinitions,
                                         List<InterceptSendToEndpointDefinition> interceptSendToEndpointDefinitions) {

        // configure intercept
        if (intercepts != null && !intercepts.isEmpty()) {
            for (InterceptDefinition intercept : intercepts) {
                intercept.afterPropertiesSet();
                // init the parent
                RouteDefinitionHelper.initParent(intercept);
                // add as first output so intercept is handled before the actual route and that gives
                // us the needed head start to init and be able to intercept all the remaining processing steps
                upper.add(0, intercept);
            }
        }

        // configure intercept from
        if (interceptFromDefinitions != null && !interceptFromDefinitions.isEmpty()) {
            for (InterceptFromDefinition intercept : interceptFromDefinitions) {

                // should we only apply interceptor for a given endpoint uri
                boolean match = true;
                if (intercept.getUri() != null) {
                    match = false;
                    for (FromDefinition input : route.getInputs()) {
                        if (EndpointHelper.matchEndpoint(input.getUri(), intercept.getUri())) {
                            match = true;
                            break;
                        }
                    }
                }

                if (match) {
                    intercept.afterPropertiesSet();
                    // init the parent
                    RouteDefinitionHelper.initParent(intercept);
                    // add as first output so intercept is handled before the actual route and that gives
                    // us the needed head start to init and be able to intercept all the remaining processing steps
                    upper.add(0, intercept);
                }
            }
        }

        // configure intercept send to endpoint
        if (interceptSendToEndpointDefinitions != null && !interceptSendToEndpointDefinitions.isEmpty()) {
            for (InterceptSendToEndpointDefinition intercept : interceptSendToEndpointDefinitions) {
                intercept.afterPropertiesSet();
                // init the parent
                RouteDefinitionHelper.initParent(intercept);
                // add as first output so intercept is handled before the actual route and that gives
                // us the needed head start to init and be able to intercept all the remaining processing steps
                upper.add(0, intercept);
            }
        }
    }

    private static void initOnCompletions(List<ProcessorDefinition> abstracts, List<ProcessorDefinition> upper,
                                          List<OnCompletionDefinition> onCompletions) {
        List<OnCompletionDefinition> completions = new ArrayList<OnCompletionDefinition>();

        // find the route scoped onCompletions
        for (ProcessorDefinition out : abstracts) {
            if (out instanceof OnCompletionDefinition) {
                completions.add((OnCompletionDefinition) out);
            }
        }

        // only add global onCompletion if there are no route already
        if (completions.isEmpty() && onCompletions != null) {
            completions = onCompletions;
            // init the parent
            for (OnCompletionDefinition global : completions) {
                RouteDefinitionHelper.initParent(global);
            }
        }

        // are there any completions to init at all?
        if (completions.isEmpty()) {
            return;
        }

        upper.addAll(completions);
    }

    private static void initTransacted(List<ProcessorDefinition> abstracts, List<ProcessorDefinition> lower) {
        TransactedDefinition transacted = null;

        // add to correct type
        for (ProcessorDefinition type : abstracts) {
            if (type instanceof TransactedDefinition) {
                if (transacted == null) {
                    transacted = (TransactedDefinition) type;
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

}
