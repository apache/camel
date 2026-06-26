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

import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.transform.DataWeaveAst.ArrayLit;
import org.apache.camel.dsl.jbang.core.commands.transform.DataWeaveAst.BinaryOp;
import org.apache.camel.dsl.jbang.core.commands.transform.DataWeaveAst.DefaultExpr;
import org.apache.camel.dsl.jbang.core.commands.transform.DataWeaveAst.FieldAccess;
import org.apache.camel.dsl.jbang.core.commands.transform.DataWeaveAst.FunctionCall;
import org.apache.camel.dsl.jbang.core.commands.transform.DataWeaveAst.Identifier;
import org.apache.camel.dsl.jbang.core.commands.transform.DataWeaveAst.IfElse;
import org.apache.camel.dsl.jbang.core.commands.transform.DataWeaveAst.IndexAccess;
import org.apache.camel.dsl.jbang.core.commands.transform.DataWeaveAst.Lambda;
import org.apache.camel.dsl.jbang.core.commands.transform.DataWeaveAst.LambdaShorthand;
import org.apache.camel.dsl.jbang.core.commands.transform.DataWeaveAst.MapExpr;
import org.apache.camel.dsl.jbang.core.commands.transform.DataWeaveAst.NumberLit;
import org.apache.camel.dsl.jbang.core.commands.transform.DataWeaveAst.ObjectLit;
import org.apache.camel.dsl.jbang.core.commands.transform.DataWeaveAst.Parens;
import org.apache.camel.dsl.jbang.core.commands.transform.DataWeaveAst.Script;
import org.apache.camel.dsl.jbang.core.commands.transform.DataWeaveAst.StringLit;
import org.apache.camel.dsl.jbang.core.commands.transform.DataWeaveAst.TypeCoercion;
import org.apache.camel.dsl.jbang.core.commands.transform.DataWeaveAst.UnaryOp;
import org.apache.camel.dsl.jbang.core.commands.transform.DataWeaveAst.Unsupported;
import org.apache.camel.dsl.jbang.core.commands.transform.DataWeaveAst.VarDecl;
import org.apache.camel.dsl.jbang.core.commands.transform.DataWeaveLexer.Token;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link DataWeaveParser}.
 *
 * The parser is recursive descent and best-effort (it never throws on malformed input). These tests assert the shape of
 * the produced {@link DataWeaveAst} for representative scripts, with emphasis on operator precedence, postfix
 * collection operations, header parsing, and graceful handling of unsupported constructs. The AST records are exercised
 * transitively here rather than in a separate test, since they carry no logic of their own.
 */
class DataWeaveParserTest {

    private static DataWeaveAst parseExpr(String src) {
        var tokens = new DataWeaveLexer(src).tokenize();
        return new DataWeaveParser(tokens).parseExpressionOnly();
    }

    private static DataWeaveAst parseScript(String src) {
        var tokens = new DataWeaveLexer(src).tokenize();
        return new DataWeaveParser(tokens).parse();
    }

    // ── Header ──

    @Test
    void shouldParseHeaderVersionAndOutputType() {
        String dw = """
                %dw 2.0
                output application/json
                ---
                { greeting: "hello" }
                """;
        Script script = assertInstanceOf(Script.class, parseScript(dw));
        assertEquals("2.0", script.header().version());
        assertEquals("application/json", script.header().outputType());
        assertTrue(script.header().inputs().isEmpty(), "no input declarations expected");
        ObjectLit body = assertInstanceOf(ObjectLit.class, script.body());
        assertEquals(1, body.entries().size());
    }

    @Test
    void shouldParseInputDeclarations() {
        String dw = """
                %dw 2.0
                input payload application/xml
                output application/json
                ---
                payload
                """;
        Script script = assertInstanceOf(Script.class, parseScript(dw));
        assertEquals(1, script.header().inputs().size());
        assertEquals("payload", script.header().inputs().get(0).name());
        assertEquals("application/xml", script.header().inputs().get(0).mediaType());
    }

    @Test
    void shouldDefaultVersionWhenNoHeaderPresent() {
        // an expression with no %dw header still yields a Script with the default 2.0 version
        Script script = assertInstanceOf(Script.class, parseScript("payload"));
        assertEquals("2.0", script.header().version());
        assertNull(script.header().outputType());
        assertInstanceOf(Identifier.class, script.body());
    }

    // ── Literals and field access ──

    @Test
    void shouldParseLiterals() {
        assertEquals("42", assertInstanceOf(NumberLit.class, parseExpr("42")).value());
        assertEquals("hi", assertInstanceOf(StringLit.class, parseExpr("\"hi\"")).value());
        assertTrue(assertInstanceOf(DataWeaveAst.BooleanLit.class, parseExpr("true")).value());
        assertInstanceOf(DataWeaveAst.NullLit.class, parseExpr("null"));
    }

    @Test
    void shouldParseFieldAccessAndIndexAccessChain() {
        // payload.items[0] -> IndexAccess(FieldAccess(Identifier(payload), items), NumberLit(0))
        IndexAccess idx = assertInstanceOf(IndexAccess.class, parseExpr("payload.items[0]"));
        assertEquals("0", assertInstanceOf(NumberLit.class, idx.index()).value());
        FieldAccess fa = assertInstanceOf(FieldAccess.class, idx.object());
        assertEquals("items", fa.field());
        assertEquals("payload", assertInstanceOf(Identifier.class, fa.object()).name());
    }

    // ── Precedence ──

    @Test
    void shouldGiveMultiplicationHigherPrecedenceThanAddition() {
        // 1 + 2 * 3 -> BinaryOp(+, 1, BinaryOp(*, 2, 3))
        BinaryOp add = assertInstanceOf(BinaryOp.class, parseExpr("1 + 2 * 3"));
        assertEquals("+", add.op());
        assertEquals("1", assertInstanceOf(NumberLit.class, add.left()).value());
        BinaryOp mul = assertInstanceOf(BinaryOp.class, add.right());
        assertEquals("*", mul.op());
        assertEquals("2", assertInstanceOf(NumberLit.class, mul.left()).value());
        assertEquals("3", assertInstanceOf(NumberLit.class, mul.right()).value());
    }

    @Test
    void shouldGiveAndHigherPrecedenceThanOr() {
        // a and b or c -> BinaryOp(or, BinaryOp(and, a, b), c)
        BinaryOp or = assertInstanceOf(BinaryOp.class, parseExpr("a and b or c"));
        assertEquals("or", or.op());
        assertEquals("c", assertInstanceOf(Identifier.class, or.right()).name());
        BinaryOp and = assertInstanceOf(BinaryOp.class, or.left());
        assertEquals("and", and.op());
    }

    @Test
    void shouldParseConcatOperator() {
        BinaryOp op = assertInstanceOf(BinaryOp.class, parseExpr("\"a\" ++ \"b\""));
        assertEquals("++", op.op());
    }

    @Test
    void shouldParseParenthesizedGrouping() {
        Parens parens = assertInstanceOf(Parens.class, parseExpr("(a + b)"));
        assertEquals("+", assertInstanceOf(BinaryOp.class, parens.expr()).op());
    }

    // ── Object / array literals ──

    @Test
    void shouldParseObjectLiteralWithStaticAndDynamicKeys() {
        ObjectLit obj = assertInstanceOf(ObjectLit.class, parseExpr("{ name: payload.x, (k): v }"));
        assertEquals(2, obj.entries().size());
        // static key
        assertEquals("name", assertInstanceOf(Identifier.class, obj.entries().get(0).key()).name());
        assertFalse(obj.entries().get(0).dynamic());
        // dynamic key parsed from a parenthesized expression
        assertTrue(obj.entries().get(1).dynamic());
        assertEquals("k", assertInstanceOf(Identifier.class, obj.entries().get(1).key()).name());
    }

    @Test
    void shouldParseArrayLiteral() {
        ArrayLit arr = assertInstanceOf(ArrayLit.class, parseExpr("[1, 2, 3]"));
        assertEquals(3, arr.elements().size());
        assertEquals("2", assertInstanceOf(NumberLit.class, arr.elements().get(1)).value());
    }

    // ── Collection operations / lambdas ──

    @Test
    void shouldParseMapWithExplicitLambda() {
        MapExpr map = assertInstanceOf(MapExpr.class, parseExpr("payload map ((item) -> item.name)"));
        assertEquals("payload", assertInstanceOf(Identifier.class, map.collection()).name());
        Lambda lambda = assertInstanceOf(Lambda.class, map.lambda());
        assertEquals(1, lambda.params().size());
        assertEquals("item", lambda.params().get(0).name());
        assertInstanceOf(FieldAccess.class, lambda.body());
    }

    @Test
    void shouldParseMapWithDollarShorthand() {
        MapExpr map = assertInstanceOf(MapExpr.class, parseExpr("payload map $.name"));
        LambdaShorthand sh = assertInstanceOf(LambdaShorthand.class, map.lambda());
        assertEquals(List.of("name"), sh.fields());
    }

    // ── Function calls ──

    @Test
    void shouldParseBuiltinFunctionCall() {
        FunctionCall call = assertInstanceOf(FunctionCall.class, parseExpr("sizeOf(payload)"));
        assertEquals("sizeOf", call.name());
        assertEquals(1, call.args().size());
        assertEquals("payload", assertInstanceOf(Identifier.class, call.args().get(0)).name());
    }

    // ── Type coercion ──

    @Test
    void shouldParseTypeCoercionWithoutFormat() {
        TypeCoercion tc = assertInstanceOf(TypeCoercion.class, parseExpr("x as String"));
        assertEquals("String", tc.type());
        assertNull(tc.format());
    }

    @Test
    void shouldParseTypeCoercionWithFormat() {
        TypeCoercion tc = assertInstanceOf(TypeCoercion.class, parseExpr("x as Date {format: \"yyyy-MM-dd\"}"));
        assertEquals("Date", tc.type());
        assertEquals("yyyy-MM-dd", tc.format());
    }

    // ── Control flow ──

    @Test
    void shouldParseIfElse() {
        IfElse ie = assertInstanceOf(IfElse.class, parseExpr("if (a) b else c"));
        assertEquals("a", assertInstanceOf(Identifier.class, ie.condition()).name());
        assertEquals("b", assertInstanceOf(Identifier.class, ie.thenExpr()).name());
        assertEquals("c", assertInstanceOf(Identifier.class, ie.elseExpr()).name());
    }

    @Test
    void shouldParseDefaultExpression() {
        DefaultExpr def = assertInstanceOf(DefaultExpr.class, parseExpr("a default b"));
        assertEquals("a", assertInstanceOf(Identifier.class, def.expr()).name());
        assertEquals("b", assertInstanceOf(Identifier.class, def.fallback()).name());
    }

    @Test
    void shouldParseVarDeclarationWithTrailingBody() {
        // "var x = 1 x" -> VarDecl(x, NumberLit(1), body=Identifier(x))
        VarDecl var = assertInstanceOf(VarDecl.class, parseExpr("var x = 1 x"));
        assertEquals("x", var.name());
        assertEquals("1", assertInstanceOf(NumberLit.class, var.value()).value());
        assertEquals("x", assertInstanceOf(Identifier.class, var.body()).name());
    }

    // ── Unary ──

    @Test
    void shouldParseLogicalNot() {
        UnaryOp not = assertInstanceOf(UnaryOp.class, parseExpr("not a"));
        assertEquals("not", not.op());
        assertEquals("a", assertInstanceOf(Identifier.class, not.operand()).name());
    }

    // ── Graceful degradation ──

    @Test
    void shouldRepresentMatchExpressionAsUnsupported() {
        Unsupported node = assertInstanceOf(Unsupported.class, parseExpr("payload match { 1 }"));
        assertEquals("match expression", node.reason());
        assertTrue(node.originalText().startsWith("match"),
                "original text should be retained, was: " + node.originalText());
    }

    @Test
    void shouldNotThrowOnUnbalancedInput() {
        // expect() silently skips a missing RPAREN, so a best-effort AST is still produced
        List<Token> tokens = new DataWeaveLexer("(a + b").tokenize();
        assertEquals(DataWeaveLexer.TokenType.EOF, tokens.get(tokens.size() - 1).type());
        assertInstanceOf(Parens.class, parseExpr("(a + b"));
    }
}
