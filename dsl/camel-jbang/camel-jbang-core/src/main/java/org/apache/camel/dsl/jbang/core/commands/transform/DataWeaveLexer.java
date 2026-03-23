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
package org.apache.camel.dsl.jbang.core.commands.transform;

import java.util.ArrayList;
import java.util.List;

/**
 * Tokenizer for DataWeave 2.0 scripts.
 */
public class DataWeaveLexer {

    public enum TokenType {
        // Literals
        STRING,
        NUMBER,
        BOOLEAN,
        NULL_LIT,
        // Identifiers and keywords
        IDENTIFIER,
        // Operators
        PLUS,
        MINUS,
        STAR,
        SLASH,
        PLUSPLUS,
        ASSIGN,
        EQ,
        NEQ,
        GT,
        GE,
        LT,
        LE,
        AND,
        OR,
        NOT,
        // Punctuation
        DOT,
        COMMA,
        COLON,
        ARROW,
        SEMICOLON,
        LPAREN,
        RPAREN,
        LBRACE,
        RBRACE,
        LBRACKET,
        RBRACKET,
        DOLLAR,
        // Special
        HEADER_SEPARATOR, // ---
        PERCENT,          // %
        TILDE,            // ~
        EOF
    }

    public record Token(TokenType type, String value, int line, int col) {
        @Override
        public String toString() {
            return type + "(" + value + ")@" + line + ":" + col;
        }
    }

    private final String input;
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
        List<Token> tokens = new ArrayList<>();
        while (pos < input.length()) {
            skipWhitespaceAndComments();
            if (pos >= input.length()) {
                break;
            }

            Token token = readToken();
            if (token != null) {
                tokens.add(token);
            }
        }
        tokens.add(new Token(TokenType.EOF, "", line, col));
        return tokens;
    }

    private void skipWhitespaceAndComments() {
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\r') {
                advance();
            } else if (c == '\n') {
                advance();
            } else if (c == '/' && pos + 1 < input.length() && input.charAt(pos + 1) == '/') {
                // Line comment
                while (pos < input.length() && input.charAt(pos) != '\n') {
                    advance();
                }
            } else if (c == '/' && pos + 1 < input.length() && input.charAt(pos + 1) == '*') {
                // Block comment
                advance(); // /
                advance(); // *
                while (pos + 1 < input.length()
                        && !(input.charAt(pos) == '*' && input.charAt(pos + 1) == '/')) {
                    advance();
                }
                if (pos + 1 < input.length()) {
                    advance(); // *
                    advance(); // /
                }
            } else {
                break;
            }
        }
    }

    private Token readToken() {
        int startLine = line;
        int startCol = col;
        char c = input.charAt(pos);

        // Header separator ---
        if (c == '-' && pos + 2 < input.length()
                && input.charAt(pos + 1) == '-' && input.charAt(pos + 2) == '-') {
            // Make sure it's not a negative number context
            if (pos == 0 || isHeaderSeparatorContext()) {
                advance();
                advance();
                advance();
                return new Token(TokenType.HEADER_SEPARATOR, "---", startLine, startCol);
            }
        }

        // Strings
        if (c == '"' || c == '\'') {
            return readString(c, startLine, startCol);
        }

        // Numbers
        if (Character.isDigit(c) || (c == '-' && pos + 1 < input.length() && Character.isDigit(input.charAt(pos + 1))
                && !isPreviousTokenValueLike())) {
            return readNumber(startLine, startCol);
        }

        // Identifiers and keywords
        if (Character.isLetter(c) || c == '_') {
            return readIdentifier(startLine, startCol);
        }

        // Operators and punctuation
        return readOperator(startLine, startCol);
    }

    private boolean isHeaderSeparatorContext() {
        // Look backwards to see if we're at the start of a line (after whitespace)
        int i = pos - 1;
        while (i >= 0 && (input.charAt(i) == ' ' || input.charAt(i) == '\t')) {
            i--;
        }
        return i < 0 || input.charAt(i) == '\n';
    }

    private boolean isPreviousTokenValueLike() {
        // Look back to see if the previous non-whitespace is a value-like token
        int i = pos - 1;
        while (i >= 0 && (input.charAt(i) == ' ' || input.charAt(i) == '\t')) {
            i--;
        }
        if (i < 0) {
            return false;
        }
        char prev = input.charAt(i);
        return Character.isLetterOrDigit(prev) || prev == ')' || prev == ']' || prev == '}' || prev == '"'
                || prev == '\'';
    }

    private Token readString(char quote, int startLine, int startCol) {
        advance(); // opening quote
        StringBuilder sb = new StringBuilder();
        while (pos < input.length() && input.charAt(pos) != quote) {
            if (input.charAt(pos) == '\\' && pos + 1 < input.length()) {
                sb.append(input.charAt(pos));
                advance();
                sb.append(input.charAt(pos));
                advance();
            } else {
                sb.append(input.charAt(pos));
                advance();
            }
        }
        if (pos < input.length()) {
            advance(); // closing quote
        }
        return new Token(TokenType.STRING, sb.toString(), startLine, startCol);
    }

    private Token readNumber(int startLine, int startCol) {
        StringBuilder sb = new StringBuilder();
        if (input.charAt(pos) == '-') {
            sb.append('-');
            advance();
        }
        while (pos < input.length() && (Character.isDigit(input.charAt(pos)) || input.charAt(pos) == '.')) {
            sb.append(input.charAt(pos));
            advance();
        }
        return new Token(TokenType.NUMBER, sb.toString(), startLine, startCol);
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

    private Token readOperator(int startLine, int startCol) {
        char c = input.charAt(pos);
        advance();

        return switch (c) {
            case '+' -> {
                if (pos < input.length() && input.charAt(pos) == '+') {
                    advance();
                    yield new Token(TokenType.PLUSPLUS, "++", startLine, startCol);
                }
                yield new Token(TokenType.PLUS, "+", startLine, startCol);
            }
            case '-' -> {
                if (pos < input.length() && input.charAt(pos) == '>') {
                    advance();
                    yield new Token(TokenType.ARROW, "->", startLine, startCol);
                }
                yield new Token(TokenType.MINUS, "-", startLine, startCol);
            }
            case '*' -> new Token(TokenType.STAR, "*", startLine, startCol);
            case '/' -> new Token(TokenType.SLASH, "/", startLine, startCol);
            case '=' -> {
                if (pos < input.length() && input.charAt(pos) == '=') {
                    advance();
                    yield new Token(TokenType.EQ, "==", startLine, startCol);
                }
                yield new Token(TokenType.ASSIGN, "=", startLine, startCol);
            }
            case '!' -> {
                if (pos < input.length() && input.charAt(pos) == '=') {
                    advance();
                    yield new Token(TokenType.NEQ, "!=", startLine, startCol);
                }
                yield new Token(TokenType.NOT, "!", startLine, startCol);
            }
            case '>' -> {
                if (pos < input.length() && input.charAt(pos) == '=') {
                    advance();
                    yield new Token(TokenType.GE, ">=", startLine, startCol);
                }
                yield new Token(TokenType.GT, ">", startLine, startCol);
            }
            case '<' -> {
                if (pos < input.length() && input.charAt(pos) == '=') {
                    advance();
                    yield new Token(TokenType.LE, "<=", startLine, startCol);
                }
                yield new Token(TokenType.LT, "<", startLine, startCol);
            }
            case '.' -> new Token(TokenType.DOT, ".", startLine, startCol);
            case ',' -> new Token(TokenType.COMMA, ",", startLine, startCol);
            case ':' -> new Token(TokenType.COLON, ":", startLine, startCol);
            case ';' -> new Token(TokenType.SEMICOLON, ";", startLine, startCol);
            case '(' -> new Token(TokenType.LPAREN, "(", startLine, startCol);
            case ')' -> new Token(TokenType.RPAREN, ")", startLine, startCol);
            case '{' -> new Token(TokenType.LBRACE, "{", startLine, startCol);
            case '}' -> new Token(TokenType.RBRACE, "}", startLine, startCol);
            case '[' -> new Token(TokenType.LBRACKET, "[", startLine, startCol);
            case ']' -> new Token(TokenType.RBRACKET, "]", startLine, startCol);
            case '$' -> new Token(TokenType.DOLLAR, "$", startLine, startCol);
            case '%' -> new Token(TokenType.PERCENT, "%", startLine, startCol);
            case '~' -> {
                if (pos < input.length() && input.charAt(pos) == '=') {
                    advance();
                    yield new Token(TokenType.TILDE, "~=", startLine, startCol);
                }
                yield new Token(TokenType.TILDE, "~", startLine, startCol);
            }
            default -> {
                // Skip unknown character
                yield null;
            }
        };
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
