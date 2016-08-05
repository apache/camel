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
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

/**
 * Command to list all Camel routes.
 */
public class RouteListCommand extends AbstractCamelCommand {

    private static final String CONTEXT_COLUMN_LABEL = "Context";
    private static final String ROUTE_COLUMN_LABEL = "Route";
    private static final String STATUS_COLUMN_LABEL = "Status";
    private static final String TOTAL_COLUMN_LABEL = "Total #";
    private static final String FAILED_COLUMN_LABEL = "Failed #";
    private static final String INFLIGHT_COLUMN_LABEL = "Inflight #";
    private static final String UPTIME_COLUMN_LABEL = "Uptime";

    private static final int DEFAULT_COLUMN_WIDTH_INCREMENT = 0;
    private static final String DEFAULT_FIELD_PREAMBLE = " ";
    private static final String DEFAULT_FIELD_POSTAMBLE = " ";
    private static final String DEFAULT_HEADER_PREAMBLE = " ";
    private static final String DEFAULT_HEADER_POSTAMBLE = " ";
    private static final int DEFAULT_FORMAT_BUFFER_LENGTH = 24;
    private static final int MAX_COLUMN_WIDTH = Integer.MAX_VALUE;
    private static final int MIN_COLUMN_WIDTH = 12;

    String name;

    public RouteListCommand(String name) {
        this.name = name;
    }

    @Override
    public Object execute(CamelController camelController, PrintStream out, PrintStream err) throws Exception {
        List<Map<String, String>> routes = camelController.getRoutes(name);

        final Map<String, Integer> columnWidths = computeColumnWidths(routes);
        final String headerFormat = buildFormatString(columnWidths, true);
        final String rowFormat = buildFormatString(columnWidths, false);

        if (routes.size() > 0) {
            out.println(String.format(headerFormat, CONTEXT_COLUMN_LABEL, ROUTE_COLUMN_LABEL, STATUS_COLUMN_LABEL, TOTAL_COLUMN_LABEL, FAILED_COLUMN_LABEL, INFLIGHT_COLUMN_LABEL,
                    UPTIME_COLUMN_LABEL));
            out.println(String.format(headerFormat, "-------", "-----", "------", "-------", "--------", "----------", "------"));
            for (Map<String, String> row : routes) {
                String contextId = row.get("camelContextName");
                String routeId = row.get("routeId");
                String state = row.get("state");
                String total = row.get("exchangesTotal");
                String failed = row.get("exchangesFailed");
                String inflight = row.get("exchangesInflight");
                String uptime = row.get("uptime");
                out.println(String.format(rowFormat, contextId, routeId, state, total, failed, inflight, uptime));
            }
        }

        return null;
    }

    private static Map<String, Integer> computeColumnWidths(final Iterable<Map<String, String>> routes) throws Exception {
        if (routes == null) {
            throw new IllegalArgumentException("Unable to determine column widths from null Iterable<Route>");
        } else {
            int maxContextLen = 0;
            int maxRouteLen = 0;
            int maxStatusLen = 0;
            int maxTotalLen = 0;
            int maxFailedLen = 0;
            int maxInflightLen = 0;
            int maxUptimeLen = 0;

            for (Map<String, String> row : routes) {
                final String contextId = row.get("camelContextName");
                maxContextLen = java.lang.Math.max(maxContextLen, contextId == null ? 0 : contextId.length());

                final String routeId = row.get("routeId");
                maxRouteLen = java.lang.Math.max(maxRouteLen, routeId == null ? 0 : routeId.length());

                final String status = row.get("state");
                maxStatusLen = java.lang.Math.max(maxStatusLen, status == null ? 0 : status.length());

                final String total = row.get("exchangesTotal");
                maxTotalLen = java.lang.Math.max(maxTotalLen, total == null ? 0 : total.length());

                final String failed = row.get("exchangesFailed");
                maxFailedLen = java.lang.Math.max(maxFailedLen, failed == null ? 0 : failed.length());

                final String inflight = row.get("exchangesInflight");
                maxInflightLen = java.lang.Math.max(maxInflightLen, inflight == null ? 0 : inflight.length());

                final String uptime = row.get("uptime");
                maxUptimeLen = java.lang.Math.max(maxUptimeLen, uptime == null ? 0 : uptime.length());
            }

            final Map<String, Integer> retval = new Hashtable<String, Integer>(3);
            retval.put(CONTEXT_COLUMN_LABEL, maxContextLen);
            retval.put(ROUTE_COLUMN_LABEL, maxRouteLen);
            retval.put(STATUS_COLUMN_LABEL, maxStatusLen);
            retval.put(TOTAL_COLUMN_LABEL, maxTotalLen);
            retval.put(FAILED_COLUMN_LABEL, maxFailedLen);
            retval.put(INFLIGHT_COLUMN_LABEL, maxInflightLen);
            retval.put(UPTIME_COLUMN_LABEL, maxUptimeLen);

            return retval;
        }
    }

    private static String buildFormatString(final Map<String, Integer> columnWidths, final boolean isHeader) {
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

        int contextLen = Math.min(columnWidths.get(CONTEXT_COLUMN_LABEL) + columnWidthIncrement, MAX_COLUMN_WIDTH);
        int routeLen = Math.min(columnWidths.get(ROUTE_COLUMN_LABEL) + columnWidthIncrement, MAX_COLUMN_WIDTH);
        int statusLen = Math.min(columnWidths.get(STATUS_COLUMN_LABEL) + columnWidthIncrement, MAX_COLUMN_WIDTH);
        int totalLen = Math.min(columnWidths.get(TOTAL_COLUMN_LABEL) + columnWidthIncrement, MAX_COLUMN_WIDTH);
        int failedlLen = Math.min(columnWidths.get(FAILED_COLUMN_LABEL) + columnWidthIncrement, MAX_COLUMN_WIDTH);
        int inflightLen = Math.min(columnWidths.get(INFLIGHT_COLUMN_LABEL) + columnWidthIncrement, MAX_COLUMN_WIDTH);
        int uptimeLen = Math.min(columnWidths.get(UPTIME_COLUMN_LABEL) + columnWidthIncrement, MAX_COLUMN_WIDTH);
        contextLen = Math.max(MIN_COLUMN_WIDTH, contextLen);
        routeLen = Math.max(MIN_COLUMN_WIDTH, routeLen);
        statusLen = Math.max(MIN_COLUMN_WIDTH, statusLen);
        totalLen = Math.max(MIN_COLUMN_WIDTH, totalLen);
        failedlLen = Math.max(MIN_COLUMN_WIDTH, failedlLen);
        inflightLen = Math.max(MIN_COLUMN_WIDTH, inflightLen);
        uptimeLen = Math.max(MIN_COLUMN_WIDTH, uptimeLen);

        final StringBuilder retval = new StringBuilder(DEFAULT_FORMAT_BUFFER_LENGTH);
        retval.append(fieldPreamble).append("%-").append(contextLen).append('.').append(contextLen).append('s').append(fieldPostamble).append(' ');
        retval.append(fieldPreamble).append("%-").append(routeLen).append('.').append(routeLen).append('s').append(fieldPostamble).append(' ');
        retval.append(fieldPreamble).append("%-").append(statusLen).append('.').append(statusLen).append('s').append(fieldPostamble).append(' ');
        retval.append(fieldPreamble).append("%").append(totalLen).append('.').append(totalLen).append('s').append(fieldPostamble).append(' ');
        retval.append(fieldPreamble).append("%").append(failedlLen).append('.').append(failedlLen).append('s').append(fieldPostamble).append(' ');
        retval.append(fieldPreamble).append("%").append(inflightLen).append('.').append(inflightLen).append('s').append(fieldPostamble).append(' ');
        retval.append(fieldPreamble).append("%-").append(uptimeLen).append('.').append(uptimeLen).append('s').append(fieldPostamble).append(' ');

        return retval.toString();
    }
}
