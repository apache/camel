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
 * Command to list all {@link org.apache.camel.CamelContext} in the JVM.
 */
public class ContextListCommand extends AbstractCamelCommand {

    private static final String CONTEXT_COLUMN_LABEL = "Context";
    private static final String STATUS_COLUMN_LABEL = "Status";
    private static final String UPTIME_COLUMN_LABEL = "Uptime";
    private static final int DEFAULT_FORMAT_BUFFER_LENGTH = 24;
    private static final String DEFAULT_FIELD_PREAMBLE = " ";
    private static final String DEFAULT_FIELD_POSTAMBLE = " ";
    private static final String DEFAULT_HEADER_PREAMBLE = " ";
    private static final String DEFAULT_HEADER_POSTAMBLE = " ";
    private static final int DEFAULT_COLUMN_WIDTH_INCREMENT = 0;
    private static final int MAX_COLUMN_WIDTH = Integer.MAX_VALUE;
    private static final int MIN_COLUMN_WIDTH = 12;

    @Override
    public Object execute(CamelController camelController, PrintStream out, PrintStream err) throws Exception {
        final List<Map<String, String>> camelContexts = camelController.getCamelContexts();

        final Map<String, Integer> columnWidths = computeColumnWidths(camelContexts);
        final String headerFormat = buildFormatString(columnWidths, true);
        final String rowFormat = buildFormatString(columnWidths, false);

        if (camelContexts.size() > 0) {
            out.println(String.format(headerFormat, CONTEXT_COLUMN_LABEL, STATUS_COLUMN_LABEL, UPTIME_COLUMN_LABEL));
            out.println(String.format(headerFormat, "-------", "------", "------"));
            for (Map<String, String> row : camelContexts) {
                out.println(String.format(rowFormat, row.get("name"), row.get("state"), row.get("uptime")));
            }
        }

        return null;
    }

    private static Map<String, Integer> computeColumnWidths(final Iterable<Map<String, String>> camelContexts) throws Exception {
        if (camelContexts == null) {
            throw new IllegalArgumentException("Unable to determine column widths from null Iterable<CamelContext>");
        } else {
            int maxNameLen = 0;
            int maxStatusLen = 0;
            int maxUptimeLen = 0;

            for (Map<String, String> row : camelContexts) {
                final String name = row.get("name");
                maxNameLen = java.lang.Math.max(maxNameLen, name == null ? 0 : name.length());

                final String status = row.get("state");
                maxStatusLen = java.lang.Math.max(maxStatusLen, status == null ? 0 : status.length());

                final String uptime = row.get("uptime");
                maxUptimeLen = java.lang.Math.max(maxUptimeLen, uptime == null ? 0 : uptime.length());
            }

            final Map<String, Integer> retval = new Hashtable<String, Integer>(3);
            retval.put(CONTEXT_COLUMN_LABEL, maxNameLen);
            retval.put(STATUS_COLUMN_LABEL, maxStatusLen);
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

        int contextLen = java.lang.Math.min(columnWidths.get(CONTEXT_COLUMN_LABEL) + columnWidthIncrement, MAX_COLUMN_WIDTH);
        int statusLen = java.lang.Math.min(columnWidths.get(STATUS_COLUMN_LABEL) + columnWidthIncrement, MAX_COLUMN_WIDTH);
        int uptimeLen = java.lang.Math.min(columnWidths.get(UPTIME_COLUMN_LABEL) + columnWidthIncrement, MAX_COLUMN_WIDTH);
        contextLen = Math.max(MIN_COLUMN_WIDTH, contextLen);
        statusLen = Math.max(MIN_COLUMN_WIDTH, statusLen);
        // last row does not have min width

        final StringBuilder retval = new StringBuilder(DEFAULT_FORMAT_BUFFER_LENGTH);
        retval.append(fieldPreamble).append("%-").append(contextLen).append('.').append(contextLen).append('s').append(fieldPostamble).append(' ');
        retval.append(fieldPreamble).append("%-").append(statusLen).append('.').append(statusLen).append('s').append(fieldPostamble).append(' ');
        retval.append(fieldPreamble).append("%-").append(uptimeLen).append('.').append(uptimeLen).append('s').append(fieldPostamble).append(' ');

        return retval.toString();
    }

}
