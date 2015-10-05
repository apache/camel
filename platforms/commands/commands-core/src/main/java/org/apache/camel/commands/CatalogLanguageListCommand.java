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
 * From the Camel catalog lists all the languages.
 */
public class CatalogLanguageListCommand extends AbstractCamelCommand {

    private static final String TITLE_COLUMN_LABEL = "Title";
    private static final String NAME_COLUMN_LABEL = "Name";
    private static final String LABEL_COLUMN_LABEL = "Label";
    private static final String MAVEN_COLUMN_LABEL = "Maven Coordinate";
    private static final String DESCRIPTION_COLUMN_LABEL = "Description";

    private static final int DEFAULT_COLUMN_WIDTH_INCREMENT = 0;
    private static final String DEFAULT_FIELD_PREAMBLE = " ";
    private static final String DEFAULT_FIELD_POSTAMBLE = " ";
    private static final String DEFAULT_HEADER_PREAMBLE = " ";
    private static final String DEFAULT_HEADER_POSTAMBLE = " ";
    private static final int DEFAULT_FORMAT_BUFFER_LENGTH = 24;
    // descriptions can be very long so clip by default after 120 chars
    private static final int MAX_COLUMN_WIDTH = 120;
    private static final int MIN_COLUMN_WIDTH = 12;

    private boolean verbose;
    private String label;

    public CatalogLanguageListCommand(boolean verbose, String label) {
        this.verbose = verbose;
        this.label = label;
    }

    @Override
    public Object execute(CamelController camelController, PrintStream out, PrintStream err) throws Exception {
        List<Map<String, String>> languages = camelController.listLanguagesCatalog(label);

        if (languages == null || languages.isEmpty()) {
            return null;
        }

        final Map<String, Integer> columnWidths = computeColumnWidths(languages);
        final String headerFormat = buildFormatString(columnWidths, true);
        final String rowFormat = buildFormatString(columnWidths, false);

        if (verbose) {
            out.println(String.format(headerFormat, TITLE_COLUMN_LABEL, NAME_COLUMN_LABEL, LABEL_COLUMN_LABEL, MAVEN_COLUMN_LABEL));
            out.println(String.format(headerFormat, "-----", "----", "-----", "----------------"));
        } else {
            out.println(String.format(headerFormat, TITLE_COLUMN_LABEL, DESCRIPTION_COLUMN_LABEL));
            out.println(String.format(headerFormat, "-----", "-----------"));
        }
        for (final Map<String, String> language : languages) {
            if (verbose) {
                String title = safeNull(language.get("title"));
                String name = safeNull(language.get("name"));
                String label = safeNull(language.get("label"));
                String maven = "";
                if (language.containsKey("groupId") && language.containsKey("artifactId") && language.containsKey("version")) {
                    maven = language.get("groupId") + "/" + language.get("artifactId") + "/" + language.get("version");
                }
                out.println(String.format(rowFormat, title, name, label, maven));
            } else {
                String title = safeNull(language.get("title"));
                String description = safeNull(language.get("description"));
                out.println(String.format(rowFormat, title, description));
            }
        }

        return null;
    }

    private Map<String, Integer> computeColumnWidths(final Iterable<Map<String, String>> languages) throws Exception {
        if (languages == null) {
            return null;
        } else {
            // some of the options is optional so we need to start from 1
            int maxTitleLen = TITLE_COLUMN_LABEL.length();
            int maxNameLen = NAME_COLUMN_LABEL.length();
            int maxLabelLen = LABEL_COLUMN_LABEL.length();
            int maxMavenLen = MAVEN_COLUMN_LABEL.length();
            int maxDescriptionLen = DESCRIPTION_COLUMN_LABEL.length();

            for (final Map<String, String> dataFormat : languages) {

                // grab the information and compute max len
                String title = dataFormat.get("title");
                if (title != null) {
                    maxTitleLen = Math.max(maxTitleLen, title.length());
                }
                String name = dataFormat.get("name");
                if (name != null) {
                    maxNameLen = Math.max(maxNameLen, name.length());
                }
                String label = dataFormat.get("label");
                if (label != null) {
                    maxLabelLen = Math.max(maxLabelLen, label.length());
                }
                if (dataFormat.containsKey("groupId") && dataFormat.containsKey("artifactId") && dataFormat.containsKey("version")) {
                    String mvn = dataFormat.get("groupId") + "/" + dataFormat.get("artifactId") + "/" + dataFormat.get("version");
                    maxMavenLen = Math.max(maxMavenLen, mvn.length());
                }
                String description = dataFormat.get("description");
                if (description != null) {
                    maxDescriptionLen = Math.max(maxDescriptionLen, description.length());
                }
            }

            final Map<String, Integer> retval = new Hashtable<String, Integer>();
            retval.put(TITLE_COLUMN_LABEL, maxTitleLen);
            retval.put(NAME_COLUMN_LABEL, maxNameLen);
            retval.put(LABEL_COLUMN_LABEL, maxLabelLen);
            retval.put(MAVEN_COLUMN_LABEL, maxMavenLen);
            retval.put(DESCRIPTION_COLUMN_LABEL, maxDescriptionLen);

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
            int titleLen = Math.min(columnWidths.get(TITLE_COLUMN_LABEL) + columnWidthIncrement, getMaxColumnWidth());
            int nameLen = Math.min(columnWidths.get(NAME_COLUMN_LABEL) + columnWidthIncrement, getMaxColumnWidth());
            int labelLen = Math.min(columnWidths.get(LABEL_COLUMN_LABEL) + columnWidthIncrement, getMaxColumnWidth());
            int mavenLen = Math.min(columnWidths.get(MAVEN_COLUMN_LABEL) + columnWidthIncrement, getMaxColumnWidth());

            titleLen = Math.max(MIN_COLUMN_WIDTH, titleLen);
            nameLen = Math.max(MIN_COLUMN_WIDTH, nameLen);
            labelLen = Math.max(MIN_COLUMN_WIDTH, labelLen);
            mavenLen = Math.max(MIN_COLUMN_WIDTH, mavenLen);

            final StringBuilder retval = new StringBuilder(DEFAULT_FORMAT_BUFFER_LENGTH);
            retval.append(fieldPreamble).append("%-").append(titleLen).append('.').append(titleLen).append('s').append(fieldPostamble).append(' ');
            retval.append(fieldPreamble).append("%-").append(nameLen).append('.').append(nameLen).append('s').append(fieldPostamble).append(' ');
            retval.append(fieldPreamble).append("%-").append(labelLen).append('.').append(labelLen).append('s').append(fieldPostamble).append(' ');
            retval.append(fieldPreamble).append("%-").append(mavenLen).append('.').append(mavenLen).append('s').append(fieldPostamble).append(' ');
            return retval.toString();
        } else {
            int titleLen = Math.min(columnWidths.get(TITLE_COLUMN_LABEL) + columnWidthIncrement, getMaxColumnWidth());
            int descriptionLen = Math.min(columnWidths.get(DESCRIPTION_COLUMN_LABEL) + columnWidthIncrement, getMaxColumnWidth());

            titleLen = Math.max(MIN_COLUMN_WIDTH, titleLen);
            descriptionLen = Math.max(MIN_COLUMN_WIDTH, descriptionLen);

            final StringBuilder retval = new StringBuilder(DEFAULT_FORMAT_BUFFER_LENGTH);
            retval.append(fieldPreamble).append("%-").append(titleLen).append('.').append(titleLen).append('s').append(fieldPostamble).append(' ');
            retval.append(fieldPreamble).append("%-").append(descriptionLen).append('.').append(descriptionLen).append('s').append(fieldPostamble).append(' ');
            return retval.toString();
        }
    }

    private int getMaxColumnWidth() {
        if (verbose) {
            return Integer.MAX_VALUE;
        } else {
            return MAX_COLUMN_WIDTH;
        }
    }

}
