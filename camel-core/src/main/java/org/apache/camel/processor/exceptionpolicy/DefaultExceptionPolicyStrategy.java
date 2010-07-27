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
package org.apache.camel.processor.exceptionpolicy;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.camel.Exchange;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.ProcessorDefinitionHelper;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * The default strategy used in Camel to resolve the {@link org.apache.camel.model.OnExceptionDefinition} that should
 * handle the thrown exception.
 * <p/>
 * <b>Selection strategy:</b>
 * <br/>This strategy applies the following rules:
 * <ul>
 * <li>Will walk the exception hierarchy from bottom upwards till the thrown exception, meaning that the most outer caused
 * by is selected first, ending with the thrown exception itself. The method {@link #createExceptionIterator(Throwable)}
 * provides the Iterator used for the walking.</li>
 * <li>The exception type must be configured with an Exception that is an instance of the thrown exception, this
 * is tested using the {@link #filter(org.apache.camel.model.OnExceptionDefinition, Class, Throwable)} method.
 * By default the filter uses <tt>instanceof</tt> test.</li>
 * <li>If the exception type has <b>exactly</b> the thrown exception then its selected as its an exact match</li>
 * <li>Otherwise the type that has an exception that is the closets super of the thrown exception is selected
 * (recurring up the exception hierarchy)</li>
 * </ul>
 * <p/>
 * <b>Fine grained matching:</b>
 * <br/> If the {@link OnExceptionDefinition} has a when defined with an expression the type is also matches against
 * the current exchange using the {@link #matchesWhen(org.apache.camel.model.OnExceptionDefinition, org.apache.camel.Exchange)}
 * method. This can be used to for more fine grained matching, so you can e.g. define multiple sets of
 * exception types with the same exception class(es) but have a predicate attached to select which to select at runtime.
 */
public class DefaultExceptionPolicyStrategy implements ExceptionPolicyStrategy {

    private static final transient Log LOG = LogFactory.getLog(DefaultExceptionPolicyStrategy.class);

    public OnExceptionDefinition getExceptionPolicy(Map<ExceptionPolicyKey, OnExceptionDefinition> exceptionPolicies,
                                                    Exchange exchange, Throwable exception) {

        Map<Integer, OnExceptionDefinition> candidates = new TreeMap<Integer, OnExceptionDefinition>();

        // recursive up the tree using the iterator
        boolean exactMatch = false;
        Iterator<Throwable> it = createExceptionIterator(exception);
        while (!exactMatch && it.hasNext()) {
            // we should stop looking if we have found an exact match
            exactMatch = findMatchedExceptionPolicy(exceptionPolicies, exchange, it.next(), candidates);
        }

        // now go through the candidates and find the best

        if (LOG.isTraceEnabled()) {
            LOG.trace("Found " + candidates.size() + " candidates");
        }

        if (candidates.isEmpty()) {
            // no type found
            return null;
        } else {
            // return the first in the map as its sorted and
            return candidates.values().iterator().next();
        }
    }


    private boolean findMatchedExceptionPolicy(Map<ExceptionPolicyKey, OnExceptionDefinition> exceptionPolicies,
                                               Exchange exchange, Throwable exception,
                                               Map<Integer, OnExceptionDefinition> candidates) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Finding best suited exception policy for thrown exception " + exception.getClass().getName());
        }

        // the goal is to find the exception with the same/closet inheritance level as the target exception being thrown
        int targetLevel = getInheritanceLevel(exception.getClass());
        // candidate is the best candidate found so far to return
        OnExceptionDefinition candidate = null;
        // difference in inheritance level between the current candidate and the thrown exception (target level)
        int candidateDiff = Integer.MAX_VALUE;

        // loop through all the entries and find the best candidates to use
        Set<Map.Entry<ExceptionPolicyKey, OnExceptionDefinition>> entries = exceptionPolicies.entrySet();
        for (Map.Entry<ExceptionPolicyKey, OnExceptionDefinition> entry : entries) {
            Class<?> clazz = entry.getKey().getExceptionClass();
            OnExceptionDefinition type = entry.getValue();

            // if OnException is route scoped then the current route (Exchange) must match
            // so we will not pick an OnException from another route
            if (exchange != null && exchange.getUnitOfWork() != null) {
                RouteDefinition route = exchange.getUnitOfWork().getRouteContext() != null ? exchange.getUnitOfWork().getRouteContext().getRoute() : null;
                RouteDefinition typeRoute = ProcessorDefinitionHelper.getRoute(type);
                if (route != null && typeRoute != null && route != typeRoute) {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("The type is scoped for route: " + typeRoute.getId() + " however Exchange is at route: " + route.getId());
                    }
                    continue;
                }
            }

            if (filter(type, clazz, exception)) {

                // must match
                if (!matchesWhen(type, exchange)) {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("The type did not match when: " + type);
                    }
                    continue;
                }

                // exact match then break
                if (clazz.equals(exception.getClass())) {
                    candidate = type;
                    candidateDiff = 0;
                    break;
                }

                // not an exact match so find the best candidate
                int level = getInheritanceLevel(clazz);
                int diff = targetLevel - level;

                if (diff < candidateDiff) {
                    // replace with a much better candidate
                    candidate = type;
                    candidateDiff = diff;
                }
            }
        }

        if (candidate != null) {
            if (!candidates.containsKey(candidateDiff)) {
                // only add as candidate if we do not already have it registered with that level
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Adding " + candidate + " as candidate at level " + candidateDiff);
                }
                candidates.put(candidateDiff, candidate);
            } else {
                // we have an existing candidate already which we should prefer to use
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Existing candidate " + candidates.get(candidateDiff)
                        + " takes precedence over " + candidate + " at level " + candidateDiff);
                }
            }
        }

        // if we found a exact match then we should stop continue looking
        boolean exactMatch = candidateDiff == 0;
        if (LOG.isTraceEnabled() && exactMatch) {
            LOG.trace("Exact match found for candidate: " + candidate);
        }
        return exactMatch;
    }

    /**
     * Strategy to filter the given type exception class with the thrown exception
     *
     * @param type           the exception type
     * @param exceptionClass the current exception class for testing
     * @param exception      the thrown exception
     * @return <tt>true</tt> if the to current exception class is a candidate, <tt>false</tt> to skip it.
     */
    protected boolean filter(OnExceptionDefinition type, Class<?> exceptionClass, Throwable exception) {
        // must be instance of check to ensure that the exceptionClass is one type of the thrown exception
        return exceptionClass.isInstance(exception);
    }

    /**
     * Strategy method for matching the exception type with the current exchange.
     * <p/>
     * This default implementation will match as:
     * <ul>
     * <li>Always true if no when predicate on the exception type
     * <li>Otherwise the when predicate is matches against the current exchange
     * </ul>
     *
     * @param definition     the exception definition
     * @param exchange the current {@link Exchange}
     * @return <tt>true</tt> if matched, <tt>false</tt> otherwise.
     */
    protected boolean matchesWhen(OnExceptionDefinition definition, Exchange exchange) {
        if (definition.getOnWhen() == null || definition.getOnWhen().getExpression() == null) {
            // if no predicate then it's always a match
            return true;
        }
        return definition.getOnWhen().getExpression().matches(exchange);
    }

    /**
     * Strategy method creating the iterator to walk the exception in the order Camel should use
     * for find the {@link OnExceptionDefinition} should be used.
     * <p/>
     * The default iterator will walk from the bottom upwards
     * (the last caused by going upwards to the exception)
     *
     * @param exception  the exception
     * @return the iterator
     */
    protected Iterator<Throwable> createExceptionIterator(Throwable exception) {
        return ObjectHelper.createExceptionIterator(exception);
    }

    private static int getInheritanceLevel(Class<?> clazz) {
        if (clazz == null || "java.lang.Object".equals(clazz.getName())) {
            return 0;
        }
        return 1 + getInheritanceLevel(clazz.getSuperclass());
    }
}
