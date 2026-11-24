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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.camel.tooling.util.Strings.isNullOrEmpty;

public final class JavadocHelper {

    private static final String VALID_CHARS = ".,-='/\\!&%():;#${}?*+[]|";

    // what the sanitizer removes as a tag: everything between angle brackets
    private static final Pattern TAG = Pattern.compile("<.*?>");
    // an HTML start, end or empty element tag, such as <p>, </p>, <br/> or <a href="...">
    private static final Pattern HTML_TAG = Pattern.compile("</?([a-zA-Z][a-zA-Z0-9]*)(?:\\s[^<>]*)?/?>");
    // the HTML elements that are used in javadoc
    private static final Set<String> HTML_TAG_NAMES = Set.of(
            "a", "abbr", "b", "big", "blockquote", "br", "caption", "cite", "code", "dd", "del", "dfn", "div", "dl", "dt",
            "em", "h1", "h2", "h3", "h4", "h5", "h6", "hr", "i", "img", "ins", "kbd", "li", "ol", "p", "pre", "q", "s",
            "samp", "small", "span", "strike", "strong", "sub", "sup", "table", "tbody", "td", "tfoot", "th", "thead", "tr",
            "tt", "u", "ul", "var");

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

        String s = joinLines(javadoc, summary);
        // remove all XML tags
        s = TAG.matcher(s).replaceAll("");
        // remove {@link inlined javadoc links which is special handled
        s = s.replaceAll("\\{@link\\s\\w+\\s(\\w+)}", "$1");
        s = s.replaceAll("\\{@link\\s([\\w]+)}", "$1");
        // also remove the commonly mistake to do with @{link
        s = s.replaceAll("@\\{link\\s\\w+\\s(\\w+)}", "$1");
        s = s.replaceAll("@\\{link\\s([\\w]+)}", "$1");

        // Code and literal tags can contain spaces and punctuation, not just Java identifiers.
        s = s.replaceAll("\\{@(?:code|literal)\\s+([^{}]*)}", "$1");

        // remove all inlined javadoc links, eg such as {@link org.apache.camel.spi.Registry}
        // use #? to remove leading # in case its a local reference
        s = s.replaceAll("\\{@\\w+\\s#?([\\w.#(\\d,)]+)}", "$1");

        // create a new line
        StringBuilder cb = new StringBuilder(s.length() + 16);
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
     * Finds the text with angle brackets that {@link #sanitizeDescription(String, boolean)} removes from the javadoc
     * and that is not an HTML tag: the text between angle brackets, such as a placeholder ({@code <name>}) or a generic
     * type ({@code List<String>}), and a lone angle bracket, such as in an operator ({@code <=}). The sanitizer removes
     * everything between angle brackets, and then every angle bracket that is left, so this text would be lost.
     *
     * @param  javadoc the javadoc
     * @param  summary whether only the summary is used, as in {@link #sanitizeDescription(String, boolean)}
     * @return         the removed text that is not an HTML tag, or an empty list. The text between angle brackets comes
     *                 first, then each lone angle bracket with the text around it.
     */
    public static List<String> findRemovedAngleBracketText(String javadoc, boolean summary) {
        if (isNullOrEmpty(javadoc)) {
            return List.of();
        }
        List<String> answer = new ArrayList<>();
        Matcher matcher = TAG.matcher(joinLines(javadoc, summary));
        StringBuilder rest = new StringBuilder();
        while (matcher.find()) {
            String tag = matcher.group();
            if (!isHtmlTag(tag)) {
                answer.add(tag);
            }
            matcher.appendReplacement(rest, " ");
        }
        matcher.appendTail(rest);
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (c == '<' || c == '>') {
                answer.add(rest.substring(Math.max(0, i - 10), Math.min(rest.length(), i + 11)).trim());
            }
        }
        return answer;
    }

    private static boolean isHtmlTag(String tag) {
        if (tag.startsWith("<!--") && tag.endsWith("-->")) {
            return true;
        }
        Matcher matcher = HTML_TAG.matcher(tag);
        return matcher.matches() && HTML_TAG_NAMES.contains(matcher.group(1).toLowerCase(Locale.ROOT));
    }

    private static String joinLines(String javadoc, boolean summary) {
        StringBuilder sb = new StringBuilder(javadoc.length());

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

        return sb.toString();
    }

    /**
     * Encodes the text into safe XML by replacing &lt; $gt; and &amp; with XML tokens
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
