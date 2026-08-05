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

import java.util.List;

import org.apache.camel.component.dataweave.DataWeaveLexer.Token;
import org.apache.camel.component.dataweave.DataWeaveLexer.TokenType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DataWeaveLexerTest {

    private static List<Token> tokenize(String input) {
        return new DataWeaveLexer(input).tokenize();
    }

    private static List<TokenType> types(String input) {
        return tokenize(input).stream().map(Token::type).toList();
    }

    @Test
    void shouldAlwaysAppendEofToken() {
        List<Token> tokens = tokenize("");
        assertEquals(1, tokens.size());
        assertEquals(TokenType.EOF, tokens.get(0).type());
    }

    @Test
    void shouldClassifyKeywordsAndIdentifiers() {
        assertEquals(
                List.of(TokenType.BOOLEAN, TokenType.BOOLEAN, TokenType.NULL_LIT,
                        TokenType.AND, TokenType.OR, TokenType.NOT,
                        TokenType.IDENTIFIER, TokenType.EOF),
                types("true false null and or not payload"));
    }

    @Test
    void shouldReadStringWithBothQuoteStylesAndKeepEscapes() {
        List<Token> tokens = tokenize("\"hello\" 'world' \"a\\\"b\"");
        assertEquals(TokenType.STRING, tokens.get(0).type());
        assertEquals("hello", tokens.get(0).value());
        assertEquals(TokenType.STRING, tokens.get(1).type());
        assertEquals("world", tokens.get(1).value());
        assertEquals("a\\\"b", tokens.get(2).value());
    }

    @Test
    void shouldReadDecimalNumber() {
        List<Token> tokens = tokenize("3.14");
        assertEquals(TokenType.NUMBER, tokens.get(0).type());
        assertEquals("3.14", tokens.get(0).value());
    }

    @Test
    void shouldTreatLeadingMinusAsNegativeNumberWhenNotAfterValue() {
        List<Token> tokens = tokenize("-5");
        assertEquals(TokenType.NUMBER, tokens.get(0).type());
        assertEquals("-5", tokens.get(0).value());
    }

    @Test
    void shouldTreatMinusBetweenValuesAsOperator() {
        assertEquals(
                List.of(TokenType.NUMBER, TokenType.MINUS, TokenType.NUMBER, TokenType.EOF),
                types("5 - 3"));
    }

    @Test
    void shouldTreatMinusAfterOpeningParenAsNegativeNumber() {
        assertEquals(
                List.of(TokenType.LPAREN, TokenType.NUMBER, TokenType.RPAREN, TokenType.EOF),
                types("(-3)"));
        assertEquals("-3", tokenize("(-3)").get(1).value());
    }

    @Test
    void shouldDistinguishSingleAndDoubleCharacterOperators() {
        assertEquals(
                List.of(TokenType.PLUSPLUS, TokenType.PLUS, TokenType.ARROW, TokenType.MINUS,
                        TokenType.EQ, TokenType.ASSIGN, TokenType.NEQ, TokenType.GE, TokenType.GT,
                        TokenType.LE, TokenType.LT, TokenType.EOF),
                types("++ + -> - == = != >= > <= <"));
    }

    @Test
    void shouldRecognizeHeaderSeparatorAtLineStart() {
        List<TokenType> ts = types("%dw 2.0\n---\n-5");
        assertEquals(
                List.of(TokenType.PERCENT, TokenType.IDENTIFIER, TokenType.NUMBER,
                        TokenType.HEADER_SEPARATOR, TokenType.NUMBER, TokenType.EOF),
                ts);
    }

    @Test
    void shouldSkipLineAndBlockComments() {
        String input = """
                // a line comment
                payload /* inline block */ + 1
                """;
        assertEquals(
                List.of(TokenType.IDENTIFIER, TokenType.PLUS, TokenType.NUMBER, TokenType.EOF),
                types(input));
    }

    @Test
    void shouldTrackLineAndColumnPositions() {
        List<Token> tokens = tokenize("ab\n  cd");
        assertEquals(1, tokens.get(0).line());
        assertEquals(1, tokens.get(0).col());
        assertEquals("ab", tokens.get(0).value());
        assertEquals(2, tokens.get(1).line());
        assertEquals(3, tokens.get(1).col());
        assertEquals("cd", tokens.get(1).value());
    }

    @Test
    void shouldSkipUnknownCharactersInsteadOfFailing() {
        assertEquals(
                List.of(TokenType.IDENTIFIER, TokenType.IDENTIFIER, TokenType.EOF),
                types("a @ b"));
    }
}
