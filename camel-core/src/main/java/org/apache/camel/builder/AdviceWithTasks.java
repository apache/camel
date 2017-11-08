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
package org.apache.camel.builder;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.camel.Endpoint;
import org.apache.camel.model.ChoiceDefinition;
import org.apache.camel.model.EndpointRequiredDefinition;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.ProcessorDefinitionHelper;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.TransactedDefinition;
import org.apache.camel.util.EndpointHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link AdviceWithTask} tasks which are used by the {@link AdviceWithRouteBuilder}.
 */
public final class AdviceWithTasks {

    private static final Logger LOG = LoggerFactory.getLogger(AdviceWithTasks.class);

    private AdviceWithTasks() {
        // utility class
    }

    /**
     * Match by is used for pluggable match by logic.
     */
    private interface MatchBy {

        String getId();

        boolean match(ProcessorDefinition<?> processor);
    }

    /**
     * Will match by id of the processor.
     */
    private static final class MatchById implements MatchBy {

        private final String id;

        private MatchById(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public boolean match(ProcessorDefinition<?> processor) {
            if (id.equals("*")) {
                // make sure the processor which id isn't be set is matched.
                return true;
            }
            return EndpointHelper.matchPattern(processor.getId(), id);
        }
    }

    /**
     * Will match by the to string representation of the processor.
     */
    private static final class MatchByToString implements MatchBy {

        private final String toString;

        private MatchByToString(String toString) {
            this.toString = toString;
        }

        public String getId() {
            return toString;
        }

        public boolean match(ProcessorDefinition<?> processor) {
            return EndpointHelper.matchPattern(processor.toString(), toString);
        }
    }

    /**
     * Will match by the sending to endpoint uri representation of the processor.
     */
    private static final class MatchByToUri implements MatchBy {

        private final String toUri;

        private MatchByToUri(String toUri) {
            this.toUri = toUri;
        }

        public String getId() {
            return toUri;
        }

        public boolean match(ProcessorDefinition<?> processor) {
            if (processor instanceof EndpointRequiredDefinition) {
                String uri = ((EndpointRequiredDefinition) processor).getEndpointUri();
                return EndpointHelper.matchPattern(uri, toUri);
            }
            return false;
        }
    }

    /**
     * Will match by the type of the processor.
     */
    private static final class MatchByType implements MatchBy {

        private final Class<?> type;

        private MatchByType(Class<?> type) {
            this.type = type;
        }

        public String getId() {
            return type.getSimpleName();
        }

        public boolean match(ProcessorDefinition<?> processor) {
            return type.isAssignableFrom(processor.getClass());
        }
    }

    public static AdviceWithTask replaceByToString(final RouteDefinition route, final String toString, final ProcessorDefinition<?> replace,
                                                   boolean selectFirst, boolean selectLast, int selectFrom, int selectTo, int maxDeep) {
        MatchBy matchBy = new MatchByToString(toString);
        return doReplace(route, matchBy, replace, selectFirst, selectLast, selectFrom, selectTo, maxDeep);
    }

    public static AdviceWithTask replaceByToUri(final RouteDefinition route, final String toUri, final ProcessorDefinition<?> replace,
                                                boolean selectFirst, boolean selectLast, int selectFrom, int selectTo, int maxDeep) {
        MatchBy matchBy = new MatchByToUri(toUri);
        return doReplace(route, matchBy, replace, selectFirst, selectLast, selectFrom, selectTo, maxDeep);
    }

    public static AdviceWithTask replaceById(final RouteDefinition route, final String id, final ProcessorDefinition<?> replace,
                                             boolean selectFirst, boolean selectLast, int selectFrom, int selectTo, int maxDeep) {
        MatchBy matchBy = new MatchById(id);
        return doReplace(route, matchBy, replace, selectFirst, selectLast, selectFrom, selectTo, maxDeep);
    }

    public static AdviceWithTask replaceByType(final RouteDefinition route, final Class<?> type, final ProcessorDefinition<?> replace,
                                               boolean selectFirst, boolean selectLast, int selectFrom, int selectTo, int maxDeep) {
        MatchBy matchBy = new MatchByType(type);
        return doReplace(route, matchBy, replace, selectFirst, selectLast, selectFrom, selectTo, maxDeep);
    }

    private static AdviceWithTask doReplace(final RouteDefinition route, final MatchBy matchBy, final ProcessorDefinition<?> replace,
                                            boolean selectFirst, boolean selectLast, int selectFrom, int selectTo, int maxDeep) {
        return new AdviceWithTask() {
            public void task() throws Exception {
                Iterator<ProcessorDefinition<?>> it = AdviceWithTasks.createMatchByIterator(route, matchBy, selectFirst, selectLast, selectFrom, selectTo, maxDeep);
                boolean match = false;
                while (it.hasNext()) {
                    ProcessorDefinition<?> output = it.next();
                    if (matchBy.match(output)) {
                        List<ProcessorDefinition<?>> outputs = getOutputs(output);
                        if (outputs != null) {
                            int index = outputs.indexOf(output);
                            if (index != -1) {
                                match = true;
                                outputs.add(index + 1, replace);
                                Object old = outputs.remove(index);
                                // must set parent on the node we added in the route
                                replace.setParent(output.getParent());
                                LOG.info("AdviceWith ({}) : [{}] --> replace [{}]", matchBy.getId(), old, replace);
                            }
                        }
                    }
                }

                if (!match) {
                    throw new IllegalArgumentException("There are no outputs which matches: " + matchBy.getId() + " in the route: " + route);
                }
            }
        };
    }

    public static AdviceWithTask removeByToString(final RouteDefinition route, final String toString,
                                                  boolean selectFirst, boolean selectLast, int selectFrom, int selectTo, int maxDeep) {
        MatchBy matchBy = new MatchByToString(toString);
        return doRemove(route, matchBy, selectFirst, selectLast, selectFrom, selectTo, maxDeep);
    }

    public static AdviceWithTask removeByToUri(final RouteDefinition route, final String toUri,
                                               boolean selectFirst, boolean selectLast, int selectFrom, int selectTo, int maxDeep) {
        MatchBy matchBy = new MatchByToUri(toUri);
        return doRemove(route, matchBy, selectFirst, selectLast, selectFrom, selectTo, maxDeep);
    }

    public static AdviceWithTask removeById(final RouteDefinition route, final String id,
                                            boolean selectFirst, boolean selectLast, int selectFrom, int selectTo, int maxDeep) {
        MatchBy matchBy = new MatchById(id);
        return doRemove(route, matchBy, selectFirst, selectLast, selectFrom, selectTo, maxDeep);
    }

    public static AdviceWithTask removeByType(final RouteDefinition route, final Class<?> type,
                                              boolean selectFirst, boolean selectLast, int selectFrom, int selectTo, int maxDeep) {
        MatchBy matchBy = new MatchByType(type);
        return doRemove(route, matchBy, selectFirst, selectLast, selectFrom, selectTo, maxDeep);
    }

    private static AdviceWithTask doRemove(final RouteDefinition route, final MatchBy matchBy,
                                           boolean selectFirst, boolean selectLast, int selectFrom, int selectTo, int maxDeep) {
        return new AdviceWithTask() {
            public void task() throws Exception {
                boolean match = false;
                Iterator<ProcessorDefinition<?>> it = AdviceWithTasks.createMatchByIterator(route, matchBy, selectFirst, selectLast, selectFrom, selectTo, maxDeep);
                while (it.hasNext()) {
                    ProcessorDefinition<?> output = it.next();
                    if (matchBy.match(output)) {
                        List<ProcessorDefinition<?>> outputs = getOutputs(output);
                        if (outputs != null) {
                            int index = outputs.indexOf(output);
                            if (index != -1) {
                                match = true;
                                Object old = outputs.remove(index);
                                LOG.info("AdviceWith ({}) : [{}] --> remove", matchBy.getId(), old);
                            }
                        }
                    }
                }

                if (!match) {
                    throw new IllegalArgumentException("There are no outputs which matches: " + matchBy.getId() + " in the route: " + route);
                }
            }
        };
    }

    public static AdviceWithTask beforeByToString(final RouteDefinition route, final String toString, final ProcessorDefinition<?> before,
                                                  boolean selectFirst, boolean selectLast, int selectFrom, int selectTo, int maxDeep) {
        MatchBy matchBy = new MatchByToString(toString);
        return doBefore(route, matchBy, before, selectFirst, selectLast, selectFrom, selectTo, maxDeep);
    }

    public static AdviceWithTask beforeByToUri(final RouteDefinition route, final String toUri, final ProcessorDefinition<?> before,
                                               boolean selectFirst, boolean selectLast, int selectFrom, int selectTo, int maxDeep) {
        MatchBy matchBy = new MatchByToUri(toUri);
        return doBefore(route, matchBy, before, selectFirst, selectLast, selectFrom, selectTo, maxDeep);
    }

    public static AdviceWithTask beforeById(final RouteDefinition route, final String id, final ProcessorDefinition<?> before,
                                            boolean selectFirst, boolean selectLast, int selectFrom, int selectTo, int maxDeep) {
        MatchBy matchBy = new MatchById(id);
        return doBefore(route, matchBy, before, selectFirst, selectLast, selectFrom, selectTo, maxDeep);
    }

    public static AdviceWithTask beforeByType(final RouteDefinition route, final Class<?> type, final ProcessorDefinition<?> before,
                                              boolean selectFirst, boolean selectLast, int selectFrom, int selectTo, int maxDeep) {
        MatchBy matchBy = new MatchByType(type);
        return doBefore(route, matchBy, before, selectFirst, selectLast, selectFrom, selectTo, maxDeep);
    }

    private static AdviceWithTask doBefore(final RouteDefinition route, final MatchBy matchBy, final ProcessorDefinition<?> before,
                                           boolean selectFirst, boolean selectLast, int selectFrom, int selectTo, int maxDeep) {
        return new AdviceWithTask() {
            public void task() throws Exception {
                boolean match = false;
                Iterator<ProcessorDefinition<?>> it = AdviceWithTasks.createMatchByIterator(route, matchBy, selectFirst, selectLast, selectFrom, selectTo, maxDeep);
                while (it.hasNext()) {
                    ProcessorDefinition<?> output = it.next();
                    if (matchBy.match(output)) {
                        List<ProcessorDefinition<?>> outputs = getOutputs(output);
                        if (outputs != null) {
                            int index = outputs.indexOf(output);
                            if (index != -1) {
                                match = true;
                                Object existing = outputs.get(index);
                                outputs.add(index, before);
                                // must set parent on the node we added in the route
                                before.setParent(output.getParent());
                                LOG.info("AdviceWith ({}) : [{}] --> before [{}]", matchBy.getId(), existing, before);
                            }
                        }
                    }
                }

                if (!match) {
                    throw new IllegalArgumentException("There are no outputs which matches: " + matchBy.getId() + " in the route: " + route);
                }
            }
        };
    }

    public static AdviceWithTask afterByToString(final RouteDefinition route, final String toString, final ProcessorDefinition<?> after,
                                                 boolean selectFirst, boolean selectLast, int selectFrom, int selectTo, int maxDeep) {
        MatchBy matchBy = new MatchByToString(toString);
        return doAfter(route, matchBy, after, selectFirst, selectLast, selectFrom, selectTo, maxDeep);
    }

    public static AdviceWithTask afterByToUri(final RouteDefinition route, final String toUri, final ProcessorDefinition<?> after,
                                              boolean selectFirst, boolean selectLast, int selectFrom, int selectTo, int maxDeep) {
        MatchBy matchBy = new MatchByToUri(toUri);
        return doAfter(route, matchBy, after, selectFirst, selectLast, selectFrom, selectTo, maxDeep);
    }

    public static AdviceWithTask afterById(final RouteDefinition route, final String id, final ProcessorDefinition<?> after,
                                           boolean selectFirst, boolean selectLast, int selectFrom, int selectTo, int maxDeep) {
        MatchBy matchBy = new MatchById(id);
        return doAfter(route, matchBy, after, selectFirst, selectLast, selectFrom, selectTo, maxDeep);
    }

    public static AdviceWithTask afterByType(final RouteDefinition route, final Class<?> type, final ProcessorDefinition<?> after,
                                             boolean selectFirst, boolean selectLast, int selectFrom, int selectTo, int maxDeep) {
        MatchBy matchBy = new MatchByType(type);
        return doAfter(route, matchBy, after, selectFirst, selectLast, selectFrom, selectTo, maxDeep);
    }

    private static AdviceWithTask doAfter(final RouteDefinition route, final MatchBy matchBy, final ProcessorDefinition<?> after,
                                          boolean selectFirst, boolean selectLast, int selectFrom, int selectTo, int maxDeep) {
        return new AdviceWithTask() {
            public void task() throws Exception {
                boolean match = false;
                Iterator<ProcessorDefinition<?>> it = AdviceWithTasks.createMatchByIterator(route, matchBy, selectFirst, selectLast, selectFrom, selectTo, maxDeep);
                while (it.hasNext()) {
                    ProcessorDefinition<?> output = it.next();
                    if (matchBy.match(output)) {
                        List<ProcessorDefinition<?>> outputs = getOutputs(output);
                        if (outputs != null) {
                            int index = outputs.indexOf(output);
                            if (index != -1) {
                                match = true;
                                Object existing = outputs.get(index);
                                outputs.add(index + 1, after);
                                // must set parent on the node we added in the route
                                after.setParent(output.getParent());
                                LOG.info("AdviceWith ({}) : [{}] --> after [{}]", matchBy.getId(), existing, after);
                            }
                        }
                    }
                }

                if (!match) {
                    throw new IllegalArgumentException("There are no outputs which matches: " + matchBy.getId() + " in the route: " + route);
                }
            }
        };
    }

    /**
     * Gets the outputs to use with advice with from the given child/parent
     * <p/>
     * This implementation deals with that outputs can be abstract and retrieves the <i>correct</i> parent output.
     *
     * @param node the node
     * @return <tt>null</tt> if not outputs to be used
     */
    private static List<ProcessorDefinition<?>> getOutputs(ProcessorDefinition<?> node) {
        if (node == null) {
            return null;
        }
        ProcessorDefinition<?> parent = node.getParent();
        if (parent == null) {
            return null;
        }
        // for CBR then use the outputs from the node itself
        // so we work on the right branch in the CBR (when/otherwise)
        if (parent instanceof ChoiceDefinition) {
            return node.getOutputs();
        }
        List<ProcessorDefinition<?>> outputs = parent.getOutputs();
        if (outputs.size() == 1 && outputs.get(0).isAbstract()) {
            // if the output is abstract then get its output, as
            outputs = outputs.get(0).getOutputs();
        }
        return outputs;
    }

    public static AdviceWithTask replaceFromWith(final RouteDefinition route, final String uri) {
        return new AdviceWithTask() {
            public void task() throws Exception {
                FromDefinition from = route.getInputs().get(0);
                LOG.info("AdviceWith replace input from [{}] --> [{}]", from.getUriOrRef(), uri);
                from.setEndpoint(null);
                from.setRef(null);
                from.setUri(uri);
            }
        };
    }

    public static AdviceWithTask replaceFrom(final RouteDefinition route, final Endpoint endpoint) {
        return new AdviceWithTask() {
            public void task() throws Exception {
                FromDefinition from = route.getInputs().get(0);
                LOG.info("AdviceWith replace input from [{}] --> [{}]", from.getUriOrRef(), endpoint.getEndpointUri());
                from.setRef(null);
                from.setUri(null);
                from.setEndpoint(endpoint);
            }
        };
    }

    /**
     * Create iterator which walks the route, and only returns nodes which matches the given set of criteria.
     *
     * @param route        the route
     * @param matchBy      match by which must match
     * @param selectFirst  optional to select only the first
     * @param selectLast   optional to select only the last
     * @param selectFrom   optional to select index/range
     * @param selectTo     optional to select index/range
     * @param maxDeep      maximum levels deep (is unbounded by default)
     *
     * @return the iterator
     */
    private static Iterator<ProcessorDefinition<?>> createMatchByIterator(final RouteDefinition route, final MatchBy matchBy,
                                                               final boolean selectFirst, final boolean selectLast,
                                                               final int selectFrom, final int selectTo, int maxDeep) {

        // first iterator and apply match by
        List<ProcessorDefinition<?>> matched = new ArrayList<ProcessorDefinition<?>>();

        List<ProcessorDefinition<?>> outputs = new ArrayList<>();
        // skip abstract nodes in the beginning as they are cross cutting functionality such as onException, onCompletion etc
        for (ProcessorDefinition output : route.getOutputs()) {
            // special for transacted, which we need to unwrap
            if (output instanceof TransactedDefinition) {
                outputs.addAll(output.getOutputs());
            } else {
                boolean invalid = outputs.isEmpty() && output.isAbstract();
                if (!invalid) {
                    outputs.add(output);
                }
            }
        }

        @SuppressWarnings("rawtypes")
        Iterator<ProcessorDefinition> itAll = ProcessorDefinitionHelper.filterTypeInOutputs(outputs, ProcessorDefinition.class, maxDeep);
        while (itAll.hasNext()) {
            ProcessorDefinition<?> next = itAll.next();

            if (matchBy.match(next)) {
                matched.add(next);
            }
        }

        // and then apply the selector iterator
        return createSelectorIterator(matched, selectFirst, selectLast, selectFrom, selectTo);
    }

    private static Iterator<ProcessorDefinition<?>> createSelectorIterator(final List<ProcessorDefinition<?>> list, final boolean selectFirst,
                                                                           final boolean selectLast, final int selectFrom, final int selectTo) {
        return new Iterator<ProcessorDefinition<?>>() {
            private int current;
            private boolean done;

            @Override
            public boolean hasNext() {
                if (list.isEmpty() || done) {
                    return false;
                }

                if (selectFirst) {
                    done = true;
                    // spool to first
                    current = 0;
                    return true;
                }

                if (selectLast) {
                    done = true;
                    // spool to last
                    current = list.size() - 1;
                    return true;
                }

                if (selectFrom >= 0 && selectTo >= 0) {
                    // check for out of bounds
                    if (selectFrom >= list.size() || selectTo >= list.size()) {
                        return false;
                    }
                    if (current < selectFrom) {
                        // spool to beginning of range
                        current = selectFrom;
                    }
                    return current >= selectFrom && current <= selectTo;
                }

                return current < list.size();
            }

            @Override
            public ProcessorDefinition<?> next() {
                ProcessorDefinition<?> answer = list.get(current);
                current++;
                return answer;
            }

            @Override
            public void remove() {
                // noop
            }
        };
    }

}
