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
 * Command to display inflight exchange information
 */
public class ContextInflightCommand extends AbstractContextCommand {

    private static final String EXCHANGE_COLUMN_LABEL = "ExchangeId";
    private static final String FROM_ROUTE_COLUMN_LABEL = "From Route";
    private static final String ROUTE_COLUMN_LABEL = "Route";
    private static final String NODE_COLUMN_LABEL = "Node";
    private static final String ELAPSED_COLUMN_LABEL = "Elapsed (ms)";
    private static final String DURATION_COLUMN_LABEL = "Duration (ms)";

    private static final int DEFAULT_COLUMN_WIDTH_INCREMENT = 0;
    private static final String DEFAULT_FIELD_PREAMBLE = " ";
    private static final String DEFAULT_FIELD_POSTAMBLE = " ";
    private static final String DEFAULT_HEADER_PREAMBLE = " ";
    private static final String DEFAULT_HEADER_POSTAMBLE = " ";
    private static final int DEFAULT_FORMAT_BUFFER_LENGTH = 24;
    private static final int MAX_COLUMN_WIDTH = Integer.MAX_VALUE;
    private static final int MIN_COLUMN_WIDTH = 12;

    private int limit;
    private String route;
    private boolean sortByLongestDuration;

    public ContextInflightCommand(String context, String route, int limit, boolean sortByLongestDuration) {
        super(context);
        this.route = route;
        this.limit = limit;
        this.sortByLongestDuration = sortByLongestDuration;
    }

    @Override
    protected Object performContextCommand(CamelController camelController, String contextName, PrintStream out, PrintStream err) throws Exception {
        List<Map<String, Object>> inflight = camelController.browseInflightExchanges(contextName, route, limit, sortByLongestDuration);

        final Map<String, Integer> columnWidths = computeColumnWidths(inflight);
        final String headerFormat = buildFormatString(columnWidths, true);
        final String rowFormat = buildFormatString(columnWidths, false);

        if (inflight.size() > 0) {
            out.println(String.format(headerFormat, EXCHANGE_COLUMN_LABEL, FROM_ROUTE_COLUMN_LABEL, ROUTE_COLUMN_LABEL, NODE_COLUMN_LABEL, ELAPSED_COLUMN_LABEL, DURATION_COLUMN_LABEL));
            out.println(String.format(headerFormat, "----------", "----------", "-----", "----", "------------", "-------------"));
            for (Map<String, Object> row : inflight) {
                Object exchangeId = row.get("exchangeId");
                Object fromRouteId = row.get("fromRouteId");
                Object routeId = row.get("routeId");
                Object nodeId = row.get("nodeId");
                Object elapsed = row.get("elapsed");
                Object duration = row.get("duration");
                out.println(String.format(rowFormat, exchangeId, fromRouteId, routeId, nodeId, safeNull(elapsed), safeNull(duration)));
            }
        }

        return null;
    }

    private Map<String, Integer> computeColumnWidths(final Iterable<Map<String, Object>> inflight) throws Exception {
        if (inflight == null) {
            throw new IllegalArgumentException("Unable to determine column widths from null Iterable<Inflight>");
        } else {
            int maxExchangeLen = 0;
            int maxFromRouteLen = 0;
            int maxRouteLen = 0;
            int maxNodeLen = 0;
            int maxElapsedLen = 0;
            int maxDurationLen = 0;

            for (Map<String, Object> row : inflight) {
                final String exchangeId = safeNull(row.get("exchangeId"));
                maxExchangeLen = java.lang.Math.max(maxExchangeLen, exchangeId == null ? 0 : exchangeId.length());

                final String fromRouteId = safeNull(row.get("fromRouteId"));
                maxFromRouteLen = java.lang.Math.max(maxFromRouteLen, fromRouteId == null ? 0 : fromRouteId.length());

                final String routeId = safeNull(row.get("routeId"));
                maxRouteLen = java.lang.Math.max(maxRouteLen, routeId == null ? 0 : routeId.length());

                final String nodeId = safeNull(row.get("nodeId"));
                maxNodeLen = java.lang.Math.max(maxNodeLen, nodeId == null ? 0 : nodeId.length());

                final String elapsed = safeNull(row.get("elapsed"));
                maxElapsedLen = java.lang.Math.max(maxElapsedLen, elapsed == null ? 0 : elapsed.length());

                final String duration = safeNull(row.get("duration"));
                maxDurationLen = java.lang.Math.max(maxDurationLen, duration == null ? 0 : duration.length());
            }

            final Map<String, Integer> retval = new Hashtable<String, Integer>(5);
            retval.put(EXCHANGE_COLUMN_LABEL, maxExchangeLen);
            retval.put(FROM_ROUTE_COLUMN_LABEL, maxFromRouteLen);
            retval.put(ROUTE_COLUMN_LABEL, maxRouteLen);
            retval.put(NODE_COLUMN_LABEL, maxNodeLen);
            retval.put(ELAPSED_COLUMN_LABEL, maxElapsedLen);
            retval.put(DURATION_COLUMN_LABEL, maxDurationLen);

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

        int exchangeLen = Math.min(columnWidths.get(EXCHANGE_COLUMN_LABEL) + columnWidthIncrement, MAX_COLUMN_WIDTH);
        int fromRouteLen = Math.min(columnWidths.get(FROM_ROUTE_COLUMN_LABEL) + columnWidthIncrement, MAX_COLUMN_WIDTH);
        int routeLen = Math.min(columnWidths.get(ROUTE_COLUMN_LABEL) + columnWidthIncrement, MAX_COLUMN_WIDTH);
        int nodeLen = Math.min(columnWidths.get(NODE_COLUMN_LABEL) + columnWidthIncrement, MAX_COLUMN_WIDTH);
        int elapsedLen = Math.min(columnWidths.get(ELAPSED_COLUMN_LABEL) + columnWidthIncrement, MAX_COLUMN_WIDTH);
        int durationLen = Math.min(columnWidths.get(DURATION_COLUMN_LABEL) + columnWidthIncrement, MAX_COLUMN_WIDTH);
        exchangeLen = Math.max(MIN_COLUMN_WIDTH, exchangeLen);
        fromRouteLen = Math.max(MIN_COLUMN_WIDTH, fromRouteLen);
        routeLen = Math.max(MIN_COLUMN_WIDTH, routeLen);
        nodeLen = Math.max(MIN_COLUMN_WIDTH, nodeLen);
        elapsedLen = Math.max(MIN_COLUMN_WIDTH, elapsedLen);
        durationLen = Math.max(13, durationLen);

        final StringBuilder retval = new StringBuilder(DEFAULT_FORMAT_BUFFER_LENGTH);
        retval.append(fieldPreamble).append("%-").append(exchangeLen).append('.').append(exchangeLen).append('s').append(fieldPostamble).append(' ');
        retval.append(fieldPreamble).append("%-").append(fromRouteLen).append('.').append(fromRouteLen).append('s').append(fieldPostamble).append(' ');
        retval.append(fieldPreamble).append("%-").append(routeLen).append('.').append(routeLen).append('s').append(fieldPostamble).append(' ');
        retval.append(fieldPreamble).append("%-").append(nodeLen).append('.').append(nodeLen).append('s').append(fieldPostamble).append(' ');
        retval.append(fieldPreamble).append("%").append(elapsedLen).append('.').append(elapsedLen).append('s').append(fieldPostamble).append(' ');
        retval.append(fieldPreamble).append("%").append(durationLen).append('.').append(durationLen).append('s').append(fieldPostamble).append(' ');

        return retval.toString();
    }

}
