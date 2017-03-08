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
import java.util.Map;
import java.util.regex.PatternSyntaxException;

import org.apache.camel.catalog.CatalogHelper;
import org.apache.camel.commands.internal.RegexUtil;

/**
 * Shows properties of a component from Catalog
 */
@Deprecated
public class CatalogComponentInfoCommand extends AbstractCamelCommand {
    private static final String[][] COMPONENT_PROPERTIES = new String[][] {
        {"Property", "Description"},
        {"key", "description"}
    };

    private static final String[][] COMPONENT_PROPERTIES_VERBOSE = new String[][] {
        {"Property", "Description"},
        {"key", "description"}
    };

    private static final String[][] PROPERTIES = new String[][] {
        {"Property", "Description"},
        {"key", "description"}
    };

    private static final String[][] PROPERTIES_VERBOSE = new String[][] {
        {"Property", "Group", "Default Value", "Description"},
        {"key", "group", "defaultValue", "description"}
    };

    private final String name;
    private final boolean verbose;
    private final String labelFilter;

    public CatalogComponentInfoCommand(String name, boolean verbose, String labelFilter) {
        this.name = name;
        this.verbose = verbose;
        this.labelFilter = labelFilter != null ? RegexUtil.wildcardAsRegex(labelFilter) : null;
    }

    @Override
    public Object execute(CamelController camelController, PrintStream out, PrintStream err) throws Exception {
        final Map<String, Object> info = camelController.componentInfo(name);

        String component = name.toUpperCase();
        if (info.containsKey("description")) {
            component += " :: " +  info.get("description");
        }

        out.println("");
        out.println(component);
        out.println(buildSeparatorString(component.length(), '-'));
        out.println("");


        if (info.containsKey("label")) {
            out.printf("label: %s\n", info.get("label"));
        }

        if (info.containsKey("groupId") && info.containsKey("artifactId") && info.containsKey("version")) {
            out.printf("maven: %s\n", info.get("groupId") + "/" + info.get("artifactId") + "/" + info.get("version"));
        }

        dumpProperties("componentProperties", info, verbose ? COMPONENT_PROPERTIES_VERBOSE : COMPONENT_PROPERTIES, out);
        dumpProperties("properties", info, verbose ? PROPERTIES_VERBOSE : PROPERTIES, out);

        return null;
    }

    private void dumpProperties(String groupName, Map<String, Object> info, String[][] tableStruct, PrintStream out) {
        Map<String, Object> group = (Map<String, Object>)info.get(groupName);
        if (group != null) {
            CatalogComponentHelper.Table table = new CatalogComponentHelper.Table(tableStruct[0], tableStruct[1]);
            for (Map.Entry<String, Object> entry : group.entrySet()) {
                if (entry.getValue() instanceof Map) {
                    Map<String, Object> data = (Map<String, Object>) entry.getValue();
                    if (matchLabel(data)) {
                        table.addRow(entry.getKey(), data);
                    }
                }
            }

            if (!table.isEmpty()) {
                out.println("");
                out.println(groupName);
                out.println("");

                table.print(out);
            }
        }
    }

    private static String buildSeparatorString(int len, char pad) {
        StringBuilder sb = new StringBuilder(len);
        while (sb.length() < len) {
            sb.append(pad);
        }

        return sb.toString();
    }

    private boolean matchLabel(Map<String, Object> properties) {
        if (labelFilter == null) {
            return true;
        }

        final String label = (String)properties.get("label");
        if (label != null) {
            String[] parts = label.split(",");
            for (String part : parts) {
                try {
                    if (part.equalsIgnoreCase(labelFilter) || CatalogHelper.matchWildcard(part, labelFilter) || part.matches(labelFilter)) {
                        return true;
                    }
                } catch (PatternSyntaxException e) {
                    // ignore as filter is maybe not a pattern
                }
            }
        }

        return false;
    }
}
