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
package org.apache.camel.component.dataweave;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Tokenizer for DataWeave 2.0 scripts.
 */
public class DataWeaveLexer {

    public enum TokenType {
        STRING, // the raw text between the quotes, with escape sequences and $(...) interpolations as in the source
        NUMBER,
        BOOLEAN,
        NULL_LIT,
        IDENTIFIER,
        REGEX, // /pattern/ (the pattern without the slashes)
        TEMPORAL, // |2020-01-31|, |P1D| (the value without the bars)
        PLUS,
        MINUS,
        STAR,
        SLASH,
        PLUSPLUS,
        MINUSMINUS,
        ASSIGN,
        EQ,
        NEQ,
        SIMILAR, // ~=
        GT,
        GE,
        LT,
        LE,
        AND,
        OR,
        NOT,
        DOT,
        COMMA,
        COLON,
        DOUBLE_COLON,
        ARROW,
        SEMICOLON,
        LPAREN,
        RPAREN,
        LBRACE,
        RBRACE,
        LBRACKET,
        RBRACKET,
        DOLLAR, // $
        DOLLAR_DOLLAR, // $$
        DOLLAR_DOLLAR_DOLLAR, // $$$
        AT, // @
        QUESTION, // ?
        HASH, // #  (XML namespace prefix)
        CARET, // ^  (metadata selector)
        TILDE, // ~
        PIPE, // |  (type union separator)
        HEADER_SEPARATOR, // ---
        PERCENT, // %
        EOF
    }

    public record Token(TokenType type, String value, int line, int col) {
        @Override
        public String toString() {
            return type + "(" + value + ")@" + line + ":" + col;
        }
    }

    // A '/' after one of these identifiers starts a regular expression literal rather than a division
    private static final Set<String> REGEX_PREFIX_IDENTIFIERS = Set.of(
            "replace", "contains", "matches", "splitBy", "scan", "find", "match", "case", "startsWith", "endsWith");

    private final String input;
    private final List<Token> tokens = new ArrayList<>();
    private int pos;
    private int line;
    private int col;

    public DataWeaveLexer(String input) {
        this.input = input;
        this.pos = 0;
        this.line = 1;
        this.col = 1;
    }

    public List<Token> tokenize() {
        while (pos < input.length()) {
            skipWhitespaceAndComments();
            if (pos >= input.length()) {
                break;
            }
            tokens.add(readToken());
        }
        tokens.add(new Token(TokenType.EOF, "", line, col));
        return tokens;
    }

    private void skipWhitespaceAndComments() {
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (Character.isWhitespace(c)) {
                advance();
            } else if (c == '/' && peek(1) == '/') {
                while (pos < input.length() && input.charAt(pos) != '\n') {
                    advance();
                }
            } else if (c == '/' && peek(1) == '*') {
                int startLine = line;
                int startCol = col;
                advance(); // /
                advance(); // *
                while (pos < input.length() && !(input.charAt(pos) == '*' && peek(1) == '/')) {
                    advance();
                }
                if (pos >= input.length()) {
                    throw error("unterminated comment", startLine, startCol);
                }
                advance(); // *
                advance(); // /
            } else {
                break;
            }
        }
    }

    private Token readToken() {
        int startLine = line;
        int startCol = col;
        char c = input.charAt(pos);

        if (c == '-' && peek(1) == '-' && peek(2) == '-' && peek(3) != '-') {
            advance();
            advance();
            advance();
            return new Token(TokenType.HEADER_SEPARATOR, "---", startLine, startCol);
        }
        if (c == '"' || c == '\'' || c == '`') {
            return readString(c, startLine, startCol);
        }
        if (Character.isDigit(c)
                || (c == '-' && Character.isDigit(peek(1)) && !isPreviousTokenValueLike())) {
            return readNumber(startLine, startCol);
        }
        if (Character.isLetter(c) || c == '_') {
            return readIdentifier(startLine, startCol);
        }
        if (c == '/' && isRegexStart()) {
            return readRegex(startLine, startCol);
        }
        if (c == '|' && isTemporalStart()) {
            return readTemporal(startLine, startCol);
        }
        return readOperator(startLine, startCol);
    }

    private boolean isPreviousTokenValueLike() {
        if (tokens.isEmpty()) {
            return false;
        }
        TokenType type = tokens.get(tokens.size() - 1).type();
        return type == TokenType.IDENTIFIER || type == TokenType.NUMBER || type == TokenType.STRING
                || type == TokenType.RPAREN || type == TokenType.RBRACKET || type == TokenType.RBRACE
                || type == TokenType.BOOLEAN || type == TokenType.NULL_LIT || type == TokenType.DOLLAR
                || type == TokenType.DOLLAR_DOLLAR || type == TokenType.DOLLAR_DOLLAR_DOLLAR
                || type == TokenType.TEMPORAL;
    }

    private boolean isRegexStart() {
        if (tokens.isEmpty()) {
            return true;
        }
        Token previous = tokens.get(tokens.size() - 1);
        if (previous.type() == TokenType.IDENTIFIER) {
            return REGEX_PREFIX_IDENTIFIERS.contains(previous.value());
        }
        return !isPreviousTokenValueLike();
    }

    private boolean isTemporalStart() {
        // |2020-01-31|, |2020-01-31T10:00:00Z|, |10:00:00|, |P1D|, |PT1H| -- a type union is "A | B" instead
        char next = peek(1);
        if (!(Character.isDigit(next) || next == 'P' || next == '-')) {
            return false;
        }
        for (int i = pos + 1; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (ch == '|') {
                return i > pos + 1;
            }
            if (!(Character.isLetterOrDigit(ch) || ch == '-' || ch == ':' || ch == '.' || ch == '+')) {
                return false;
            }
        }
        return false;
    }

    private Token readString(char quote, int startLine, int startCol) {
        advance(); // opening quote
        StringBuilder sb = new StringBuilder();
        while (pos < input.length() && input.charAt(pos) != quote) {
            char ch = input.charAt(pos);
            if (ch == '\\' && pos + 1 < input.length()) {
                sb.append(ch);
                advance();
                sb.append(input.charAt(pos));
                advance();
            } else if (ch == '$' && peek(1) == '(') {
                readInterpolation(sb);
            } else {
                sb.append(ch);
                advance();
            }
        }
        if (pos >= input.length()) {
            throw error("unterminated string", startLine, startCol);
        }
        advance(); // closing quote
        return new Token(TokenType.STRING, sb.toString(), startLine, startCol);
    }

    // Copies $( ... ) verbatim, including nested parentheses and strings: "Hello $(upper("x"))"
    private void readInterpolation(StringBuilder sb) {
        int startLine = line;
        int startCol = col;
        sb.append('$');
        advance();
        int depth = 0;
        while (pos < input.length()) {
            char ch = input.charAt(pos);
            if (ch == '"' || ch == '\'') {
                char quote = ch;
                sb.append(ch);
                advance();
                while (pos < input.length() && input.charAt(pos) != quote) {
                    if (input.charAt(pos) == '\\' && pos + 1 < input.length()) {
                        sb.append(input.charAt(pos));
                        advance();
                    }
                    sb.append(input.charAt(pos));
                    advance();
                }
                if (pos < input.length()) {
                    sb.append(input.charAt(pos));
                    advance();
                }
                continue;
            }
            sb.append(ch);
            advance();
            if (ch == '(') {
                depth++;
            } else if (ch == ')') {
                depth--;
                if (depth == 0) {
                    return;
                }
            }
        }
        throw error("unterminated string interpolation", startLine, startCol);
    }

    private Token readNumber(int startLine, int startCol) {
        StringBuilder sb = new StringBuilder();
        if (input.charAt(pos) == '-') {
            sb.append('-');
            advance();
        }
        readDigits(sb);
        if (pos < input.length() && input.charAt(pos) == '.' && Character.isDigit(peek(1))) {
            sb.append('.');
            advance();
            readDigits(sb);
        }
        if (pos < input.length() && (input.charAt(pos) == 'e' || input.charAt(pos) == 'E')
                && (Character.isDigit(peek(1)) || (peek(1) == '-' || peek(1) == '+') && Character.isDigit(peek(2)))) {
            sb.append(input.charAt(pos));
            advance();
            if (input.charAt(pos) == '-' || input.charAt(pos) == '+') {
                sb.append(input.charAt(pos));
                advance();
            }
            readDigits(sb);
        }
        return new Token(TokenType.NUMBER, sb.toString(), startLine, startCol);
    }

    private void readDigits(StringBuilder sb) {
        while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
            sb.append(input.charAt(pos));
            advance();
        }
    }

    private Token readIdentifier(int startLine, int startCol) {
        StringBuilder sb = new StringBuilder();
        while (pos < input.length() && (Character.isLetterOrDigit(input.charAt(pos)) || input.charAt(pos) == '_')) {
            sb.append(input.charAt(pos));
            advance();
        }
        String word = sb.toString();
        return switch (word) {
            case "true", "false" -> new Token(TokenType.BOOLEAN, word, startLine, startCol);
            case "null" -> new Token(TokenType.NULL_LIT, word, startLine, startCol);
            case "and" -> new Token(TokenType.AND, word, startLine, startCol);
            case "or" -> new Token(TokenType.OR, word, startLine, startCol);
            case "not" -> new Token(TokenType.NOT, word, startLine, startCol);
            default -> new Token(TokenType.IDENTIFIER, word, startLine, startCol);
        };
    }

    private Token readRegex(int startLine, int startCol) {
        advance(); // opening /
        StringBuilder sb = new StringBuilder();
        while (pos < input.length() && input.charAt(pos) != '/') {
            char ch = input.charAt(pos);
            if (ch == '\n') {
                throw error("unterminated regular expression", startLine, startCol);
            }
            if (ch == '\\' && peek(1) == '/') {
                advance(); // an escaped slash is a plain slash in the pattern
            } else if (ch == '\\' && pos + 1 < input.length()) {
                sb.append(ch);
                advance();
            }
            sb.append(input.charAt(pos));
            advance();
        }
        if (pos >= input.length()) {
            throw error("unterminated regular expression", startLine, startCol);
        }
        advance(); // closing /
        return new Token(TokenType.REGEX, sb.toString(), startLine, startCol);
    }

    private Token readTemporal(int startLine, int startCol) {
        advance(); // opening |
        StringBuilder sb = new StringBuilder();
        while (input.charAt(pos) != '|') {
            sb.append(input.charAt(pos));
            advance();
        }
        advance(); // closing |
        return new Token(TokenType.TEMPORAL, sb.toString(), startLine, startCol);
    }

    private Token readOperator(int startLine, int startCol) {
        char c = input.charAt(pos);
        advance();
        return switch (c) {
            case '+' -> match('+')
                    ? token(TokenType.PLUSPLUS, "++", startLine, startCol)
                    : token(TokenType.PLUS, "+", startLine, startCol);
            case '-' -> {
                if (match('>')) {
                    yield token(TokenType.ARROW, "->", startLine, startCol);
                }
                if (match('-')) {
                    yield token(TokenType.MINUSMINUS, "--", startLine, startCol);
                }
                yield token(TokenType.MINUS, "-", startLine, startCol);
            }
            case '*' -> token(TokenType.STAR, "*", startLine, startCol);
            case '/' -> token(TokenType.SLASH, "/", startLine, startCol);
            case '=' -> match('=')
                    ? token(TokenType.EQ, "==", startLine, startCol)
                    : token(TokenType.ASSIGN, "=", startLine, startCol);
            case '!' -> match('=')
                    ? token(TokenType.NEQ, "!=", startLine, startCol)
                    : token(TokenType.NOT, "!", startLine, startCol);
            case '>' -> match('=')
                    ? token(TokenType.GE, ">=", startLine, startCol)
                    : token(TokenType.GT, ">", startLine, startCol);
            case '<' -> match('=')
                    ? token(TokenType.LE, "<=", startLine, startCol)
                    : token(TokenType.LT, "<", startLine, startCol);
            case '~' -> match('=')
                    ? token(TokenType.SIMILAR, "~=", startLine, startCol)
                    : token(TokenType.TILDE, "~", startLine, startCol);
            case ':' -> match(':')
                    ? token(TokenType.DOUBLE_COLON, "::", startLine, startCol)
                    : token(TokenType.COLON, ":", startLine, startCol);
            case '$' -> {
                if (match('$')) {
                    yield match('$')
                            ? token(TokenType.DOLLAR_DOLLAR_DOLLAR, "$$$", startLine, startCol)
                            : token(TokenType.DOLLAR_DOLLAR, "$$", startLine, startCol);
                }
                yield token(TokenType.DOLLAR, "$", startLine, startCol);
            }
            case '.' -> token(TokenType.DOT, ".", startLine, startCol);
            case ',' -> token(TokenType.COMMA, ",", startLine, startCol);
            case ';' -> token(TokenType.SEMICOLON, ";", startLine, startCol);
            case '(' -> token(TokenType.LPAREN, "(", startLine, startCol);
            case ')' -> token(TokenType.RPAREN, ")", startLine, startCol);
            case '{' -> token(TokenType.LBRACE, "{", startLine, startCol);
            case '}' -> token(TokenType.RBRACE, "}", startLine, startCol);
            case '[' -> token(TokenType.LBRACKET, "[", startLine, startCol);
            case ']' -> token(TokenType.RBRACKET, "]", startLine, startCol);
            case '@' -> token(TokenType.AT, "@", startLine, startCol);
            case '?' -> token(TokenType.QUESTION, "?", startLine, startCol);
            case '#' -> token(TokenType.HASH, "#", startLine, startCol);
            case '^' -> token(TokenType.CARET, "^", startLine, startCol);
            case '|' -> token(TokenType.PIPE, "|", startLine, startCol);
            case '%' -> token(TokenType.PERCENT, "%", startLine, startCol);
            default -> throw error("unexpected character '" + c + "'", startLine, startCol);
        };
    }

    private static Token token(TokenType type, String value, int line, int col) {
        return new Token(type, value, line, col);
    }

    private boolean match(char expected) {
        if (pos < input.length() && input.charAt(pos) == expected) {
            advance();
            return true;
        }
        return false;
    }

    private char peek(int offset) {
        int i = pos + offset;
        return i < input.length() ? input.charAt(i) : '\0';
    }

    private static DataWeaveConversionException error(String message, int line, int col) {
        return new DataWeaveConversionException("DataWeave parse error at " + line + ":" + col + ": " + message);
    }

    private void advance() {
        if (pos < input.length()) {
            if (input.charAt(pos) == '\n') {
                line++;
                col = 1;
            } else {
                col++;
            }
            pos++;
        }
    }
}
