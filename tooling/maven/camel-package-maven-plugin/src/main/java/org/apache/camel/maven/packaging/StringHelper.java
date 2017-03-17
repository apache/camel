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
package org.apache.camel.maven.packaging;

import java.util.Collection;

import com.google.common.base.CaseFormat;

public final class StringHelper {

    private StringHelper() {
        // Utils Class
    }

    public static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    public static String after(String text, String after) {
        if (!text.contains(after)) {
            return null;
        }
        return text.substring(text.indexOf(after) + after.length());
    }

    public static String before(String text, String before) {
        if (!text.contains(before)) {
            return null;
        }
        return text.substring(0, text.indexOf(before));
    }

    public static String between(String text, String after, String before) {
        text = after(text, after);
        if (text == null) {
            return null;
        }
        return before(text, before);
    }

    public static String indentCollection(String indent, Collection<String> list) {
        StringBuilder sb = new StringBuilder();
        for (String text : list) {
            sb.append(indent).append(text);
        }
        return sb.toString();
    }

    /**
     * Converts the value to use title style instead of dash cased
     */
    public static String camelDashToTitle(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        boolean dash = false;

        for (char c : value.toCharArray()) {
            if ('-' == c) {
                dash = true;
                continue;
            }

            if (dash) {
                sb.append(' ');
                sb.append(Character.toUpperCase(c));
            } else {
                // upper case first
                if (sb.length() == 0) {
                    sb.append(Character.toUpperCase(c));
                } else {
                    sb.append(c);
                }
            }
            dash = false;
        }
        return sb.toString();
    }

    public static String cutLastZeroDigit(String version) {
        String answer = version;
        // cut last digit so its not 2.18.0 but 2.18
        String[] parts = version.split("\\.");
        if (parts.length == 3 && parts[2].equals("0")) {
            answer = parts[0] + "." + parts[1];
        }
        return answer;
    }

    /**
     * To wrap long camel cased texts by words.
     *
     * @param option  the option which is camel cased.
     * @param watermark a watermark to denote the size to cut after
     * @param newLine the new line to use when breaking into a new line
     */
    public static String wrapCamelCaseWords(String option, int watermark, String newLine) {
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
    private static String wrapWords(String line, String newLine, int watermark, boolean wrapLongWords) {
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
