/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.ruby;

import org.apache.camel.Endpoint;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.ProcessorType;
import org.apache.camel.model.RouteType;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Provide some helper methods for building routes from scripting languages
 * with a minimum amount of noise using state for the current node in the DSL
 *
 * @version $Revision: 1.1 $
 */
public abstract class ScriptRouteBuilder extends RouteBuilder {
    private static final transient Log LOG = LogFactory.getLog(ScriptRouteBuilder.class);
    protected ProcessorType node;

    public ScriptRouteBuilder() {
    }

    public ScriptRouteBuilder(CamelContext context) {
        super(context);
    }

    @Override
    protected void configureRoute(RouteType route) {
        super.configureRoute(route);
        this.node = route;
    }

    public ProcessorType to(String uri) {
        return getNode().to(uri);
    }
    
    public ProcessorType to(Endpoint endpoint) {
        return getNode().to(endpoint);
    }

    public ProcessorType getNode() {
        if (node == null) {
            throw new IllegalStateException("You must define a route first via the from() method");
        }
        return node;
    }

    public void setNode(ProcessorType node) {
        this.node = node;

        LOG.info("Node is now: " + node);
    }
}
