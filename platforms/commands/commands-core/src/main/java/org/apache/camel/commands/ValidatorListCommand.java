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
 * List the Camel validators available in the JVM.
 */
public class ValidatorListCommand extends AbstractCamelCommand {

    private static final String CONTEXT_NAME_COLUMN_LABEL = "Context";
    private static final String TYPE_COLUMN_LABEL = "Type";
    private static final String STATE_COLUMN_LABEL = "State";
    private static final String DESCRIPTION_COLUMN_LABEL = "Description";

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

    public ValidatorListCommand(String context, boolean decode, boolean verbose, boolean explain) {
        this.decode = decode;
        this.verbose = verbose;
        this.explain = explain;
        this.context = context;
    }

    @Override
    public Object execute(CamelController camelController, PrintStream out, PrintStream err) throws Exception {
        final List<Map<String, String>> camelContextInfos = camelController.getCamelContexts(this.context);
        final Map<String, List<Map<String, String>>> contextsToValidators = new HashMap<>();
        
        for (Map<String, String> camelContextInfo : camelContextInfos) {
            String camelContextName = camelContextInfo.get("name");
            final List<Map<String, String>> validators = camelController.getValidators(camelContextName);
            if (validators.isEmpty()) {
                continue;
            }
            contextsToValidators.put(camelContextName, validators);
        }

        final Map<String, Integer> columnWidths = computeColumnWidths(contextsToValidators);
        final String headerFormat = buildFormatString(columnWidths, true);
        final String rowFormat = buildFormatString(columnWidths, false);

        for (Map.Entry<String, List<Map<String, String>>> stringListEntry : contextsToValidators.entrySet()) {
            final String camelContextName = stringListEntry.getKey();
            final List<Map<String, String>> validators = stringListEntry.getValue();

            if (verbose) {
                out.println(String.format(headerFormat, CONTEXT_NAME_COLUMN_LABEL, TYPE_COLUMN_LABEL, STATE_COLUMN_LABEL, DESCRIPTION_COLUMN_LABEL));
                out.println(String.format(headerFormat, "-------", "----", "-----", "-----------"));
            } else {
                out.println(String.format(headerFormat, CONTEXT_NAME_COLUMN_LABEL, TYPE_COLUMN_LABEL, STATE_COLUMN_LABEL));
                out.println(String.format(headerFormat, "-------", "----", "-----"));
            }
            for (Map<String, String> row : validators) {
                String type = row.get("type");
                String state = row.get("state");
                if (verbose) {
                    String desc = row.get("description");
                    out.println(String.format(rowFormat, camelContextName, type, state, desc));
                } else {
                    out.println(String.format(rowFormat, camelContextName, type, state));
                }
            }
        }
        return null;
    }

    private Map<String, Integer> computeColumnWidths(final Map<String, List<Map<String, String>>> contextsToValidators) throws Exception {
        int maxCamelContextLen = 0;
        int maxTypeLen = 0;
        int maxStatusLen = 0;
        int maxDescLen = 0;

        for (Map.Entry<String, List<Map<String, String>>> stringListEntry : contextsToValidators.entrySet()) {
            final String camelContextName = stringListEntry.getKey();

            maxCamelContextLen = java.lang.Math.max(maxCamelContextLen, camelContextName.length());
            
            final List<Map<String, String>> validators = stringListEntry.getValue();


            for (Map<String, String> row : validators) {
                String type = row.get("type");
                maxTypeLen = java.lang.Math.max(maxTypeLen, type == null ? 0 : type.length());
                String status = row.get("state");
                maxStatusLen = java.lang.Math.max(maxStatusLen, status == null ? 0 : status.length());
                if (verbose) {
                    String desc = row.get("description");
                    maxDescLen = java.lang.Math.max(maxDescLen, desc == null ? 0 : desc.length());
                }
            }
        }
    
        final Map<String, Integer> retval = new Hashtable<>();
        retval.put(CONTEXT_NAME_COLUMN_LABEL, maxCamelContextLen);
        retval.put(TYPE_COLUMN_LABEL, maxTypeLen);
        retval.put(STATE_COLUMN_LABEL, maxStatusLen);
        if (verbose) {
            retval.put(DESCRIPTION_COLUMN_LABEL, maxDescLen);
        }

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

        int ctxLen = java.lang.Math.min(columnWidths.get(CONTEXT_NAME_COLUMN_LABEL) + columnWidthIncrement, getMaxColumnWidth());
        ctxLen = Math.max(MIN_COLUMN_WIDTH, ctxLen);
        int typeLen = java.lang.Math.min(columnWidths.get(TYPE_COLUMN_LABEL) + columnWidthIncrement, getMaxColumnWidth());
        typeLen = Math.max(MIN_COLUMN_WIDTH, typeLen);
        int stateLen = -1;
        if (verbose) {
            stateLen = java.lang.Math.min(columnWidths.get(STATE_COLUMN_LABEL) + columnWidthIncrement, getMaxColumnWidth());
            stateLen = Math.max(MIN_COLUMN_WIDTH, stateLen);
        }
        // last row does not have min width

        final StringBuilder retval = new StringBuilder(DEFAULT_FORMAT_BUFFER_LENGTH);
        retval.append(fieldPreamble).append("%-").append(ctxLen).append('.').append(ctxLen).append('s').append(fieldPostamble).append(' ');
        retval.append(fieldPreamble).append("%-").append(typeLen).append('.').append(typeLen).append('s').append(fieldPostamble).append(' ');
        if (verbose) {
            retval.append(fieldPreamble).append("%-").append(stateLen).append('.').append(stateLen).append('s').append(fieldPostamble).append(' ');
        }
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
