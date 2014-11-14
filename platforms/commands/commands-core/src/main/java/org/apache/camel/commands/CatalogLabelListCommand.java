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

import org.apache.camel.util.CollectionStringBuffer;

/**
 * From the Camel catalog lists all the components.
 */
public class CatalogLabelListCommand extends AbstractCamelCommand {

    private static final String LABEL_COLUMN_LABEL = "Label";
    private static final String NUMBER_COLUMN_LABEL = "#";
    private static final String NAME_COLUMN_LABEL = "Name";

    private static final int DEFAULT_COLUMN_WIDTH_INCREMENT = 0;
    private static final String DEFAULT_FIELD_PREAMBLE = " ";
    private static final String DEFAULT_FIELD_POSTAMBLE = " ";
    private static final String DEFAULT_HEADER_PREAMBLE = " ";
    private static final String DEFAULT_HEADER_POSTAMBLE = " ";
    private static final int DEFAULT_FORMAT_BUFFER_LENGTH = 24;
    // descriptions can be very long so clip by default after 120 chars
    private static final int MAX_COLUMN_WIDTH = 120;
    private static final int MIN_COLUMN_WIDTH = 12;
    private static final int MIN_NUMBER_COLUMN_WIDTH = 6;

    private boolean verbose;

    public CatalogLabelListCommand(boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public Object execute(CamelController camelController, PrintStream out, PrintStream err) throws Exception {
        Map<String, Set<String>> labels = camelController.listLabelCatalog();

        if (labels.isEmpty()) {
            return null;
        }

        final Map<String, Integer> columnWidths = computeColumnWidths(labels);
        final String headerFormat = buildFormatString(columnWidths, true);
        final String rowFormat = buildFormatString(columnWidths, false);

        if (verbose) {
            out.println(String.format(headerFormat, LABEL_COLUMN_LABEL, NUMBER_COLUMN_LABEL, NAME_COLUMN_LABEL));
            out.println(String.format(headerFormat, "-----", "-", "----"));
        } else {
            out.println(String.format(headerFormat, LABEL_COLUMN_LABEL));
            out.println(String.format(headerFormat, "-----"));
        }

        for (Map.Entry<String, Set<String>> row : labels.entrySet()) {
            if (verbose) {
                String label = row.getKey();
                String number = "" + row.getValue().size();
                CollectionStringBuffer csb = new CollectionStringBuffer(", ");
                for (String name : row.getValue()) {
                    csb.append(name);
                }
                out.println(String.format(rowFormat, label, number, csb.toString()));
            } else {
                String label = row.getKey();
                out.println(String.format(rowFormat, label));
            }
        }

        return null;
    }

    private Map<String, Integer> computeColumnWidths(Map<String, Set<String>> labels) throws Exception {
        if (labels == null) {
            return null;
        } else {
            // some of the options is optional so we need to start from 1
            int maxLabelLen = LABEL_COLUMN_LABEL.length();
            int maxNameLen = NAME_COLUMN_LABEL.length();

            int counter = 0;
            CollectionStringBuffer csb = new CollectionStringBuffer(", ");
            for (Map.Entry<String, Set<String>> entry : labels.entrySet()) {
                String label = entry.getKey();
                maxLabelLen = Math.max(maxLabelLen, label.length());
                for (String name : entry.getValue()) {
                    counter++;
                    csb.append(name);
                }
            }
            maxNameLen = Math.max(maxNameLen, csb.toString().length());
            int maxMumberLen = ("" + counter).length();

            final Map<String, Integer> retval = new Hashtable<String, Integer>();
            retval.put(LABEL_COLUMN_LABEL, maxLabelLen);
            retval.put(NUMBER_COLUMN_LABEL, maxMumberLen);
            retval.put(NAME_COLUMN_LABEL, maxNameLen);

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

        if (verbose) {
            int labelLen = Math.min(columnWidths.get(LABEL_COLUMN_LABEL) + columnWidthIncrement, getMaxColumnWidth(isHeader));
            int numberLen = Math.min(columnWidths.get(NUMBER_COLUMN_LABEL) + columnWidthIncrement, getMaxColumnWidth(isHeader));

            labelLen = Math.max(MIN_COLUMN_WIDTH, labelLen);
            numberLen = Math.max(MIN_NUMBER_COLUMN_WIDTH, numberLen);

            final StringBuilder retval = new StringBuilder(DEFAULT_FORMAT_BUFFER_LENGTH);
            retval.append(fieldPreamble).append("%-").append(labelLen).append('.').append(labelLen).append('s').append(fieldPostamble).append(' ');
            retval.append(fieldPreamble).append("%-").append(numberLen).append('.').append(numberLen).append('s').append(fieldPostamble).append(' ');
            // allow last to overlap
            retval.append(fieldPreamble).append("%s").append(fieldPostamble).append(' ');
            return retval.toString();
        } else {
            int labelLen = Math.min(columnWidths.get(LABEL_COLUMN_LABEL) + columnWidthIncrement, getMaxColumnWidth(isHeader));
            labelLen = Math.max(MIN_COLUMN_WIDTH, labelLen);

            final StringBuilder retval = new StringBuilder(DEFAULT_FORMAT_BUFFER_LENGTH);
            retval.append(fieldPreamble).append("%-").append(labelLen).append('.').append(labelLen).append('s').append(fieldPostamble).append(' ');
            return retval.toString();
        }
    }

    private int getMaxColumnWidth(boolean isHeader) {
        if (!isHeader && verbose) {
            return Integer.MAX_VALUE;
        } else {
            return MAX_COLUMN_WIDTH;
        }
    }

}
