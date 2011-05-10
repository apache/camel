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


    public static AdviceWithTask replaceByToString(final RouteDefinition route, final String toString, final ProcessorDefinition replace) {
        return doReplace(route, new MatchByToString(toString), replace);
    }

    public static AdviceWithTask replaceById(final RouteDefinition route, final String id, final ProcessorDefinition replace) {
        return doReplace(route, new MatchById(id), replace);
    }

    public static AdviceWithTask replaceByType(final RouteDefinition route, final Class type, final ProcessorDefinition replace) {
        return doReplace(route, new MatchByType(type), replace);
    }

    @SuppressWarnings("unchecked")
    private static AdviceWithTask doReplace(final RouteDefinition route, final MatchBy matchBy, final ProcessorDefinition replace) {
        return new AdviceWithTask() {
            public void task() throws Exception {
                boolean match = false;
                Iterator<ProcessorDefinition> it = ProcessorDefinitionHelper.filterTypeInOutputs(route.getOutputs(), ProcessorDefinition.class);
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

    public static AdviceWithTask removeByToString(final RouteDefinition route, final String toString) {
        return doRemove(route, new MatchByToString(toString));
    }

    public static AdviceWithTask removeById(final RouteDefinition route, final String id) {
        return doRemove(route, new MatchById(id));
    }

    public static AdviceWithTask removeByType(final RouteDefinition route, final Class type) {
        return doRemove(route, new MatchByType(type));
    }

    private static AdviceWithTask doRemove(final RouteDefinition route, final MatchBy matchBy) {
        return new AdviceWithTask() {
            public void task() throws Exception {
                boolean match = false;
                Iterator<ProcessorDefinition> it = ProcessorDefinitionHelper.filterTypeInOutputs(route.getOutputs(), ProcessorDefinition.class);
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

    public static AdviceWithTask beforeByToString(final RouteDefinition route, final String toString, final ProcessorDefinition before) {
        return doBefore(route, new MatchByToString(toString), before);
    }

    public static AdviceWithTask beforeById(final RouteDefinition route, final String id, final ProcessorDefinition before) {
        return doBefore(route, new MatchById(id), before);
    }

    public static AdviceWithTask beforeByType(final RouteDefinition route, final Class type, final ProcessorDefinition before) {
        return doBefore(route, new MatchByType(type), before);
    }

    @SuppressWarnings("unchecked")
    private static AdviceWithTask doBefore(final RouteDefinition route, final MatchBy matchBy, final ProcessorDefinition before) {
        return new AdviceWithTask() {
            public void task() throws Exception {
                boolean match = false;
                Iterator<ProcessorDefinition> it = ProcessorDefinitionHelper.filterTypeInOutputs(route.getOutputs(), ProcessorDefinition.class);
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

    public static AdviceWithTask afterByToString(final RouteDefinition route, final String toString, final ProcessorDefinition after) {
        return doAfter(route, new MatchByToString(toString), after);
    }

    public static AdviceWithTask afterById(final RouteDefinition route, final String id, final ProcessorDefinition after) {
        return doAfter(route, new MatchById(id), after);
    }

    public static AdviceWithTask afterByType(final RouteDefinition route, final Class type, final ProcessorDefinition after) {
        return doAfter(route, new MatchByType(type), after);
    }

    @SuppressWarnings("unchecked")
    private static AdviceWithTask doAfter(final RouteDefinition route, final MatchBy matchBy, final ProcessorDefinition after) {
        return new AdviceWithTask() {
            public void task() throws Exception {
                boolean match = false;
                Iterator<ProcessorDefinition> it = ProcessorDefinitionHelper.filterTypeInOutputs(route.getOutputs(), ProcessorDefinition.class);
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

}
