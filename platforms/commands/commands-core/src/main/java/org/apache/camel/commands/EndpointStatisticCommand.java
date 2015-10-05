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
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.util.URISupport;

/**
 * Display endpoint runtime statistics for a CamelContext
 */
public class EndpointStatisticCommand extends AbstractCamelCommand {

    private static final String CONTEXT_COLUMN_LABEL = "Context";
    private static final String URI_COLUMN_LABEL = "Uri";
    private static final String ROUTE_COLUMN_LABEL = "Route Id";
    private static final String DIRECTION_COLUMN_LABEL = "Direction";
    private static final String STATIC_COLUMN_LABEL = "Static";
    private static final String DYNAMIC_COLUMN_LABEL = "Dynamic";
    private static final String HITS_COLUMN_LABEL = "Total #";

    private static final int DEFAULT_COLUMN_WIDTH_INCREMENT = 0;
    private static final String DEFAULT_FIELD_PREAMBLE = " ";
    private static final String DEFAULT_FIELD_POSTAMBLE = " ";
    private static final String DEFAULT_HEADER_PREAMBLE = " ";
    private static final String DEFAULT_HEADER_POSTAMBLE = " ";
    private static final int DEFAULT_FORMAT_BUFFER_LENGTH = 24;
    // endpoint uris can be very long so clip by default after 120 chars
    private static final int MAX_COLUMN_WIDTH = 120;
    private static final int MIN_COLUMN_WIDTH = 12;

    String context;
    boolean decode = true;
    private String[] filter;

    public EndpointStatisticCommand(String context, boolean decode, String[] filter) {
        this.context = context;
        this.decode = decode;
        this.filter = filter;
    }

    @Override
    public Object execute(CamelController camelController, PrintStream out, PrintStream err) throws Exception {
        List<Map<String, String>> contexts = camelController.getCamelContexts(context);

        boolean header = false;

        Map<String, List<Map<String, String>>> allEndpoints = new LinkedHashMap<>();

        for (Map<String, String> context : contexts) {
            String contextName = context.get("name");
            List<Map<String, String>> endpoints = camelController.getEndpointRuntimeStatistics(contextName);
            allEndpoints.put(contextName, endpoints);
        }

        final Map<String, Integer> columnWidths = computeColumnWidths(allEndpoints);
        final String headerFormat = buildFormatString(columnWidths, true);
        final String rowFormat = buildFormatString(columnWidths, false);

        for (Map.Entry<String, List<Map<String, String>>> entry : allEndpoints.entrySet()) {
            String contextName = entry.getKey();
            for (Map<String, String> row : entry.getValue()) {

                if (!header) {
                    out.println(String.format(headerFormat, CONTEXT_COLUMN_LABEL, URI_COLUMN_LABEL,
                                              ROUTE_COLUMN_LABEL, DIRECTION_COLUMN_LABEL, STATIC_COLUMN_LABEL,
                                              DYNAMIC_COLUMN_LABEL, HITS_COLUMN_LABEL));
                    out.println(String.format(headerFormat, "-------", "---", "--------",
                                              "---------", "------", "-------", "-------"));
                    header = true;
                }

                String uri = row.get("uri");
                if (decode) {
                    // decode uri so its more human readable
                    uri = URLDecoder.decode(uri, "UTF-8");
                }
                // sanitize and mask uri so we dont see passwords
                uri = URISupport.sanitizeUri(uri);
                String routeId = row.get("routeId");
                String direction = row.get("direction");
                String isStatic = row.get("static");
                String isDynamic = row.get("dynamic");
                String hits = row.get("hits");

                // should we filter
                if (isValidRow(direction, isStatic, isDynamic)) {
                    out.println(String.format(rowFormat, contextName, uri, routeId, direction, isStatic, isDynamic, hits));
                }
            }
        }

        return null;
    }

    protected boolean isValidRow(String direction, String isStatic, String isDynamic) {
        if (filter == null || filter.length == 0) {
            return true;
        }

        boolean answer = false;
        for (String f : filter) {
            if ("in".equals(f)) {
                answer = "in".equals(direction);
            } else if ("out".equals(f)) {
                answer = "out".equals(direction);
            } else if ("static".equals(f)) {
                answer = "true".equals(isStatic);
            } else if ("dynamic".equals(f)) {
                answer = "true".equals(isDynamic);
            }
            // all filters must apply to accept when multi valued
            if (!answer) {
                return false;
            }
        }
        return answer;
    }

    private Map<String, Integer> computeColumnWidths(Map<String, List<Map<String, String>>> allEndpoints) throws Exception {
        if (allEndpoints == null) {
            throw new IllegalArgumentException("Unable to determine column widths from null Iterable<Endpoint>");
        } else {
            int maxContextLen = 0;
            int maxUriLen = 0;
            int maxRouteIdLen = 0;
            int maxDirectionLen = 0;
            int maxStaticLen = 0;
            int maxDynamicLen = 0;
            int maxHitsLen = 0;

            for (Map.Entry<String, List<Map<String, String>>> entry : allEndpoints.entrySet()) {
                String contextName = entry.getKey();
                for (Map<String, String> row : entry.getValue()) {
                    maxContextLen = Math.max(maxContextLen, contextName == null ? 0 : contextName.length());

                    String uri = row.get("uri");
                    if (decode) {
                        // decode uri so its more human readable
                        uri = URLDecoder.decode(uri, "UTF-8");
                    }
                    // sanitize and mask uri so we dont see passwords
                    uri = URISupport.sanitizeUri(uri);

                    maxUriLen = Math.max(maxUriLen, uri == null ? 0 : uri.length());

                    final String routeId = row.get("routeId");
                    maxRouteIdLen = Math.max(maxRouteIdLen, routeId == null ? 0 : routeId.length());

                    final String direction = row.get("direction");
                    maxDirectionLen = Math.max(maxDirectionLen, direction == null ? 0 : direction.length());

                    final String isStatic = row.get("static");
                    maxStaticLen = Math.max(maxStaticLen, isStatic == null ? 0 : isStatic.length());

                    final String isDynamic = row.get("dynamic");
                    maxDynamicLen = Math.max(maxDynamicLen, isDynamic == null ? 0 : isDynamic.length());

                    final String hits = row.get("hits");
                    maxHitsLen = Math.max(maxHitsLen, hits == null ? 0 : hits.length());
                }
            }

            final Map<String, Integer> retval = new Hashtable<String, Integer>();
            retval.put(CONTEXT_COLUMN_LABEL, maxContextLen);
            retval.put(URI_COLUMN_LABEL, maxUriLen);
            retval.put(ROUTE_COLUMN_LABEL, maxRouteIdLen);
            retval.put(DIRECTION_COLUMN_LABEL, maxDirectionLen);
            retval.put(STATIC_COLUMN_LABEL, maxStaticLen);
            retval.put(DYNAMIC_COLUMN_LABEL, maxDynamicLen);
            retval.put(HITS_COLUMN_LABEL, maxHitsLen);

            return retval;
        }
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

        int contextLen = Math.min(columnWidths.get(CONTEXT_COLUMN_LABEL) + columnWidthIncrement, getMaxColumnWidth());
        contextLen = Math.max(MIN_COLUMN_WIDTH, contextLen);

        int uriLen = Math.min(columnWidths.get(URI_COLUMN_LABEL) + columnWidthIncrement, getMaxColumnWidth());
        uriLen = Math.max(MIN_COLUMN_WIDTH, uriLen);

        int routeIdLen = Math.min(columnWidths.get(ROUTE_COLUMN_LABEL) + columnWidthIncrement, getMaxColumnWidth());
        routeIdLen = Math.max(MIN_COLUMN_WIDTH, routeIdLen);

        int directionLen = Math.min(columnWidths.get(DIRECTION_COLUMN_LABEL) + columnWidthIncrement, getMaxColumnWidth());
        directionLen = Math.max(MIN_COLUMN_WIDTH, directionLen);

        int staticLen = Math.min(columnWidths.get(STATIC_COLUMN_LABEL) + columnWidthIncrement, getMaxColumnWidth());
        staticLen = Math.max(MIN_COLUMN_WIDTH, staticLen);

        int dynamicLen = Math.min(columnWidths.get(DYNAMIC_COLUMN_LABEL) + columnWidthIncrement, getMaxColumnWidth());
        dynamicLen = Math.max(MIN_COLUMN_WIDTH, dynamicLen);

        int totalLen = Math.min(columnWidths.get(HITS_COLUMN_LABEL) + columnWidthIncrement, getMaxColumnWidth());
        totalLen = Math.max(MIN_COLUMN_WIDTH, totalLen);

        // last row does not have min width

        final StringBuilder retval = new StringBuilder(DEFAULT_FORMAT_BUFFER_LENGTH);
        retval.append(fieldPreamble).append("%-").append(contextLen).append('.').append(contextLen).append('s').append(fieldPostamble).append(' ');
        retval.append(fieldPreamble).append("%-").append(uriLen).append('.').append(uriLen).append('s').append(fieldPostamble).append(' ');
        retval.append(fieldPreamble).append("%-").append(routeIdLen).append('.').append(routeIdLen).append('s').append(fieldPostamble).append(' ');
        retval.append(fieldPreamble).append("%-").append(directionLen).append('.').append(directionLen).append('s').append(fieldPostamble).append(' ');
        retval.append(fieldPreamble).append("%-").append(staticLen).append('.').append(staticLen).append('s').append(fieldPostamble).append(' ');
        retval.append(fieldPreamble).append("%-").append(dynamicLen).append('.').append(dynamicLen).append('s').append(fieldPostamble).append(' ');
        retval.append(fieldPreamble).append("%").append(totalLen).append('.').append(totalLen).append('s').append(fieldPostamble).append(' ');

        return retval.toString();
    }

    private int getMaxColumnWidth() {
        return MAX_COLUMN_WIDTH;
    }

}
