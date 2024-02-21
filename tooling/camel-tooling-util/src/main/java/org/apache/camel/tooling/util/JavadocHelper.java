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
package org.apache.camel.tooling.util;

import static org.apache.camel.tooling.util.Strings.isNullOrEmpty;

public final class JavadocHelper {

    private static final String VALID_CHARS = ".,-='/\\!&%():;#${}";

    private JavadocHelper() {
    }

    /**
     * Sanitizes the javadoc to removed invalid characters so it can be used as json description
     *
     * @param  javadoc the javadoc
     * @return         the text that is valid as json
     */
    public static String sanitizeDescription(String javadoc, boolean summary) {
        if (isNullOrEmpty(javadoc)) {
            return null;
        }

        // lets just use what java accepts as identifiers
        StringBuilder sb = new StringBuilder();

        // split into lines
        String[] lines = javadoc.split("\n");

        boolean first = true;
        for (String line : lines) {
            line = line.trim();

            if (line.startsWith("**")) {
                continue;
            }
            // remove leading javadoc *
            if (line.startsWith("*")) {
                line = line.substring(1);
                line = line.trim();
            }

            // terminate if we reach @param, @return or @deprecated as we only want the javadoc summary
            if (line.startsWith("@param") || line.startsWith("@return") || line.startsWith("@deprecated")) {
                break;
            }

            // skip lines that are javadoc references
            if (line.startsWith("@")) {
                continue;
            }

            // we are starting from a new line, so add a whitespace
            if (!first) {
                sb.append(' ');
            }

            // append data
            String s = line.trim();
            sb.append(s);

            boolean empty = isNullOrEmpty(s);
            boolean endWithDot = s.endsWith(".");
            boolean haveText = !sb.isEmpty();

            if (haveText && summary && (empty || endWithDot)) {
                // if we only want a summary, then skip at first empty line we encounter, or if the sentence ends with a dot
                break;
            }

            first = false;
        }

        String s = sb.toString();
        // remove all XML tags
        s = s.replaceAll("<.*?>", "");
        // remove {@link inlined javadoc links which is special handled
        s = s.replaceAll("\\{@link\\s\\w+\\s(\\w+)}", "$1");
        s = s.replaceAll("\\{@link\\s([\\w]+)}", "$1");
        // also remove the commonly mistake to do with @{link
        s = s.replaceAll("@\\{link\\s\\w+\\s(\\w+)}", "$1");
        s = s.replaceAll("@\\{link\\s([\\w]+)}", "$1");

        // remove all inlined javadoc links, eg such as {@link org.apache.camel.spi.Registry}
        // use #? to remove leading # in case its a local reference
        s = s.replaceAll("\\{@\\w+\\s#?([\\w.#(\\d,)]+)}", "$1");

        // create a new line
        StringBuilder cb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (Character.isJavaIdentifierPart(c) || VALID_CHARS.indexOf(c) != -1) {
                cb.append(c);
            } else if (Character.isWhitespace(c)) {
                // always use space as whitespace, also for line feeds etc
                cb.append(' ');
            }
        }
        s = cb.toString();

        // remove double whitespaces, and trim
        s = s.replaceAll("\\s+", " ");
        // unescape http links
        s = s.replaceAll("\\\\(http:|https:)", "$1");
        return s.trim();
    }

    /**
     * Encodes the text into safe XML by replacing < > and & with XML tokens
     *
     * @param  text the text
     * @return      the encoded text
     */
    public static String xmlEncode(String text) {
        if (text == null) {
            return "";
        }
        // must replace amp first, so we dont replace &lt; to amp later
        text = text.replace("&", "&amp;");
        text = text.replace("\"", "&quot;");
        text = text.replace("<", "&lt;");
        text = text.replace(">", "&gt;");
        return text;
    }

}
