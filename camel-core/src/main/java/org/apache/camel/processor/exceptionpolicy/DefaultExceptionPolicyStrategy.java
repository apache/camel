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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.model.ExceptionType;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * The default strategy used in Camel to resolve the {@link org.apache.camel.model.ExceptionType} that should
 * handle the thrown exception.
 * <p/>
 * <b>Selection strategy:</b>
 * <br/>This strategy applies the following rules:
 * <ul>
 * <li>Will walk the exception hieracy from bottom upwards till the thrown exception, meaning that the most outer caused
 * by is selected first, ending with the thrown exception itself</li>
 * <li>The exception type must be configured with an Exception that is an instance of the thrown exception, this
 * is tested using the {@link #filter(org.apache.camel.model.ExceptionType, Class, Throwable)} method. </li>
 * <li>If the exception type has exactly the thrown exception then its selected as its an exact match</li>
 * <li>Otherwise the type that has an exception that is the closests super of the thrown exception is selected
 * (recurring up the exception hierarchy)</li>
 * </ul>
 * <p/>
 * <b>Fine grained matching:</b>
 * <br/> If the {@link ExceptionType} has a when defined with an expression the type is also matches against
 * the current exchange using the {@link #matchesWhen(org.apache.camel.model.ExceptionType, org.apache.camel.Exchange)}
 * method. This can be used to for more fine grained matching, so you can e.g. define multiple sets of
 * exception types with the same exception class(es) but have a predicate attached to select which to select at runtime.
 */
public class DefaultExceptionPolicyStrategy implements ExceptionPolicyStrategy {

    private static final transient Log LOG = LogFactory.getLog(DefaultExceptionPolicyStrategy.class);

    public ExceptionType getExceptionPolicy(Map<ExceptionPolicyKey, ExceptionType> exceptionPolicices, Exchange exchange,
                                            Throwable exception) {

        // recursive up the tree using the iterator
        Iterator<Throwable> it = createExceptionIterator(exception); 
        while (it.hasNext()) {
            ExceptionType type = findMatchedExceptionPolicy(exceptionPolicices, exchange, it.next());
            if (type != null) {
                return type;
            }
        }

        // no type found
        return null;
    }


    private ExceptionType findMatchedExceptionPolicy(Map<ExceptionPolicyKey, ExceptionType> exceptionPolicices, Exchange exchange,
                                               Throwable exception) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Finding best suited exception policy for thrown exception " + exception.getClass().getName());
        }

        // the goal is to find the exception with the same/closet inheritance level as the target exception being thrown
        int targetLevel = getInheritanceLevel(exception.getClass());
        // candidate is the best candidate found so far to return
        ExceptionType candidate = null;
        // difference in inheritance level between the current candidate and the thrown exception (target level)
        int candidateDiff = Integer.MAX_VALUE;

        // loop through all the entries and find the best candidates to use
        Set<Map.Entry<ExceptionPolicyKey, ExceptionType>> entries = exceptionPolicices.entrySet();
        for (Map.Entry<ExceptionPolicyKey, ExceptionType> entry : entries) {
            Class clazz = entry.getKey().getExceptionClass();
            ExceptionType type = entry.getValue();

            if (filter(type, clazz, exception)) {

                // must match
                if (!matchesWhen(type, exchange)) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("The type did not match when: " + type);
                    }
                    continue;
                }

                // exact match then break
                if (clazz.equals(exception.getClass())) {
                    candidate = type;
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

        if (LOG.isDebugEnabled()) {
            if (candidate != null) {
                LOG.debug("Using " + candidate + " as the exception policy");
            } else {
                LOG.debug("No candidate found to be used as exception policy");
            }
        }

        return candidate;
    }

    /**
     * Strategy to filter the given type exception class with the thrown exception
     *
     * @param type           the exception type
     * @param exceptionClass the current exception class for testing
     * @param exception      the thrown exception
     * @return <tt>true</tt> if the to current exception class is a candidate, <tt>false</tt> to skip it.
     */
    protected boolean filter(ExceptionType type, Class exceptionClass, Throwable exception) {
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
     * @param type     the exception type
     * @param exchange the current {@link Exchange}
     * @return <tt>true</tt> if matched, <tt>false</tt> otherwise.
     */
    protected boolean matchesWhen(ExceptionType type, Exchange exchange) {
        if (type.getOnWhen() == null || type.getOnWhen().getExpression() == null) {
            // if no predicate then it's always a match
            return true;
        }
        return type.getOnWhen().getExpression().matches(exchange);
    }

    /**
     * Strategy method creating the iterator to walk the exception in the order Camel should use
     * for find the {@link ExceptionType} should be used.
     * <p/>
     * The default iterator will walk from the bottom upwards
     * (the last caused by going upwards to the exception)
     *
     * @param exception  the exception
     * @return the iterator
     */
    protected Iterator<Throwable> createExceptionIterator(Throwable exception) {
        return new ExceptionIterator(exception);
    }

    private static int getInheritanceLevel(Class clazz) {
        if (clazz == null || "java.lang.Object".equals(clazz.getName())) {
            return 0;
        }
        return 1 + getInheritanceLevel(clazz.getSuperclass());
    }

    private class ExceptionIterator implements Iterator<Throwable> {
        private List<Throwable> tree = new ArrayList<Throwable>();
        private Iterator<Throwable> it;

        public ExceptionIterator(Throwable exception) {
            Throwable current = exception;
            // spool to the bottom of the caused by tree
            while (current != null) {
                tree.add(current);
                current = current.getCause();
            }

            // reverse tree so we go from bottom to top
            Collections.reverse(tree);
            it = tree.iterator();
        }

        public boolean hasNext() {
            return it.hasNext();
        }

        public Throwable next() {
            return it.next();
        }

        public void remove() {
            it.remove();
        }
    }

}
