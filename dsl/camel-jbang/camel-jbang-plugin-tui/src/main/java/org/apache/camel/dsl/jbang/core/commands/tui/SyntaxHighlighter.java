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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import org.apache.camel.util.FileUtil;

class SyntaxHighlighter {

    enum Language {
        JAVA,
        YAML,
        XML,
        PLAIN
    }

    // Java patterns (ordered by priority — comments first)
    private static final Pattern JAVA_LINE_COMMENT = Pattern.compile("//.*");
    private static final Pattern JAVA_STRING = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"");
    private static final Pattern JAVA_ANNOTATION = Pattern.compile("@\\w+");
    private static final Pattern JAVA_MODIFIER = Pattern.compile(
            "\\b(abstract|class|extends|final|implements|import|instanceof|interface|native|package|private|protected|public|static|strictfp|super|synchronized|throws|volatile|enum|record|sealed|permits|non-sealed)\\b");
    private static final Pattern JAVA_KEYWORD = Pattern.compile(
            "\\b(break|case|catch|continue|default|do|else|finally|for|if|return|switch|throw|try|while|yield|var)\\b");
    private static final Pattern JAVA_TYPE = Pattern.compile(
            "\\b(boolean|byte|char|double|float|int|long|new|short|this|transient|void)\\b");
    private static final Pattern JAVA_BOOLEAN_NULL = Pattern.compile("\\b(true|false|null)\\b");
    private static final Pattern JAVA_NUMBER = Pattern.compile("\\b\\d+\\.?\\d*[fFdDlL]?\\b");

    // YAML patterns
    private static final Pattern YAML_COMMENT = Pattern.compile("(^|\\s)#.*$");
    private static final Pattern YAML_KEY = Pattern.compile("^(\\s*-?\\s*)([\\w./${}\\-]+)\\s*:");
    private static final Pattern YAML_BOOLEAN_NULL = Pattern.compile(":\\s+(true|false|null)\\s*$");
    private static final Pattern YAML_NUMBER = Pattern.compile(":\\s+(\\d+\\.?\\d*)\\s*$");
    private static final Pattern YAML_STRING_VALUE = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"|'[^']*'");

    // XML patterns
    private static final Pattern XML_COMMENT = Pattern.compile("<!--.*?-->");
    private static final Pattern XML_OPEN_TAG = Pattern.compile("</?[\\w:.-]+");
    private static final Pattern XML_CLOSE_BRACKET = Pattern.compile("/?>|>");
    private static final Pattern XML_ATTR_VALUE = Pattern.compile("=\"[^\"]*\"");
    private static final Pattern XML_ATTR_NAME = Pattern.compile("\\s([\\w:.-]+)=");
    private static final Pattern XML_ENTITY = Pattern.compile("&[^;]+;");

    // Java styles
    private static final Style JAVA_COMMENT_STYLE = Style.EMPTY.fg(Color.LIGHT_BLUE);
    private static final Style JAVA_STRING_STYLE = Style.EMPTY.fg(Color.RED);
    private static final Style JAVA_ANNOTATION_STYLE = Style.EMPTY.fg(Color.MAGENTA);
    private static final Style JAVA_MODIFIER_STYLE = Style.EMPTY.fg(Color.CYAN);
    private static final Style JAVA_KEYWORD_STYLE = Style.EMPTY.fg(Color.RED);
    private static final Style JAVA_TYPE_STYLE = Style.EMPTY.fg(Color.GREEN);
    private static final Style JAVA_BOOLEAN_STYLE = Style.EMPTY.fg(Color.YELLOW);
    private static final Style JAVA_NUMBER_STYLE = Style.EMPTY.fg(Color.YELLOW);

    // YAML styles
    private static final Style YAML_COMMENT_STYLE = Style.EMPTY.fg(Color.LIGHT_BLUE);
    private static final Style YAML_KEY_STYLE = Style.EMPTY.fg(Color.RED);
    private static final Style YAML_VALUE_STYLE = Style.EMPTY.fg(Color.GREEN);
    private static final Style YAML_SPECIAL_STYLE = Style.EMPTY.fg(Color.YELLOW);
    private static final Style YAML_SEPARATOR_STYLE = Style.EMPTY.fg(Color.WHITE).bold();

    // XML styles
    private static final Style XML_COMMENT_STYLE = Style.EMPTY.fg(Color.YELLOW);
    private static final Style XML_TAG_STYLE = Style.EMPTY.fg(Color.CYAN);
    private static final Style XML_ATTR_NAME_STYLE = Style.EMPTY.fg(Color.MAGENTA);
    private static final Style XML_ATTR_VALUE_STYLE = Style.EMPTY.fg(Color.GREEN);
    private static final Style XML_ENTITY_STYLE = Style.EMPTY.fg(Color.RED);

    private SyntaxHighlighter() {
    }

    static Language detectLanguage(String filename) {
        if (filename == null || filename.isEmpty()) {
            return Language.PLAIN;
        }
        // Strip line number suffixes (e.g., "MyRoute.java:42")
        String name = filename;
        int colon = name.lastIndexOf(':');
        if (colon > 0) {
            String after = name.substring(colon + 1);
            if (!after.isEmpty() && after.chars().allMatch(Character::isDigit)) {
                name = name.substring(0, colon);
            }
        }
        String ext = FileUtil.onlyExt(name);
        if (ext == null) {
            return Language.PLAIN;
        }
        ext = ext.toLowerCase();
        return switch (ext) {
            case "java" -> Language.JAVA;
            case "yaml", "yml", "camel.yaml", "camel.yml" -> Language.YAML;
            case "xml", "camel.xml" -> Language.XML;
            default -> Language.PLAIN;
        };
    }

    static Line highlightLine(String text, Language lang) {
        if (text == null || text.isEmpty() || lang == Language.PLAIN) {
            return Line.from(List.of(Span.raw(text != null ? text : "")));
        }

        return switch (lang) {
            case JAVA -> highlightJava(text);
            case YAML -> highlightYaml(text);
            case XML -> highlightXml(text);
            default -> Line.from(List.of(Span.raw(text)));
        };
    }

    private static Line highlightJava(String text) {
        int len = text.length();
        Style[] charStyles = new Style[len];

        // Priority order: comments > strings > annotations > keywords > numbers
        applyPattern(charStyles, text, JAVA_LINE_COMMENT, JAVA_COMMENT_STYLE);
        applyPattern(charStyles, text, JAVA_STRING, JAVA_STRING_STYLE);
        applyPattern(charStyles, text, JAVA_ANNOTATION, JAVA_ANNOTATION_STYLE);
        applyPattern(charStyles, text, JAVA_MODIFIER, JAVA_MODIFIER_STYLE);
        applyPattern(charStyles, text, JAVA_KEYWORD, JAVA_KEYWORD_STYLE);
        applyPattern(charStyles, text, JAVA_TYPE, JAVA_TYPE_STYLE);
        applyPattern(charStyles, text, JAVA_BOOLEAN_NULL, JAVA_BOOLEAN_STYLE);
        applyPattern(charStyles, text, JAVA_NUMBER, JAVA_NUMBER_STYLE);

        return buildLine(text, charStyles);
    }

    private static Line highlightYaml(String text) {
        int len = text.length();
        Style[] charStyles = new Style[len];

        // Comments have highest priority
        applyPattern(charStyles, text, YAML_COMMENT, YAML_COMMENT_STYLE);

        // Key portion (before colon)
        Matcher keyMatcher = YAML_KEY.matcher(text);
        if (keyMatcher.find()) {
            int keyStart = keyMatcher.start(2);
            int keyEnd = keyMatcher.end(2);
            for (int i = keyStart; i < keyEnd && i < len; i++) {
                if (charStyles[i] == null) {
                    charStyles[i] = YAML_KEY_STYLE;
                }
            }
            // Colon separator
            int colonIdx = text.indexOf(':', keyEnd);
            if (colonIdx >= 0 && colonIdx < len && charStyles[colonIdx] == null) {
                charStyles[colonIdx] = YAML_SEPARATOR_STYLE;
            }
        }

        // String values
        applyPattern(charStyles, text, YAML_STRING_VALUE, YAML_VALUE_STYLE);

        // Special values (boolean, null, numbers) after colon
        applyPatternGroup(charStyles, text, YAML_BOOLEAN_NULL, 1, YAML_SPECIAL_STYLE);
        applyPatternGroup(charStyles, text, YAML_NUMBER, 1, YAML_SPECIAL_STYLE);

        // List markers
        Matcher listMarker = Pattern.compile("^(\\s*)(-)(\\s)").matcher(text);
        if (listMarker.find()) {
            int dashIdx = listMarker.start(2);
            if (dashIdx < len && charStyles[dashIdx] == null) {
                charStyles[dashIdx] = YAML_SEPARATOR_STYLE;
            }
        }

        // Value text (after colon+space, non-special, non-quoted)
        int colonPos = text.indexOf(':');
        if (colonPos >= 0 && colonPos + 1 < len) {
            int valueStart = colonPos + 1;
            while (valueStart < len && text.charAt(valueStart) == ' ') {
                valueStart++;
            }
            if (valueStart < len) {
                boolean hasSpecial = false;
                for (int i = valueStart; i < len; i++) {
                    if (charStyles[i] != null) {
                        hasSpecial = true;
                        break;
                    }
                }
                if (!hasSpecial) {
                    for (int i = valueStart; i < len; i++) {
                        charStyles[i] = YAML_VALUE_STYLE;
                    }
                }
            }
        }

        return buildLine(text, charStyles);
    }

    private static Line highlightXml(String text) {
        int len = text.length();
        Style[] charStyles = new Style[len];

        // Comments highest priority
        applyPattern(charStyles, text, XML_COMMENT, XML_COMMENT_STYLE);

        // Attribute values (before tag names so tags don't override)
        applyPattern(charStyles, text, XML_ATTR_VALUE, XML_ATTR_VALUE_STYLE);

        // Attribute names
        Matcher attrMatcher = XML_ATTR_NAME.matcher(text);
        while (attrMatcher.find()) {
            int start = attrMatcher.start(1);
            int end = attrMatcher.end(1);
            for (int i = start; i < end; i++) {
                if (charStyles[i] == null) {
                    charStyles[i] = XML_ATTR_NAME_STYLE;
                }
            }
        }

        // Tag names
        applyPattern(charStyles, text, XML_OPEN_TAG, XML_TAG_STYLE);
        applyPattern(charStyles, text, XML_CLOSE_BRACKET, XML_TAG_STYLE);

        // Entity references
        applyPattern(charStyles, text, XML_ENTITY, XML_ENTITY_STYLE);

        return buildLine(text, charStyles);
    }

    private static void applyPattern(Style[] charStyles, String text, Pattern pattern, Style style) {
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            for (int i = m.start(); i < m.end(); i++) {
                if (charStyles[i] == null) {
                    charStyles[i] = style;
                }
            }
        }
    }

    private static void applyPatternGroup(Style[] charStyles, String text, Pattern pattern, int group, Style style) {
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            for (int i = m.start(group); i < m.end(group); i++) {
                if (charStyles[i] == null) {
                    charStyles[i] = style;
                }
            }
        }
    }

    private static Line buildLine(String text, Style[] charStyles) {
        List<Span> spans = new ArrayList<>();
        int len = text.length();
        int i = 0;

        while (i < len) {
            Style current = charStyles[i];
            int start = i;
            while (i < len && charStyles[i] == current) {
                i++;
            }
            String segment = text.substring(start, i);
            if (current != null) {
                spans.add(Span.styled(segment, current));
            } else {
                spans.add(Span.raw(segment));
            }
        }

        return spans.isEmpty() ? Line.from(List.of(Span.raw(text))) : Line.from(spans);
    }
}
