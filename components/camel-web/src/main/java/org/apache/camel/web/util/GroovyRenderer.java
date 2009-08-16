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
package org.apache.camel.web.util;

import java.util.List;

import org.apache.camel.builder.DeadLetterChannelBuilder;
import org.apache.camel.builder.ErrorHandlerBuilderRef;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.SendDefinition;

/**
 * Render routes in Groovy language
 */
public final class GroovyRenderer {

    public static final String HEADER = "import org.apache.camel.language.groovy.GroovyRouteBuilder;\nclass GroovyRoute extends GroovyRouteBuilder {\nvoid configure() {\n";
    public static final String FOOTER = "\n}\n}";

    private GroovyRenderer() {
        // Utility class, no public or protected default constructor
    }

    /**
     * render a RouteDefinition
     */
    public static void renderRoute(StringBuilder buffer, RouteDefinition route) {
        List<FromDefinition> inputs = route.getInputs();
        List<ProcessorDefinition> outputs = route.getOutputs();

        // render the error handler
        if (!(route.getErrorHandlerBuilder() instanceof ErrorHandlerBuilderRef)) {
            if (route.getErrorHandlerBuilder() instanceof DeadLetterChannelBuilder) {
                DeadLetterChannelBuilder deadLetter = (DeadLetterChannelBuilder)route.getErrorHandlerBuilder();
                buffer.append("errorHandler(deadLetterChannel(\"").append(deadLetter.getDeadLetterUri()).append("\")");
                buffer.append(".maximumRedeliveries(").append(deadLetter.getRedeliveryPolicy().getMaximumRedeliveries()).append(")");
                buffer.append(".redeliverDelay(").append(deadLetter.getRedeliveryPolicy().getRedeliverDelay()).append(")");
                buffer.append(".handled(").append(deadLetter.getHandledPolicy().toString()).append(")");
                buffer.append(");");
            }
        }

        // render the global dsl not started with from, like global
        // intercept, interceptFrom,interceptSendToEndpoint, onCompletion,
        // onException
        for (ProcessorDefinition processor : outputs) {
            if (processor.getParent() == null && !(processor instanceof SendDefinition)) {
                ProcessorDefinitionRenderer.render(buffer, processor);
                buffer.append(";");
            }
        }

        // render the inputs of the router
        buffer.append("from(");
        for (FromDefinition input : inputs) {
            buffer.append("\"").append(input.getUri()).append("\"");
            if (input != inputs.get(inputs.size() - 1)) {
                buffer.append(", ");
            }
        }
        buffer.append(")");

        // render some route configurations
        if (route.isTrace() != null) {
            if (route.isTrace()) {
                buffer.append(".tracing()");
            } else {
                buffer.append(".noTracing()");
            }
        }
        if (route.isStreamCache() != null && route.isStreamCache()) {
            buffer.append(".streamCaching()");
        }

        // render the outputs of the router
        for (ProcessorDefinition processor : outputs) {
            if (processor.getParent() == route || processor instanceof SendDefinition) {
                ProcessorDefinitionRenderer.render(buffer, processor);
            }
        }
    }

    /**
     * render a set of RouteDefinition
     */
    public static void renderRoutes(StringBuilder buffer, List<RouteDefinition> routes) {
        for (RouteDefinition route : routes) {
            renderRoute(buffer, route);
            if (route != routes.get(routes.size() - 1)) {
                buffer.append(";");
            }
        }
    }
}
