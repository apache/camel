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
import java.util.Objects;
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
        PROPERTIES,
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

    // Monokai color palette (dark themes)
    static final Color MONOKAI_COMMENT = Color.rgb(117, 113, 94);
    static final Color MONOKAI_STRING = Color.rgb(230, 219, 116);
    static final Color MONOKAI_KEYWORD = Color.rgb(249, 38, 114);
    static final Color MONOKAI_FUNCTION = Color.rgb(166, 226, 46);
    static final Color MONOKAI_TYPE = Color.rgb(102, 217, 239);
    static final Color MONOKAI_CONSTANT = Color.rgb(174, 129, 255);
    static final Color MONOKAI_TEXT = Color.rgb(248, 248, 242);

    // Light color palette (readable on light backgrounds)
    private static final Color LIGHT_COMMENT = Color.rgb(106, 115, 125);
    private static final Color LIGHT_STRING = Color.rgb(3, 47, 98);
    private static final Color LIGHT_KEYWORD = Color.rgb(215, 58, 73);
    private static final Color LIGHT_FUNCTION = Color.rgb(0, 92, 197);
    private static final Color LIGHT_TYPE = Color.rgb(0, 92, 197);
    private static final Color LIGHT_CONSTANT = Color.rgb(111, 66, 193);
    private static final Color LIGHT_TEXT = Color.rgb(36, 41, 46);

    private static Color comment() {
        return Theme.isDark() ? MONOKAI_COMMENT : LIGHT_COMMENT;
    }

    private static Color string() {
        return Theme.isDark() ? MONOKAI_STRING : LIGHT_STRING;
    }

    private static Color keyword() {
        return Theme.isDark() ? MONOKAI_KEYWORD : LIGHT_KEYWORD;
    }

    private static Color function() {
        return Theme.isDark() ? MONOKAI_FUNCTION : LIGHT_FUNCTION;
    }

    private static Color type() {
        return Theme.isDark() ? MONOKAI_TYPE : LIGHT_TYPE;
    }

    private static Color constant() {
        return Theme.isDark() ? MONOKAI_CONSTANT : LIGHT_CONSTANT;
    }

    private static Color text() {
        return Theme.isDark() ? MONOKAI_TEXT : LIGHT_TEXT;
    }

    // Java styles
    private static Style javaComment() {
        return Style.EMPTY.fg(comment());
    }

    private static Style javaString() {
        return Style.EMPTY.fg(string());
    }

    private static Style javaAnnotation() {
        return Style.EMPTY.fg(function());
    }

    private static Style javaModifier() {
        return Style.EMPTY.fg(keyword());
    }

    private static Style javaKeyword() {
        return Style.EMPTY.fg(keyword());
    }

    private static Style javaType() {
        return Style.EMPTY.fg(type());
    }

    private static Style javaBoolean() {
        return Style.EMPTY.fg(constant());
    }

    private static Style javaNumber() {
        return Style.EMPTY.fg(constant());
    }

    // YAML styles
    private static Style yamlComment() {
        return Style.EMPTY.fg(comment());
    }

    private static Style yamlKey() {
        return Style.EMPTY.fg(keyword());
    }

    private static Style yamlValue() {
        return Style.EMPTY.fg(string());
    }

    private static Style yamlSpecial() {
        return Style.EMPTY.fg(constant());
    }

    private static Style yamlSeparator() {
        return Style.EMPTY.fg(text()).bold();
    }

    // XML styles
    private static Style xmlComment() {
        return Style.EMPTY.fg(comment());
    }

    private static Style xmlTag() {
        return Style.EMPTY.fg(keyword());
    }

    private static Style xmlAttrName() {
        return Style.EMPTY.fg(function());
    }

    private static Style xmlAttrValue() {
        return Style.EMPTY.fg(string());
    }

    private static Style xmlEntity() {
        return Style.EMPTY.fg(constant());
    }

    // Properties styles
    private static Style propsComment() {
        return Style.EMPTY.fg(comment());
    }

    private static Style propsKey() {
        return Style.EMPTY.fg(keyword());
    }

    private static Style propsSeparator() {
        return Style.EMPTY.fg(text()).bold();
    }

    private static Style propsValue() {
        return Style.EMPTY.fg(string());
    }

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
            case "properties" -> Language.PROPERTIES;
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
            case PROPERTIES -> highlightProperties(text);
            default -> Line.from(List.of(Span.raw(text)));
        };
    }

    private static Line highlightJava(String text) {
        int len = text.length();
        Style[] charStyles = new Style[len];

        // Priority order: comments > strings > annotations > keywords > numbers
        applyPattern(charStyles, text, JAVA_LINE_COMMENT, javaComment());
        applyPattern(charStyles, text, JAVA_STRING, javaString());
        applyPattern(charStyles, text, JAVA_ANNOTATION, javaAnnotation());
        applyPattern(charStyles, text, JAVA_MODIFIER, javaModifier());
        applyPattern(charStyles, text, JAVA_KEYWORD, javaKeyword());
        applyPattern(charStyles, text, JAVA_TYPE, javaType());
        applyPattern(charStyles, text, JAVA_BOOLEAN_NULL, javaBoolean());
        applyPattern(charStyles, text, JAVA_NUMBER, javaNumber());

        return buildLine(text, charStyles);
    }

    private static Line highlightYaml(String text) {
        int len = text.length();
        Style[] charStyles = new Style[len];

        // Comments have highest priority
        applyPattern(charStyles, text, YAML_COMMENT, yamlComment());

        // Key portion (before colon)
        Style ykStyle = yamlKey();
        Style ysSep = yamlSeparator();
        Matcher keyMatcher = YAML_KEY.matcher(text);
        if (keyMatcher.find()) {
            int keyStart = keyMatcher.start(2);
            int keyEnd = keyMatcher.end(2);
            for (int i = keyStart; i < keyEnd && i < len; i++) {
                if (charStyles[i] == null) {
                    charStyles[i] = ykStyle;
                }
            }
            // Colon separator
            int colonIdx = text.indexOf(':', keyEnd);
            if (colonIdx >= 0 && colonIdx < len && charStyles[colonIdx] == null) {
                charStyles[colonIdx] = ysSep;
            }
        }

        // String values
        applyPattern(charStyles, text, YAML_STRING_VALUE, yamlValue());

        // Special values (boolean, null, numbers) after colon
        Style ysSpecial = yamlSpecial();
        applyPatternGroup(charStyles, text, YAML_BOOLEAN_NULL, 1, ysSpecial);
        applyPatternGroup(charStyles, text, YAML_NUMBER, 1, ysSpecial);

        // List markers
        Matcher listMarker = Pattern.compile("^(\\s*)(-)(\\s)").matcher(text);
        if (listMarker.find()) {
            int dashIdx = listMarker.start(2);
            if (dashIdx < len && charStyles[dashIdx] == null) {
                charStyles[dashIdx] = ysSep;
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
                        charStyles[i] = yamlValue();
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
        applyPattern(charStyles, text, XML_COMMENT, xmlComment());

        // Attribute values (before tag names so tags don't override)
        applyPattern(charStyles, text, XML_ATTR_VALUE, xmlAttrValue());

        // Attribute names
        Style xan = xmlAttrName();
        Matcher attrMatcher = XML_ATTR_NAME.matcher(text);
        while (attrMatcher.find()) {
            int start = attrMatcher.start(1);
            int end = attrMatcher.end(1);
            for (int i = start; i < end; i++) {
                if (charStyles[i] == null) {
                    charStyles[i] = xan;
                }
            }
        }

        // Tag names
        Style xt = xmlTag();
        applyPattern(charStyles, text, XML_OPEN_TAG, xt);
        applyPattern(charStyles, text, XML_CLOSE_BRACKET, xt);

        // Entity references
        applyPattern(charStyles, text, XML_ENTITY, xmlEntity());

        return buildLine(text, charStyles);
    }

    private static Line highlightProperties(String text) {
        int len = text.length();
        Style[] charStyles = new Style[len];

        // skip leading whitespace (left unstyled, like the indentation)
        int start = 0;
        while (start < len && Character.isWhitespace(text.charAt(start))) {
            start++;
        }

        // blank line
        if (start >= len) {
            return buildLine(text, charStyles);
        }

        // comment line: starts with # or !
        char first = text.charAt(start);
        if (first == '#' || first == '!') {
            for (int i = start; i < len; i++) {
                charStyles[i] = propsComment();
            }
            return buildLine(text, charStyles);
        }

        // key ends at the first unescaped '=', ':' or whitespace (Properties separators)
        int keyEnd = -1;
        for (int i = start; i < len; i++) {
            char c = text.charAt(i);
            if (c == '\\') {
                i++; // skip the escaped character (e.g. \= \: \ )
                continue;
            }
            if (c == '=' || c == ':' || Character.isWhitespace(c)) {
                keyEnd = i;
                break;
            }
        }

        // key with no separator and no value (e.g. a lone "enabled")
        if (keyEnd < 0) {
            for (int i = start; i < len; i++) {
                charStyles[i] = propsKey();
            }
            return buildLine(text, charStyles);
        }

        // key
        for (int i = start; i < keyEnd; i++) {
            charStyles[i] = propsKey();
        }

        // an explicit '=' or ':' separator may follow optional whitespace
        int i = keyEnd;
        while (i < len && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        if (i < len && (text.charAt(i) == '=' || text.charAt(i) == ':')) {
            charStyles[i] = propsSeparator();
            i++;
        }

        // value (leading whitespace skipped, left unstyled)
        while (i < len && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        for (; i < len; i++) {
            charStyles[i] = propsValue();
        }

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
            while (i < len && Objects.equals(charStyles[i], current)) {
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
