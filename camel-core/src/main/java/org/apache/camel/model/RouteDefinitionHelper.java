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

import java.util.List;

/**
 * Helper for {@link RouteDefinition}
 * <p/>
 * Utility methods to help preparing {@link RouteDefinition} before they are added to
 * {@link org.apache.camel.CamelContext}.
 *
 * @version $Revision$
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

}
