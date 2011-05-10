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

import java.util.Iterator;

import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.ProcessorDefinitionHelper;
import org.apache.camel.model.RouteDefinition;
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

        boolean match(ProcessorDefinition processor);
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

        public boolean match(ProcessorDefinition processor) {
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

        public boolean match(ProcessorDefinition processor) {
            return EndpointHelper.matchPattern(processor.toString(), toString);
        }
    }

    /**
     * Will match by the type of the processor.
     */
    private static final class MatchByType implements MatchBy {

        private final Class type;

        private MatchByType(Class<?> type) {
            this.type = type;
        }

        public String getId() {
            return type.getSimpleName();
        }

        public boolean match(ProcessorDefinition processor) {
            return type.isAssignableFrom(processor.getClass());
        }
    }

    public static AdviceWithTask replaceByToString(final RouteDefinition route, final String toString, final ProcessorDefinition replace,
                                                   boolean selectFirst, boolean selectLast, int selectFrom, int selectTo) {
        MatchBy matchBy = new MatchByToString(toString);
        Iterator<ProcessorDefinition> it = AdviceWithTasks.createMatchByIterator(route, matchBy, selectFirst, selectLast, selectFrom, selectTo);
        return doReplace(route, new MatchByToString(toString), replace, it);
    }

    public static AdviceWithTask replaceById(final RouteDefinition route, final String id, final ProcessorDefinition replace,
                                             boolean selectFirst, boolean selectLast, int selectFrom, int selectTo) {
        MatchBy matchBy = new MatchById(id);
        Iterator<ProcessorDefinition> it = AdviceWithTasks.createMatchByIterator(route, matchBy, selectFirst, selectLast, selectFrom, selectTo);
        return doReplace(route, matchBy, replace, it);
    }

    public static AdviceWithTask replaceByType(final RouteDefinition route, final Class type, final ProcessorDefinition replace,
                                               boolean selectFirst, boolean selectLast, int selectFrom, int selectTo) {
        MatchBy matchBy = new MatchByType(type);
        Iterator<ProcessorDefinition> it = AdviceWithTasks.createMatchByIterator(route, matchBy, selectFirst, selectLast, selectFrom, selectTo);
        return doReplace(route, matchBy, replace, it);
    }

    @SuppressWarnings("unchecked")
    private static AdviceWithTask doReplace(final RouteDefinition route, final MatchBy matchBy, final ProcessorDefinition replace,
                                            final Iterator<ProcessorDefinition> it) {
        return new AdviceWithTask() {
            public void task() throws Exception {
                boolean match = false;
                while (it.hasNext()) {
                    ProcessorDefinition output = it.next();
                    if (matchBy.match(output)) {
                        ProcessorDefinition parent = output.getParent();
                        if (parent != null) {
                            int index = parent.getOutputs().indexOf(output);
                            if (index != -1) {
                                match = true;
                                parent.getOutputs().add(index + 1, replace);
                                Object old = parent.getOutputs().remove(index);
                                LOG.info("AdviceWith (" + matchBy.getId() + ") : [" + old + "] --> replace [" + replace + "]");
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
                                                  boolean selectFirst, boolean selectLast, int selectFrom, int selectTo) {
        MatchBy matchBy = new MatchByToString(toString);
        Iterator<ProcessorDefinition> it = AdviceWithTasks.createMatchByIterator(route, matchBy, selectFirst, selectLast, selectFrom, selectTo);
        return doRemove(route, matchBy, it);
    }

    public static AdviceWithTask removeById(final RouteDefinition route, final String id,
                                            boolean selectFirst, boolean selectLast, int selectFrom, int selectTo) {
        MatchBy matchBy = new MatchById(id);
        Iterator<ProcessorDefinition> it = AdviceWithTasks.createMatchByIterator(route, matchBy, selectFirst, selectLast, selectFrom, selectTo);
        return doRemove(route, matchBy, it);
    }

    public static AdviceWithTask removeByType(final RouteDefinition route, final Class type,
                                              boolean selectFirst, boolean selectLast, int selectFrom, int selectTo) {
        MatchBy matchBy = new MatchByType(type);
        Iterator<ProcessorDefinition> it = AdviceWithTasks.createMatchByIterator(route, matchBy, selectFirst, selectLast, selectFrom, selectTo);
        return doRemove(route, matchBy, it);
    }

    private static AdviceWithTask doRemove(final RouteDefinition route, final MatchBy matchBy,
                                           final Iterator<ProcessorDefinition> it) {
        return new AdviceWithTask() {
            public void task() throws Exception {
                boolean match = false;
                while (it.hasNext()) {
                    ProcessorDefinition output = it.next();
                    if (matchBy.match(output)) {
                        ProcessorDefinition parent = output.getParent();
                        if (parent != null) {
                            int index = parent.getOutputs().indexOf(output);
                            if (index != -1) {
                                match = true;
                                Object old = parent.getOutputs().remove(index);
                                LOG.info("AdviceWith (" + matchBy.getId() + ") : [" + old + "] --> remove");
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

    public static AdviceWithTask beforeByToString(final RouteDefinition route, final String toString, final ProcessorDefinition before,
                                                  boolean selectFirst, boolean selectLast, int selectFrom, int selectTo) {
        MatchBy matchBy = new MatchByToString(toString);
        Iterator<ProcessorDefinition> it = AdviceWithTasks.createMatchByIterator(route, matchBy, selectFirst, selectLast, selectFrom, selectTo);
        return doBefore(route, matchBy, before, it);
    }

    public static AdviceWithTask beforeById(final RouteDefinition route, final String id, final ProcessorDefinition before,
                                            boolean selectFirst, boolean selectLast, int selectFrom, int selectTo) {
        MatchBy matchBy = new MatchById(id);
        Iterator<ProcessorDefinition> it = AdviceWithTasks.createMatchByIterator(route, matchBy, selectFirst, selectLast, selectFrom, selectTo);
        return doBefore(route, matchBy, before, it);
    }

    public static AdviceWithTask beforeByType(final RouteDefinition route, final Class type, final ProcessorDefinition before,
                                              boolean selectFirst, boolean selectLast, int selectFrom, int selectTo) {
        MatchBy matchBy = new MatchByType(type);
        Iterator<ProcessorDefinition> it = AdviceWithTasks.createMatchByIterator(route, matchBy, selectFirst, selectLast, selectFrom, selectTo);
        return doBefore(route, matchBy, before, it);
    }

    @SuppressWarnings("unchecked")
    private static AdviceWithTask doBefore(final RouteDefinition route, final MatchBy matchBy, final ProcessorDefinition before,
                                           final Iterator<ProcessorDefinition> it) {
        return new AdviceWithTask() {
            public void task() throws Exception {
                boolean match = false;
                while (it.hasNext()) {
                    ProcessorDefinition output = it.next();
                    if (matchBy.match(output)) {
                        ProcessorDefinition parent = output.getParent();
                        if (parent != null) {
                            int index = parent.getOutputs().indexOf(output);
                            if (index != -1) {
                                match = true;
                                Object existing = parent.getOutputs().get(index);
                                parent.getOutputs().add(index, before);
                                LOG.info("AdviceWith (" + matchBy.getId() + ") : [" + existing + "] --> before [" + before + "]");
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

    public static AdviceWithTask afterByToString(final RouteDefinition route, final String toString, final ProcessorDefinition after,
                                                 boolean selectFirst, boolean selectLast, int selectFrom, int selectTo) {
        MatchBy matchBy = new MatchByToString(toString);
        Iterator<ProcessorDefinition> it = AdviceWithTasks.createMatchByIterator(route, matchBy, selectFirst, selectLast, selectFrom, selectTo);
        return doAfter(route, matchBy, after, it);
    }

    public static AdviceWithTask afterById(final RouteDefinition route, final String id, final ProcessorDefinition after,
                                           boolean selectFirst, boolean selectLast, int selectFrom, int selectTo) {
        MatchBy matchBy = new MatchById(id);
        Iterator<ProcessorDefinition> it = AdviceWithTasks.createMatchByIterator(route, matchBy, selectFirst, selectLast, selectFrom, selectTo);
        return doAfter(route, matchBy, after, it);
    }

    public static AdviceWithTask afterByType(final RouteDefinition route, final Class type, final ProcessorDefinition after,
                                             boolean selectFirst, boolean selectLast, int selectFrom, int selectTo) {
        MatchBy matchBy = new MatchByType(type);
        Iterator<ProcessorDefinition> it = AdviceWithTasks.createMatchByIterator(route, matchBy, selectFirst, selectLast, selectFrom, selectTo);
        return doAfter(route, matchBy, after, it);
    }

    @SuppressWarnings("unchecked")
    private static AdviceWithTask doAfter(final RouteDefinition route, final MatchBy matchBy, final ProcessorDefinition after,
                                          final Iterator<ProcessorDefinition> it) {
        return new AdviceWithTask() {
            public void task() throws Exception {
                boolean match = false;
                while (it.hasNext()) {
                    ProcessorDefinition output = it.next();
                    if (matchBy.match(output)) {

                        ProcessorDefinition parent = output.getParent();
                        if (parent != null) {
                            int index = parent.getOutputs().indexOf(output);
                            if (index != -1) {
                                match = true;
                                Object existing = parent.getOutputs().get(index);
                                parent.getOutputs().add(index + 1, after);
                                LOG.info("AdviceWith (" + matchBy.getId() + ") : [" + existing + "] --> after [" + after + "]");
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
     * Create iterator which walks the route, and only returns nodes which matches the given set of criteria.
     *
     * @param route        the route
     * @param matchBy      match by which must match
     * @param selectFirst  optional to select only the first
     * @param selectLast   optional to select only the last
     * @param selectFrom   optional to select index/range
     * @param selectTo     optional to select index/range
     * 
     * @return the iterator
     */
    private static Iterator<ProcessorDefinition> createMatchByIterator(final RouteDefinition route, final MatchBy matchBy,
                                                               final boolean selectFirst, final boolean selectLast,
                                                               final int selectFrom, final int selectTo) {

        // iterator to walk all nodes
        final Iterator<ProcessorDefinition> itAll = ProcessorDefinitionHelper.filterTypeInOutputs(route.getOutputs(), ProcessorDefinition.class);

        // iterator to only walk nodes which matchBy matches
        final Iterator<ProcessorDefinition> itMatchBy = new Iterator<ProcessorDefinition>() {
            private ProcessorDefinition next;

            @Override
            public boolean hasNext() {
                if (next == null) {
                    // compute next
                    next = next();
                }
                return next != null;
            }

            @Override
            public ProcessorDefinition next() {
                // grab the next if its ready
                if (next != null) {
                    ProcessorDefinition answer = next;
                    next = null;
                    return answer;
                }

                // find the next which matchBy matches
                boolean found = false;
                while (!found && itAll.hasNext()) {
                    ProcessorDefinition def = itAll.next();
                    if (matchBy.match(def)) {
                        found = true;
                        next = def;
                    }
                }

                ProcessorDefinition answer = next;
                next = null;
                return answer;
            }

            @Override
            public void remove() {
            }
        };

        // iterator to only walk which selectXXX matches
        return new Iterator<ProcessorDefinition>() {
            private int current;
            private ProcessorDefinition next;

            @Override
            public boolean hasNext() {
                if (next == null) {
                    // compute next
                    next = next();
                }
                return next != null;
            }

            public ProcessorDefinition next() {
                // grab the next if its ready
                if (next != null) {
                    ProcessorDefinition answer = next;
                    next = null;
                    return answer;
                }

                // a bit complicated logic to ensure selectFirst/selectLast,selectFrom/selectTo
                // filter out unwanted nodes
                // we use the matchBy iterator as the nodes mush at first match this iterator
                // before we can do any selection

                if (selectFrom >= 0 && current <= selectFrom) {
                    // spool until we should start
                    while (current <= selectFrom) {
                        current++;
                        if (itMatchBy.hasNext()) {
                            next = itMatchBy.next();
                        } else {
                            next = null;
                        }
                    }
                } else if (selectTo >= 0 && current <= selectTo) {
                    // are we in range
                    current++;
                    if (itMatchBy.hasNext()) {
                        next = itMatchBy.next();
                    } else {
                        next = null;
                    }
                } else if (selectLast) {
                    // spool until the last matching
                    while (itMatchBy.hasNext()) {
                        current++;
                        next = itMatchBy.next();
                    }
                } else if (selectFirst) {
                    // only match the first
                    current++;
                    if (itMatchBy.hasNext() && current == 1) {
                        next = itMatchBy.next();
                    } else {
                        next = null;
                    }
                } else if (!selectFirst && !selectLast && selectFrom < 0 && selectTo < 0) {
                    // regular without any selectFirst,selectLast,selectFrom/selectTo stuff
                    current++;
                    if (itMatchBy.hasNext()) {
                        next = itMatchBy.next();
                    }
                }

                return next;
            }

            @Override
            public void remove() {
                // noop
            }
        };
    }

}
