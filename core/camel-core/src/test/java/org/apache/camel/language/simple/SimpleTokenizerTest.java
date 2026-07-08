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
package org.apache.camel.language.simple;

import org.apache.camel.language.simple.types.SimpleToken;
import org.apache.camel.language.simple.types.TokenType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Tests that binary, other, and ternary infix operators are each recognized when surrounded by spaces, using the shared
 * evalSurroundedBySpace logic.
 */
public class SimpleTokenizerTest {

    private final SimpleTokenizer tokenizer = new SimpleTokenizer();

    @Test
    public void testBinaryOperatorIsRecognized() {
        // "a == b": at index 2, " ==" starts and should be recognized as binary
        String expression = "a == b";
        // index 2 is at '=' after the space
        SimpleToken token = tokenizer.nextToken(expression, 2, false);
        assertEquals(TokenType.binaryOperator, token.getType().getType());
        assertEquals("==", token.getType().getValue());
    }

    @Test
    public void testBinaryOperatorNotRecognizedAtStartOfExpression() {
        // "== b": binary op at index 0 should NOT match (no space before it)
        String expression = "== b";
        SimpleToken token = tokenizer.nextToken(expression, 0, false);
        assertFalse(token.getType().isBinary(), "Binary op at index 0 must not match without preceding space");
    }

    @Test
    public void testOtherOperatorIsRecognized() {
        // "a ?: b": at index 2, "?:" should be recognized as other operator
        String expression = "a ?: b";
        SimpleToken token = tokenizer.nextToken(expression, 2, false);
        assertEquals(TokenType.otherOperator, token.getType().getType());
        assertEquals("?:", token.getType().getValue());
    }

    @Test
    public void testTernaryOperatorQuestionMarkIsRecognized() {
        // "a ? b : c": at index 2, "?" should be recognized as ternary operator
        String expression = "a ? b : c";
        SimpleToken token = tokenizer.nextToken(expression, 2, false);
        assertEquals(TokenType.ternaryOperator, token.getType().getType());
        assertEquals("?", token.getType().getValue());
    }

    @Test
    public void testTernaryOperatorColonIsRecognized() {
        // "b : c": at index 2, ":" should be recognized as ternary operator
        String expression = "b : c";
        SimpleToken token = tokenizer.nextToken(expression, 2, false);
        assertEquals(TokenType.ternaryOperator, token.getType().getType());
        assertEquals(":", token.getType().getValue());
    }

    @Test
    public void testTernaryOperatorNotRecognizedWithoutTrailingSpace() {
        // "a ?b": "?" at index 2 without trailing space must not match as ternary
        String expression = "a ?b";
        SimpleToken token = tokenizer.nextToken(expression, 2, false);
        assertFalse(token.getType().isTernary(), "Ternary op without trailing space must not match");
    }

    @Test
    public void testContainsBinaryOperatorIsRecognized() {
        // "a contains b": at index 2, "contains" is a binary operator
        String expression = "a contains b";
        SimpleToken token = tokenizer.nextToken(expression, 2, false);
        assertEquals(TokenType.binaryOperator, token.getType().getType());
        assertEquals("contains", token.getType().getValue());
    }
}
