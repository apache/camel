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
import java.util.Map;
import java.util.Set;

/**
 * From the Camel catalog lists all the components.
 */
public class CatalogLabelListCommand extends AbstractCamelCommand {

    private static final String LABEL_COLUMN_LABEL = "Label";

    private static final int DEFAULT_COLUMN_WIDTH_INCREMENT = 0;
    private static final String DEFAULT_FIELD_PREAMBLE = " ";
    private static final String DEFAULT_FIELD_POSTAMBLE = " ";
    private static final String DEFAULT_HEADER_PREAMBLE = " ";
    private static final String DEFAULT_HEADER_POSTAMBLE = " ";
    private static final int DEFAULT_FORMAT_BUFFER_LENGTH = 24;
    // descriptions can be very long so clip by default after 120 chars
    private static final int MAX_COLUMN_WIDTH = 120;
    private static final int MIN_COLUMN_WIDTH = 12;

    public CatalogLabelListCommand() {
    }

    @Override
    public Object execute(CamelController camelController, PrintStream out, PrintStream err) throws Exception {
        Set<String> labels = camelController.listLabelCatalog();

        if (labels.isEmpty()) {
            return null;
        }

        final Map<String, Integer> columnWidths = computeColumnWidths(labels);
        final String headerFormat = buildFormatString(columnWidths, true);
        final String rowFormat = buildFormatString(columnWidths, false);

        out.println(String.format(headerFormat, LABEL_COLUMN_LABEL));
        out.println(String.format(headerFormat, "-----"));
        for (String label : labels) {
            out.println(String.format(rowFormat, label));
        }

        return null;
    }

    private Map<String, Integer> computeColumnWidths(final Iterable<String> labels) throws Exception {
        if (labels == null) {
            return null;
        } else {
            // some of the options is optional so we need to start from 1
            int maxLabelLen = LABEL_COLUMN_LABEL.length();

            for (String label : labels) {
                maxLabelLen = Math.max(maxLabelLen, label.length());
            }

            final Map<String, Integer> retval = new Hashtable<String, Integer>(1);
            retval.put(LABEL_COLUMN_LABEL, maxLabelLen);

            return retval;
        }
    }

    private String buildFormatString(Map<String, Integer> columnWidths, boolean isHeader) {
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

        int labelLen = Math.min(columnWidths.get(LABEL_COLUMN_LABEL) + columnWidthIncrement, getMaxColumnWidth());
        labelLen = Math.max(MIN_COLUMN_WIDTH, labelLen);

        final StringBuilder retval = new StringBuilder(DEFAULT_FORMAT_BUFFER_LENGTH);
        retval.append(fieldPreamble).append("%-").append(labelLen).append('.').append(labelLen).append('s').append(fieldPostamble).append(' ');
        return retval.toString();
    }

    private int getMaxColumnWidth() {
        return MAX_COLUMN_WIDTH;
    }

}
