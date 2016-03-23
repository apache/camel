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
package org.apache.camel.commands;

import java.io.PrintStream;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.camel.util.EndpointHelper;
import org.apache.camel.util.JsonSchemaHelper;
import org.apache.camel.util.URISupport;

/**
 * Explain the Camel endpoints available in the JVM.
 */
public class EndpointExplainCommand extends AbstractContextCommand {

    private boolean verbose;
    private String filter;

    public EndpointExplainCommand(String name, boolean verbose, String filter) {
        super(name);
        this.verbose = verbose;
        this.filter = filter;
    }

    @Override
    protected Object performContextCommand(CamelController camelController, String contextName, PrintStream out, PrintStream err) throws Exception {
        List<Map<String, String>> endpoints = camelController.getEndpoints(contextName);
        if (endpoints == null || endpoints.isEmpty()) {
            return null;
        }

        // filter endpoints
        if (filter != null) {
            Iterator<Map<String, String>> it = endpoints.iterator();
            while (it.hasNext()) {
                Map<String, String> row = it.next();
                if (!EndpointHelper.matchPattern(row.get("uri"), filter)) {
                    // did not match
                    it.remove();
                }
            }
        }

        for (Map<String, String> row : endpoints) {
            String json = camelController.explainEndpointAsJSon(context, row.get("uri"), verbose);
            if (json == null) {
                continue;
            }

            out.println("Context:       " + context);

            // sanitize and mask uri so we dont see passwords
            String uri = URISupport.sanitizeUri(row.get("uri"));
            String header = "Uri:           " + uri;
            out.println(header);
            for (int i = 0; i < header.length(); i++) {
                out.print('-');
            }
            out.println();

            // use a basic json parser
            List<Map<String, String>> options = JsonSchemaHelper.parseJsonSchema("properties", json, true);

            for (Map<String, String> option : options) {
                out.print("Option:        ");
                out.println(option.get("name"));
                String kind = option.get("kind");
                if (kind != null) {
                    out.print("Kind:          ");
                    out.println(kind);
                }
                String group = option.get("group");
                if (group != null) {
                    out.print("Group:         ");
                    out.println(group);
                }
                String type = option.get("type");
                if (type != null) {
                    out.print("Type:          ");
                    out.println(type);
                }
                String javaType = option.get("javaType");
                if (javaType != null) {
                    out.print("Java Type:     ");
                    out.println(javaType);
                }
                String value = option.get("value");
                if (value != null) {
                    out.print("Value:         ");
                    out.println(value);
                }
                String defaultValue = option.get("defaultValue");
                if (defaultValue != null) {
                    out.print("Default Value: ");
                    out.println(defaultValue);
                }
                String description = option.get("description");
                if (description != null) {
                    out.print("Description:   ");
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
