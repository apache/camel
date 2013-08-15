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
package org.apache.camel.ruby;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provide some helper methods for building routes from scripting languages
 * with a minimum amount of noise using state for the current node in the DSL
 *
 * @version 
 */
public abstract class ScriptRouteBuilder extends RouteBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(ScriptRouteBuilder.class);
    protected ProcessorDefinition<?> node;

    public ScriptRouteBuilder() {
    }

    public ScriptRouteBuilder(CamelContext context) {
        super(context);
    }

    @Override
    protected void configureRoute(RouteDefinition route) {
        super.configureRoute(route);
        this.node = route;
    }

    public ProcessorDefinition<?> to(String uri) {
        return getNode().to(uri);
    }
    
    public ProcessorDefinition<?> to(Endpoint endpoint) {
        return getNode().to(endpoint);
    }

    public ProcessorDefinition<?> getNode() {
        if (node == null) {
            throw new IllegalStateException("You must define a route first via the from() method");
        }
        return node;
    }

    public void setNode(ProcessorDefinition<?> node) {
        this.node = node;

        LOG.info("Node is now: {}", node);
    }
}
