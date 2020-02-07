/*
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
package org.apache.camel.tooling.model;

/**
 * Some String helper methods
 */
public final class Strings {

    private Strings() {
        //Helper class
    }

    /**
     * Returns true if the given text is null or empty string or has <tt>null</tt> as the value
     */
    public static boolean isNullOrEmpty(String text) {
        return text == null || text.length() == 0 || "null".equals(text);
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
     * @param lineSep the new line to use when breaking into a new line
     */
    public static String wrapCamelCaseWords(String option, int watermark, String lineSep) {
        String text = option.replaceAll("(?=[A-Z][a-z])", " ");
        text = wrapWords(text, "", lineSep, watermark, false);
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /**
     * To wrap a big line by words.
     * @param line the big line
     * @param wordSep the word separator
     * @param lineSep the new line to use when breaking into a new line
     * @param watermark a watermark to denote the size to cut after
     * @param wrapLongWords whether to wrap long words
     */
    public static String wrapWords(String line, String wordSep, String lineSep, int watermark, boolean wrapLongWords) {
        if (line == null) {
            return null;
        } else {
            if (lineSep == null) {
                lineSep = System.lineSeparator();
            }
            if (wordSep == null) {
                wordSep = "";
            }

            if (watermark < 1) {
                watermark = 1;
            }

            int inputLineLength = line.length();
            int offset = 0;
            StringBuilder sb = new StringBuilder(inputLineLength + 32);
            int currentLength = 0;
            while (offset < inputLineLength) {
                if (line.charAt(offset) == ' ') {
                    offset++;
                    continue;
                }
                int next = line.indexOf(' ', offset);
                if (next < 0) {
                    next = inputLineLength;
                    if (wrapLongWords && inputLineLength - offset > watermark) {
                        if (currentLength > 0) {
                            sb.append(wordSep);
                            currentLength += wordSep.length();
                        }
                        sb.append(line, offset, watermark - currentLength);
                        sb.append(lineSep);
                        offset += watermark - currentLength;
                    }
                }
                if (currentLength + (currentLength > 0 ? wordSep.length() : 0) + next - offset <= watermark) {
                    if (currentLength > 0) {
                        sb.append(wordSep);
                        currentLength += wordSep.length();
                    }
                    sb.append(line, offset, next);
                    currentLength += next - offset;
                    offset = next + 1;
                } else {
                    sb.append(lineSep);
                    sb.append(line, offset, next);
                    currentLength = next - offset;
                    offset = next + 1;
                }
            }
            /*
            while (inputLineLength - offset > watermark) {
                if (line.charAt(offset) == ' ') {
                    ++offset;
                } else {
                    int spaceToWrapAt = line.lastIndexOf(' ', watermark + offset);
                    int spaces = 0;
                    for (int i = offset; i < spaceToWrapAt; i++) {
                        spaces += line.charAt(i) == ' ' ? 1 : 0;
                    }
                    if (spaceToWrapAt >= offset) {
                        sb.append(line, offset, spaceToWrapAt);
                        sb.append(newLine);
                        offset = spaceToWrapAt + 1;
                    } else if (wrapLongWords) {
                        sb.append(line, offset, watermark + offset);
                        sb.append(newLine);
                        offset += watermark;
                    } else {
                        spaceToWrapAt = line.indexOf(' ', watermark + offset);
                        if (spaceToWrapAt >= 0) {
                            sb.append(line, offset, spaceToWrapAt);
                            sb.append(newLine);
                            offset = spaceToWrapAt + 1;
                        } else {
                            sb.append(line, offset, line.length());
                            offset = inputLineLength;
                        }
                    }
                }
            }

            sb.append(line, offset, line.length());
            */
            return sb.toString();
        }
    }

    /**
     * Returns the base class name, i.e. without package and generic related
     * information.
     *
     * @param className The class name which base class is to be computed.
     * @return the base class name, i.e. without package and generic related
     *         information.
     */
    public static String getClassShortName(String className) {
        if (className != null) {
            return className.replaceAll("<.*>", "").replaceAll(".*[.]([^.]+)", "$1");
        }
        return className;
    }
}
