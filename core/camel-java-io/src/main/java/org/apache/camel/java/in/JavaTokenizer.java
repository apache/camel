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
package org.apache.camel.java.in;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits Java source into the tokens the route parser needs: identifiers, literals and punctuation, with their line
 * numbers. Comments and whitespace are dropped. It is not a full Java lexer: it knows enough to find the fluent chains
 * of a RouteBuilder and to skip over what it does not read (lambda bodies, anonymous classes, generics).
 */
final class JavaTokenizer {

    enum Kind {
        IDENT,
        STRING,
        CHAR,
        NUMBER,
        PUNCT,
        EOF
    }

    /**
     * A token. For a string it holds the value with escapes and text blocks decoded; for other kinds the source text.
     */
    record Token(Kind kind, String text, int line) {

        boolean is(String punct) {
            return kind == Kind.PUNCT && text.equals(punct);
        }

        boolean isIdent(String name) {
            return kind == Kind.IDENT && text.equals(name);
        }
    }

    private final String src;
    private int pos;
    private int line = 1;

    private JavaTokenizer(String src) {
        this.src = src;
    }

    static List<Token> tokenize(String source) {
        return new JavaTokenizer(source).run();
    }

    private List<Token> run() {
        List<Token> tokens = new ArrayList<>();
        while (true) {
            skipSpaceAndComments();
            if (pos >= src.length()) {
                tokens.add(new Token(Kind.EOF, "", line));
                return tokens;
            }
            char c = src.charAt(pos);
            int startLine = line;
            if (src.startsWith("\"\"\"", pos)) {
                tokens.add(new Token(Kind.STRING, textBlock(), startLine));
            } else if (c == '"') {
                tokens.add(new Token(Kind.STRING, quoted('"'), startLine));
            } else if (c == '\'') {
                tokens.add(new Token(Kind.CHAR, quoted('\''), startLine));
            } else if (Character.isJavaIdentifierStart(c)) {
                int start = pos;
                while (pos < src.length() && Character.isJavaIdentifierPart(src.charAt(pos))) {
                    pos++;
                }
                tokens.add(new Token(Kind.IDENT, src.substring(start, pos), startLine));
            } else if (Character.isDigit(c) || c == '.' && pos + 1 < src.length() && Character.isDigit(src.charAt(pos + 1))) {
                tokens.add(new Token(Kind.NUMBER, number(), startLine));
            } else {
                tokens.add(new Token(Kind.PUNCT, punct(), startLine));
            }
        }
    }

    private void skipSpaceAndComments() {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == '\n') {
                line++;
                pos++;
            } else if (Character.isWhitespace(c)) {
                pos++;
            } else if (src.startsWith("//", pos)) {
                while (pos < src.length() && src.charAt(pos) != '\n') {
                    pos++;
                }
            } else if (src.startsWith("/*", pos)) {
                int end = src.indexOf("*/", pos + 2);
                end = end < 0 ? src.length() : end + 2;
                countLines(pos, end);
                pos = end;
            } else {
                return;
            }
        }
    }

    private String quoted(char quote) {
        StringBuilder sb = new StringBuilder();
        pos++;
        while (pos < src.length() && src.charAt(pos) != quote) {
            char c = src.charAt(pos);
            if (c == '\\' && pos + 1 < src.length()) {
                pos = escape(sb, pos + 1);
            } else {
                if (c == '\n') {
                    // unterminated literal: stop at the end of the line
                    break;
                }
                sb.append(c);
                pos++;
            }
        }
        pos = Math.min(src.length(), pos + 1);
        return sb.toString();
    }

    /** A text block: the content lines with their common indentation removed, as javac does. */
    private String textBlock() {
        int start = src.indexOf('\n', pos + 3);
        if (start < 0) {
            pos = src.length();
            return "";
        }
        int end = start + 1;
        while (end < src.length() && !src.startsWith("\"\"\"", end)) {
            end += src.charAt(end) == '\\' ? 2 : 1;
        }
        String raw = src.substring(start + 1, Math.min(end, src.length()));
        countLines(pos, Math.min(end + 3, src.length()));
        pos = Math.min(end + 3, src.length());
        String[] lines = raw.split("\n", -1);
        int indent = Integer.MAX_VALUE;
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i];
            // the closing delimiter's line counts for the indentation even when blank
            if (!l.isBlank() || i == lines.length - 1) {
                indent = Math.min(indent, l.length() - l.stripLeading().length());
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i];
            String stripped = l.length() >= indent ? l.substring(indent) : l.stripLeading();
            stripped = stripped.stripTrailing();
            if (i == lines.length - 1) {
                sb.append(stripped);
            } else {
                sb.append(stripped).append('\n');
            }
        }
        // decode escapes, a trailing backslash joins lines
        String text = sb.toString();
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                if (text.charAt(i + 1) == '\n') {
                    i++;
                    continue;
                }
                i = escapeIn(out, text, i + 1) - 1;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private int escape(StringBuilder sb, int at) {
        return escapeIn(sb, src, at);
    }

    /** Decodes the escape after a backslash at {@code at}; returns the position after it. */
    private static int escapeIn(StringBuilder sb, String s, int at) {
        char e = s.charAt(at);
        switch (e) {
            case 'n' -> sb.append('\n');
            case 't' -> sb.append('\t');
            case 'r' -> sb.append('\r');
            case 'b' -> sb.append('\b');
            case 'f' -> sb.append('\f');
            case 's' -> sb.append(' ');
            case '0', '1', '2', '3', '4', '5', '6', '7' -> {
                int end = at;
                while (end < s.length() && end < at + 3 && s.charAt(end) >= '0' && s.charAt(end) <= '7') {
                    end++;
                }
                sb.append((char) Integer.parseInt(s.substring(at, end), 8));
                return end;
            }
            case 'u' -> {
                int i = at;
                while (i < s.length() && s.charAt(i) == 'u') {
                    i++;
                }
                if (i + 4 <= s.length()) {
                    sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                    return i + 4;
                }
                return i;
            }
            default -> sb.append(e);
        }
        return at + 1;
    }

    private String number() {
        int start = pos;
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (Character.isLetterOrDigit(c) || c == '.' || c == '_'
                    || (c == '+' || c == '-') && (src.charAt(pos - 1) == 'e' || src.charAt(pos - 1) == 'E')) {
                pos++;
            } else {
                break;
            }
        }
        return src.substring(start, pos);
    }

    private static final String[] PUNCTS = { "->", "::", "...", "==", "!=", "<=", ">=", "&&", "||", "++", "--" };

    private String punct() {
        for (String p : PUNCTS) {
            if (src.startsWith(p, pos)) {
                pos += p.length();
                return p;
            }
        }
        return String.valueOf(src.charAt(pos++));
    }

    private void countLines(int from, int to) {
        for (int i = from; i < to; i++) {
            if (src.charAt(i) == '\n') {
                line++;
            }
        }
    }
}
