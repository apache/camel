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
package org.apache.camel.springboot.commands.crsh;

import java.util.LinkedList;

import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.commands.LocalCamelController;
import org.crsh.cli.descriptor.ParameterDescriptor;
import org.crsh.cli.spi.Completer;
import org.crsh.cli.spi.Completion;

public class CamelCompleter implements Completer {

    private LocalCamelController camelController = CamelCommandsPlugin.getInstance().getCamelCommandsFacade().getCamelController();

    public Completion complete(ParameterDescriptor parameterDescriptor, String prefix) throws Exception {

        LinkedList<String> values = new LinkedList<String>();
        Completion.Builder builder = new Completion.Builder(prefix);

        if (parameterDescriptor.getAnnotation() instanceof ArgumentCamelContext) {
            values.addAll(getContextNames());
        }

        if (parameterDescriptor.getAnnotation() instanceof ArgumentRouteID) {
            values.addAll(getRouteIds());
        }

        for (String value : values) {
            if (value.startsWith(prefix)) {
                builder.add(value.substring(prefix.length()), true);
            }
        }

        return builder.build();
    }

    private LinkedList<String> getContextNames() throws Exception {
        LinkedList<String> values = new LinkedList<String>();
        for (CamelContext camelContext : camelController.getLocalCamelContexts()) {
            values.add(camelContext.getName());
        }
        return values;
    }

    private LinkedList<String> getRouteIds() throws Exception {
        LinkedList<String> values = new LinkedList<String>();
        for (CamelContext camelContext : camelController.getLocalCamelContexts()) {
            for (Route route : camelContext.getRoutes()) {
                values.add(route.getId());
            }
        }
        return values;
    }
}
