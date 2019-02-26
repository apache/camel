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
package org.apache.camel.management.mbean;

import java.util.Iterator;

import org.apache.camel.CamelContext;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.ProcessorDefinitionHelper;
import org.apache.camel.model.RouteDefinition;

/**
 * A number of helper methods
 */
public final class ModelCamelContextHelper {

    /**
     * Utility classes should not have a public constructor.
     */
    private ModelCamelContextHelper() {
    }

    /**
     * Checks if any of the Camel routes is using an EIP with the given name
     *
     * @param camelContext  the Camel context
     * @param name          the name of the EIP
     * @return <tt>true</tt> if in use, <tt>false</tt> if not
     */
    public static boolean isEipInUse(CamelContext camelContext, String name) {
        for (RouteDefinition route : camelContext.adapt(ModelCamelContext.class).getRouteDefinitions()) {
            for (FromDefinition from : route.getInputs()) {
                if (name.equals(from.getShortName())) {
                    return true;
                }
            }
            Iterator<ProcessorDefinition> it = ProcessorDefinitionHelper.filterTypeInOutputs(route.getOutputs(), ProcessorDefinition.class);
            while (it.hasNext()) {
                ProcessorDefinition def = it.next();
                if (name.equals(def.getShortName())) {
                    return true;
                }
            }
        }
        return false;
    }

}
