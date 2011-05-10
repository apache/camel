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

import org.apache.camel.model.PipelineDefinition;
import org.apache.camel.model.ProcessorDefinition;

/**
 * A builder when using the <a href="http://camel.apache.org/advicewith.html">advice with</a> feature.
 */
public class AdviceWithBuilder<T extends ProcessorDefinition> {

    private final AdviceWithRouteBuilder builder;
    private final String id;
    private final String toString;
    private final Class<T> type;

    public AdviceWithBuilder(AdviceWithRouteBuilder builder, String id, String toString, Class<T> type) {
        this.builder = builder;
        this.id = id;
        this.toString = toString;
        this.type = type;

        if (id == null && toString == null && type == null) {
            throw new IllegalArgumentException("Either id, toString or type must be specified");
        }
    }

    /**
     * Replaces the matched node(s) with the following nodes.
     *
     * @return the builder to build the nodes.
     */
    public ProcessorDefinition replace() {
        PipelineDefinition answer = new PipelineDefinition();
        if (id != null) {
            builder.getAdviceWithTasks().add(AdviceWithTasks.replaceById(builder.getOriginalRoute(), id, answer));
        } else if (toString != null) {
            builder.getAdviceWithTasks().add(AdviceWithTasks.replaceByToString(builder.getOriginalRoute(), toString, answer));
        } else if (type != null) {
            builder.getAdviceWithTasks().add(AdviceWithTasks.replaceByType(builder.getOriginalRoute(), type, answer));
        }
        return answer;
    }

    /**
     * Removes the matched node(s)
     */
    public void remove() {
        if (id != null) {
            builder.getAdviceWithTasks().add(AdviceWithTasks.removeById(builder.getOriginalRoute(), id));
        } else if (toString != null) {
            builder.getAdviceWithTasks().add(AdviceWithTasks.removeByToString(builder.getOriginalRoute(), toString));
        } else if (type != null) {
            builder.getAdviceWithTasks().add(AdviceWithTasks.removeByType(builder.getOriginalRoute(), type));
        }
    }

    /**
     * Insert the following node(s) <b>before</b> the matched node(s)
     *
     * @return the builder to build the nodes.
     */
    public ProcessorDefinition before() {
        PipelineDefinition answer = new PipelineDefinition();
        if (id != null) {
            builder.getAdviceWithTasks().add(AdviceWithTasks.beforeById(builder.getOriginalRoute(), id, answer));
        } else if (toString != null) {
            builder.getAdviceWithTasks().add(AdviceWithTasks.beforeByToString(builder.getOriginalRoute(), toString, answer));
        } else if (type != null) {
            builder.getAdviceWithTasks().add(AdviceWithTasks.beforeByType(builder.getOriginalRoute(), type, answer));
        }
        return answer;
    }

    /**
     * Insert the following node(s) <b>after</b> the matched node(s)
     *
     * @return the builder to build the nodes.
     */
    public ProcessorDefinition after() {
        PipelineDefinition answer = new PipelineDefinition();
        if (id != null) {
            builder.getAdviceWithTasks().add(AdviceWithTasks.afterById(builder.getOriginalRoute(), id, answer));
        } else if (toString != null) {
            builder.getAdviceWithTasks().add(AdviceWithTasks.afterByToString(builder.getOriginalRoute(), toString, answer));
        } else if (type != null) {
            builder.getAdviceWithTasks().add(AdviceWithTasks.afterByType(builder.getOriginalRoute(), type, answer));
        }
        return answer;
    }

}
