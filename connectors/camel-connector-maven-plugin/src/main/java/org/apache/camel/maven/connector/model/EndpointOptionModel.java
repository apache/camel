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
package org.apache.camel.maven.connector.model;

import com.google.common.base.CaseFormat;

public class EndpointOptionModel extends OptionModel {
    private String prefix;
    private String multiValue;
    private String enumValues;

    // special for documentation rendering
    private boolean newGroup;


    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getMultiValue() {
        return multiValue;
    }

    public void setMultiValue(String multiValue) {
        this.multiValue = multiValue;
    }

    public String getEnumValues() {
        return enumValues;
    }

    public void setEnumValues(String enumValues) {
        this.enumValues = enumValues;
    }

    public boolean isNewGroup() {
        return newGroup;
    }

    public void setNewGroup(boolean newGroup) {
        this.newGroup = newGroup;
    }

    @Override
    public String getShortJavaType() {
        // TODO: use watermark in the others
        return getShortJavaType(40);
    }

    public String getShortJavaType(int watermark) {
        String group = getGroup();
        String type = getType();
        String javaType = getJavaType();

        if (javaType.startsWith("java.util.Map")) {
            return "Map";
        } else if (javaType.startsWith("java.util.Set")) {
            return "Set";
        } else if (javaType.startsWith("java.util.List")) {
            return "List";
        }

        String text = javaType;

        int pos = text.lastIndexOf(".");
        if (pos != -1) {
            text = text.substring(pos + 1);
        }

        // if its some kind of java object then lets wrap it as its long
        if ("object".equals(type)) {
            text = wrapCamelCaseWords(text, watermark, " ");
        }
        return text;
    }

    public String getShortGroup() {
        String group = getGroup();

        if (group.endsWith(" (advanced)")) {
            return group.substring(0, group.length() - 11);
        }
        return group;
    }

    public String getShortDefaultValue(int watermark) {
        String defaultValue = getDefaultValue();

        if (defaultValue.isEmpty()) {
            return "";
        }
        String text = defaultValue;
        if (text.endsWith("<T>")) {
            text = text.substring(0, text.length() - 3);
        } else if (text.endsWith("<T>>")) {
            text = text.substring(0, text.length() - 4);
        }

        // TODO: dirty hack for AUTO_ACKNOWLEDGE which we should wrap
        if ("AUTO_ACKNOWLEDGE".equals(text)) {
            return "AUTO_ ACKNOWLEDGE";
        }

        return text;
    }

    public String getShortName(int watermark) {
        String text = wrapCamelCaseWords(getName(), watermark, " ");
        // ensure the option name starts with lower-case
        return Character.toLowerCase(text.charAt(0)) + text.substring(1);
    }

    /**
     * To wrap long camel cased texts by words.
     *
     * @param option  the option which is camel cased.
     * @param watermark a watermark to denote the size to cut after
     * @param newLine the new line to use when breaking into a new line
     */
    private String wrapCamelCaseWords(String option, int watermark, String newLine) {
        String text = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN, option);
        text = text.replace('-', ' ');
        text = wrapWords(text, "\n", watermark, false);
        text = text.replace(' ', '-');
        text = CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_CAMEL, text);

        // upper case first char on each line
        String[] lines = text.split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            line = Character.toUpperCase(line.charAt(0)) + line.substring(1);
            sb.append(line);
            if (i < lines.length - 1) {
                sb.append(newLine);
            }
        }
        return sb.toString();
    }

    /**
     * To wrap a big line by words.
     *
     * @param line the big line
     * @param newLine the new line to use when breaking into a new line
     * @param watermark a watermark to denote the size to cut after
     * @param wrapLongWords whether to wrap long words
     */
    private String wrapWords(String line, String newLine, int watermark, boolean wrapLongWords) {
        if (line == null) {
            return null;
        } else {
            if (newLine == null) {
                newLine = System.lineSeparator();
            }

            if (watermark < 1) {
                watermark = 1;
            }

            int inputLineLength = line.length();
            int offset = 0;
            StringBuilder sb = new StringBuilder(inputLineLength + 32);

            while (inputLineLength - offset > watermark) {
                if (line.charAt(offset) == 32) {
                    ++offset;
                } else {
                    int spaceToWrapAt = line.lastIndexOf(32, watermark + offset);
                    if (spaceToWrapAt >= offset) {
                        sb.append(line.substring(offset, spaceToWrapAt));
                        sb.append(newLine);
                        offset = spaceToWrapAt + 1;
                    } else if (wrapLongWords) {
                        sb.append(line.substring(offset, watermark + offset));
                        sb.append(newLine);
                        offset += watermark;
                    } else {
                        spaceToWrapAt = line.indexOf(32, watermark + offset);
                        if (spaceToWrapAt >= 0) {
                            sb.append(line.substring(offset, spaceToWrapAt));
                            sb.append(newLine);
                            offset = spaceToWrapAt + 1;
                        } else {
                            sb.append(line.substring(offset));
                            offset = inputLineLength;
                        }
                    }
                }
            }

            sb.append(line.substring(offset));
            return sb.toString();
        }
    }

}