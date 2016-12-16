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
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.apache.camel.util.JsonSchemaHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;

/**
 * List the Camel endpoints available in the JVM.
 */
public class EndpointListCommand extends AbstractCamelCommand {

    private static final String URI_COLUMN_LABEL = "Uri";
    private static final String STATUS_COLUMN_LABEL = "Status";
    private static final String CONTEXT_NAME_COLUMN_LABEL = "Context";

    private static final int DEFAULT_COLUMN_WIDTH_INCREMENT = 0;
    private static final String DEFAULT_FIELD_PREAMBLE = " ";
    private static final String DEFAULT_FIELD_POSTAMBLE = " ";
    private static final String DEFAULT_HEADER_PREAMBLE = " ";
    private static final String DEFAULT_HEADER_POSTAMBLE = " ";
    private static final int DEFAULT_FORMAT_BUFFER_LENGTH = 24;
    // endpoint uris can be very long so clip by default after 120 chars
    private static final int MAX_COLUMN_WIDTH = 120;
    private static final int MIN_COLUMN_WIDTH = 12;

    boolean decode = true;
    boolean verbose;
    boolean explain;
    private final String context;

    public EndpointListCommand(String context, boolean decode, boolean verbose, boolean explain) {
        this.decode = decode;
        this.verbose = verbose;
        this.explain = explain;
        this.context = context;
    }

    @Override
    public Object execute(CamelController camelController, PrintStream out, PrintStream err) throws Exception {
        final List<Map<String, String>> camelContextInfos = camelController.getCamelContexts(this.context);
        final Map<String, List<Map<String, String>>> contextsToEndpoints = new HashMap<>();
        
        for (Map<String, String> camelContextInfo : camelContextInfos) {
            String camelContextName = camelContextInfo.get("name");
            final List<Map<String, String>> endpoints = camelController.getEndpoints(camelContextName);
            if (endpoints.isEmpty()) {
                continue;
            }
            contextsToEndpoints.put(camelContextName, endpoints);
        }

        final Map<String, Integer> columnWidths = computeColumnWidths(contextsToEndpoints);
        final String headerFormat = buildFormatString(columnWidths, true);
        final String rowFormat = buildFormatString(columnWidths, false);

        for (Map.Entry<String, List<Map<String, String>>> stringListEntry : contextsToEndpoints.entrySet()) {
            final String camelContextName = stringListEntry.getKey();
            final List<Map<String, String>> endpoints = stringListEntry.getValue();

            out.println(String.format(headerFormat, CONTEXT_NAME_COLUMN_LABEL, URI_COLUMN_LABEL, STATUS_COLUMN_LABEL));
            out.println(String.format(headerFormat, "-------", "---", "------"));
            for (Map<String, String> row : endpoints) {
                String uri = row.get("uri");
                if (decode) {
                    // decode uri so its more human readable
                    uri = URLDecoder.decode(uri, "UTF-8");
                }
                // sanitize and mask uri so we dont see passwords
                uri = URISupport.sanitizeUri(uri);
                String state = row.get("state");
                out.println(String.format(rowFormat, camelContextName, uri, state));

                if (explain) {
                    boolean first = true;
                    String json = camelController.explainEndpointAsJSon(camelContextName, row.get("uri"), verbose);
                    // use a basic json parser
                    List<Map<String, String>> options = JsonSchemaHelper.parseJsonSchema("properties", json, true);

                    for (Map<String, String> option : options) {
                        String key = option.get("name");
                        String kind = option.get("kind");
                        String type = option.get("type");
                        String javaType = option.get("javaType");
                        String value = option.get("value");
                        if (ObjectHelper.isEmpty(value)) {
                            value = option.get("defaultValue");
                        }
                        String desc = option.get("description");
                        if (key != null && value != null) {
                            if (first) {
                                out.println();
                                first = false;
                            }
                            String line;
                            if ("path".equals(kind)) {
                                line = "\t" + key + " (endpoint path) = " + value;
                            } else {
                                line = "\t" + key + " = " + value;
                            }
                            out.println(line);

                            if (type != null) {
                                String displayType = type;
                                if (javaType != null && !displayType.equals(javaType)) {
                                    displayType = type + " (" + javaType + ")";
                                }
                                out.println("\t" + displayType);
                            }
                            if (desc != null) {
                                out.println("\t" + desc);
                            }
                            out.println();
                        }
                    }
                }
            }
        }
        return null;
    }

    private Map<String, Integer> computeColumnWidths(final Map<String, List<Map<String, String>>> contextsToEndpoints) throws Exception {
        int maxUriLen = 0;
        int maxStatusLen = 0;
        int maxCamelContextLen = 0;

        for (Map.Entry<String, List<Map<String, String>>> stringListEntry : contextsToEndpoints.entrySet()) {
            final String camelContextName = stringListEntry.getKey();

            maxCamelContextLen = java.lang.Math.max(maxCamelContextLen, camelContextName.length());
            
            final List<Map<String, String>> endpoints = stringListEntry.getValue();


            for (Map<String, String> row : endpoints) {
                String uri = row.get("uri");
                if (decode) {
                    // decode uri so its more human readable
                    uri = URLDecoder.decode(uri, "UTF-8");
                }
                // sanitize and mask uri so we dont see passwords
                uri = URISupport.sanitizeUri(uri);

                maxUriLen = java.lang.Math.max(maxUriLen, uri == null ? 0 : uri.length());

                final String status = row.get("state");
                maxStatusLen = java.lang.Math.max(maxStatusLen, status == null ? 0 : status.length());
            }
        }
    
        final Map<String, Integer> retval = new Hashtable<>();
        retval.put(URI_COLUMN_LABEL, maxUriLen);
        retval.put(STATUS_COLUMN_LABEL, maxStatusLen);
        retval.put(CONTEXT_NAME_COLUMN_LABEL, maxCamelContextLen);

        return retval;
    }

    private String buildFormatString(final Map<String, Integer> columnWidths, final boolean isHeader) {
        final String fieldPreamble;
        final String fieldPostamble;
        final int columnWidthIncrement;

        if (isHeader) {
            fieldPreamble = DEFAULT_HEADER_PREAMBLE;
            fieldPostamble = DEFAULT_HEADER_POSTAMBLE;
        } else {
            fieldPreamble = DEFAULT_FIELD_PREAMBLE;
            fieldPostamble = DEFAULT_FIELD_POSTAMBLE;
        }
        columnWidthIncrement = DEFAULT_COLUMN_WIDTH_INCREMENT;

        int uriLen = java.lang.Math.min(columnWidths.get(URI_COLUMN_LABEL) + columnWidthIncrement, getMaxColumnWidth());
        uriLen = Math.max(MIN_COLUMN_WIDTH, uriLen);

        int ctxLen = java.lang.Math.min(columnWidths.get(CONTEXT_NAME_COLUMN_LABEL) + columnWidthIncrement, getMaxColumnWidth());
        ctxLen = Math.max(MIN_COLUMN_WIDTH, ctxLen);
        // last row does not have min width

        final StringBuilder retval = new StringBuilder(DEFAULT_FORMAT_BUFFER_LENGTH);
        retval.append(fieldPreamble).append("%-").append(ctxLen).append('.').append(ctxLen).append('s').append(fieldPostamble).append(' ');
        retval.append(fieldPreamble).append("%-").append(uriLen).append('.').append(uriLen).append('s').append(fieldPostamble).append(' ');
        retval.append(fieldPreamble).append("%s").append(fieldPostamble).append(' ');

        return retval.toString();
    }

    private int getMaxColumnWidth() {
        if (verbose) {
            return Integer.MAX_VALUE;
        } else {
            return MAX_COLUMN_WIDTH;
        }
    }
}
