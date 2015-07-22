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
 * From the Camel catalog lists all the components.
 */
public class CatalogComponentListCommand extends AbstractCamelCommand {

    private static final String TITLE_COLUMN_LABEL = "Title";
    private static final String SCHEME_COLUMN_LABEL = "Scheme";
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

    public CatalogComponentListCommand(boolean verbose, String label) {
        this.verbose = verbose;
        this.label = label;
    }

    @Override
    public Object execute(CamelController camelController, PrintStream out, PrintStream err) throws Exception {
        List<Map<String, String>> components = camelController.listComponentsCatalog(label);

        if (components == null || components.isEmpty()) {
            return null;
        }

        final Map<String, Integer> columnWidths = computeColumnWidths(components);
        final String headerFormat = buildFormatString(columnWidths, true);
        final String rowFormat = buildFormatString(columnWidths, false);

        if (verbose) {
            out.println(String.format(headerFormat, TITLE_COLUMN_LABEL, SCHEME_COLUMN_LABEL, LABEL_COLUMN_LABEL, MAVEN_COLUMN_LABEL));
            out.println(String.format(headerFormat, "-----", "------", "-----", "----------------"));
        } else {
            out.println(String.format(headerFormat, TITLE_COLUMN_LABEL, DESCRIPTION_COLUMN_LABEL));
            out.println(String.format(headerFormat, "-----", "-----------"));
        }
        for (final Map<String, String> component : components) {
            if (verbose) {
                String title = safeNull(component.get("title"));
                String scheme = safeNull(component.get("name"));
                String label = safeNull(component.get("label"));
                String maven = "";
                if (component.containsKey("groupId") && component.containsKey("artifactId") && component.containsKey("version")) {
                    maven = component.get("groupId") + "/" + component.get("artifactId") + "/" + component.get("version");
                }
                out.println(String.format(rowFormat, title, scheme, label, maven));
            } else {
                String title = safeNull(component.get("title"));
                String description = safeNull(component.get("description"));
                out.println(String.format(rowFormat, title, description));
            }
        }

        return null;
    }

    private Map<String, Integer> computeColumnWidths(final Iterable<Map<String, String>> components) throws Exception {
        if (components == null) {
            return null;
        } else {
            // some of the options is optional so we need to start from 1
            int maxTitleLen = TITLE_COLUMN_LABEL.length();
            int maxSchemeLen = SCHEME_COLUMN_LABEL.length();
            int maxLabelLen = LABEL_COLUMN_LABEL.length();
            int maxMavenLen = MAVEN_COLUMN_LABEL.length();
            int maxDescriptionLen = DESCRIPTION_COLUMN_LABEL.length();

            for (final Map<String, String> component : components) {

                // grab the information and compute max len
                String name = component.get("name");
                if (name != null) {
                    maxSchemeLen = Math.max(maxSchemeLen, name.length());
                }
                String title = component.get("title");
                if (title != null) {
                    maxTitleLen = Math.max(maxTitleLen, title.length());
                }
                String label = component.get("label");
                if (label != null) {
                    maxLabelLen = Math.max(maxLabelLen, label.length());
                }
                if (component.containsKey("groupId") && component.containsKey("artifactId") && component.containsKey("version")) {
                    String mvn = component.get("groupId") + "/" + component.get("artifactId") + "/" + component.get("version");
                    maxMavenLen = Math.max(maxMavenLen, mvn.length());
                }
                String description = component.get("description");
                if (description != null) {
                    maxDescriptionLen = Math.max(maxDescriptionLen, description.length());
                }
            }

            final Map<String, Integer> retval = new Hashtable<String, Integer>();
            retval.put(TITLE_COLUMN_LABEL, maxTitleLen);
            retval.put(SCHEME_COLUMN_LABEL, maxSchemeLen);
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
            int schemeLen = Math.min(columnWidths.get(SCHEME_COLUMN_LABEL) + columnWidthIncrement, getMaxColumnWidth());
            int labelLen = Math.min(columnWidths.get(LABEL_COLUMN_LABEL) + columnWidthIncrement, getMaxColumnWidth());
            int mavenLen = Math.min(columnWidths.get(MAVEN_COLUMN_LABEL) + columnWidthIncrement, getMaxColumnWidth());

            titleLen = Math.max(MIN_COLUMN_WIDTH, titleLen);
            schemeLen = Math.max(MIN_COLUMN_WIDTH, schemeLen);
            labelLen = Math.max(MIN_COLUMN_WIDTH, labelLen);
            mavenLen = Math.max(MIN_COLUMN_WIDTH, mavenLen);

            final StringBuilder retval = new StringBuilder(DEFAULT_FORMAT_BUFFER_LENGTH);
            retval.append(fieldPreamble).append("%-").append(titleLen).append('.').append(titleLen).append('s').append(fieldPostamble).append(' ');
            retval.append(fieldPreamble).append("%-").append(schemeLen).append('.').append(schemeLen).append('s').append(fieldPostamble).append(' ');
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
