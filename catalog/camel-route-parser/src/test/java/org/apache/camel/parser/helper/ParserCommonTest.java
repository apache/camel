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
package org.apache.camel.parser.helper;

import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.Block;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.NumberLiteral;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.SimpleName;
import org.jboss.forge.roaster.model.Type;
import org.jboss.forge.roaster.model.source.FieldSource;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ParserCommonTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "completionPredicate", "completion",
            "onWhen", "when", "handled", "continued",
            "retryWhile", "filter", "validate", "loopDoWhile"})
    void isCommonPredicateTest(String name) {
        assertTrue(ParserCommon.isCommonPredicate(name));
    }

    @ParameterizedTest
    @EmptySource
    @ValueSource(strings = {"disabled", "exception", "completionSize", "completionSizeExpression", "completionTimeoutExpression"})
    void isCommonPredicateShouldReturnFalseTest(String name) {
        assertFalse(ParserCommon.isCommonPredicate(name));
    }

    @ParameterizedTest
    @NullSource
    void isCommonPredicateShouldThrowException(String name) {
        //would it be better to return false when the value is null?
        assertThrows(NullPointerException.class, () -> ParserCommon.isCommonPredicate(name));
    }

    @Test
    void isNumericOperatorWhenExpressionInstanceofNumberLiteralTest() {
        ParserCommon.isNumericOperator(null, null, mock(NumberLiteral.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"int", "long", "Integer", "Long"})
    @SuppressWarnings("unchecked")
    void isNumericOperatorShouldReturnTrueWhenExpressionInstanceofSimpleNameTest(String typeParam) {

        JavaClassSource clazz = mock(JavaClassSource.class);
        Block block = mock(Block.class);
        SimpleName expression = mock(SimpleName.class);

        try (MockedStatic<ParserCommon> parserCommonMockedStatic = mockStatic(ParserCommon.class)){
            FieldSource<JavaClassSource> fieldSource = (FieldSource<JavaClassSource>) mock(FieldSource.class);
            Type<JavaClassSource> type = (Type<JavaClassSource>) mock(Type.class);
            when(fieldSource.getType()).thenReturn(type);
            when(type.isType(typeParam)).thenReturn(true);
            parserCommonMockedStatic.when(() -> ParserCommon.getField(clazz, block, expression)).thenReturn(fieldSource);
            parserCommonMockedStatic.when(() -> ParserCommon.isNumericOperator(clazz, block, expression)).thenCallRealMethod();

            assertTrue(ParserCommon.isNumericOperator(clazz, block, expression));

            parserCommonMockedStatic.verify(() -> ParserCommon.getField(clazz, block, expression));
            parserCommonMockedStatic.verify(() -> ParserCommon.isNumericOperator(clazz, block, expression));
            parserCommonMockedStatic.verifyNoMoreInteractions();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "byte", "Byte",        //      <- WARN probably a mistake?
            "short", "Short",      //      <- WARN probably a mistake?
            "double", "Double",    //      Floating Point Types
            "float", "Float",      //      Floating Point Types
            "char", "Character", "String",
            "boolean", "Boolean"})
    @SuppressWarnings("unchecked")
    void isNumericOperatorShouldReturnFalseWhenExpressionInstanceofSimpleNameTest(String typeParam) {
        JavaClassSource clazz = mock(JavaClassSource.class);
        Block block = mock(Block.class);
        SimpleName expression = mock(SimpleName.class);

        try (MockedStatic<ParserCommon> parserCommonMockedStatic = mockStatic(ParserCommon.class)){
            FieldSource<JavaClassSource> fieldSource = (FieldSource<JavaClassSource>) mock(FieldSource.class);
            Type<JavaClassSource> type = (Type<JavaClassSource>) mock(Type.class);
            when(fieldSource.getType()).thenReturn(type);
            when(type.isType(typeParam)).thenReturn(true);
            parserCommonMockedStatic.when(() -> ParserCommon.getField(clazz, block, expression)).thenReturn(fieldSource);
            parserCommonMockedStatic.when(() -> ParserCommon.isNumericOperator(clazz, block, expression)).thenCallRealMethod();

            assertFalse(ParserCommon.isNumericOperator(clazz, block, expression));

            parserCommonMockedStatic.verify(() -> ParserCommon.getField(clazz, block, expression));
            parserCommonMockedStatic.verify(() -> ParserCommon.isNumericOperator(clazz, block, expression));
            parserCommonMockedStatic.verifyNoMoreInteractions();
        }
    }
}
