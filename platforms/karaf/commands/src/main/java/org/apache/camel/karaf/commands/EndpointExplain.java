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
package org.apache.camel.karaf.commands;

import java.io.PrintStream;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.util.JsonSchemaHelper;
import org.apache.camel.util.URISupport;
import org.apache.felix.gogo.commands.Argument;
import org.apache.felix.gogo.commands.Command;
import org.apache.felix.gogo.commands.Option;

/**
 * Explain the Camel endpoints available in the Karaf instance.
 */
@Command(scope = "camel", name = "endpoint-explain", description = "Explain all Camel endpoints available in CamelContexts.")
public class EndpointExplain extends CamelCommandSupport {

    @Argument(index = 0, name = "name", description = "The Camel context name where to look for the endpoints", required = false, multiValued = false)
    String name;

    @Option(name = "--verbose", aliases = "-v", description = "Verbose output to explain all options",
            required = false, multiValued = false, valueToShowInHelp = "false")
    boolean verbose;

    @Option(name = "--scheme", aliases = "-s", description = "To filter endpoints by scheme",
            required = false, multiValued = true)
    String[] schemes;

    protected Object doExecute() throws Exception {
        List<Endpoint> endpoints = camelController.getEndpoints(name);
        if (endpoints == null || endpoints.isEmpty()) {
            return null;
        }

        // filter endpoints by scheme
        if (schemes != null && schemes.length > 0) {
            Iterator<Endpoint> it = endpoints.iterator();
            while (it.hasNext()) {
                Endpoint endpoint = it.next();
                boolean match = false;
                for (String scheme : schemes) {
                    if (endpoint.getEndpointUri().startsWith(scheme)) {
                        match = true;
                    }
                }
                if (!match) {
                    it.remove();
                }
            }
        }

        final PrintStream out = System.out;

        for (Endpoint endpoint : endpoints) {
            String json = camelController.explainEndpoint(endpoint.getCamelContext().getName(), endpoint.getEndpointUri(), verbose);

            out.println("Context:\t" + endpoint.getCamelContext().getName());

            // sanitize and mask uri so we dont see passwords
            String uri = URISupport.sanitizeUri(endpoint.getEndpointUri());
            String header = "Uri:            " + uri;
            out.println(header);
            for (int i = 0; i < header.length(); i++) {
                out.print('-');
            }
            out.println();

            // use a basic json parser
            List<Map<String, String>> options = JsonSchemaHelper.parseJsonSchema(json);

            // lets sort the options by name
            Collections.sort(options, new Comparator<Map<String, String>>() {
                @Override
                public int compare(Map<String, String> o1, Map<String, String> o2) {
                    return o1.get("name").compareTo(o2.get("name"));
                }
            });

            for (Map<String, String> option : options) {
                out.print("Option:\t\t");
                out.println(option.get("name"));
                String type = option.get("type");
                if (type != null) {
                    out.print("Type:\t\t");
                    out.println(type);
                }
                String javaType = option.get("javaType");
                if (javaType != null) {
                    out.print("Java Type:\t");
                    out.println(javaType);
                }
                String value = option.get("value");
                if (value != null) {
                    out.print("Value:\t\t");
                    out.println(value);
                }
                String description = option.get("description");
                if (description != null) {
                    out.print("Description:\t");
                    out.println(description);
                }
                out.println();
            }

            if (options.isEmpty()) {
                out.println();
            }
        }

        return null;
    }

}
