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
import java.util.Map;

import org.apache.camel.component.dataweave.DataWeaveAst.ArrayLit;
import org.apache.camel.component.dataweave.DataWeaveAst.AttributeAccess;
import org.apache.camel.component.dataweave.DataWeaveAst.BinaryOp;
import org.apache.camel.component.dataweave.DataWeaveAst.Block;
import org.apache.camel.component.dataweave.DataWeaveAst.DefaultExpr;
import org.apache.camel.component.dataweave.DataWeaveAst.DescendantSelector;
import org.apache.camel.component.dataweave.DataWeaveAst.Dollar;
import org.apache.camel.component.dataweave.DataWeaveAst.ExistenceCheck;
import org.apache.camel.component.dataweave.DataWeaveAst.FieldAccess;
import org.apache.camel.component.dataweave.DataWeaveAst.FilterSelector;
import org.apache.camel.component.dataweave.DataWeaveAst.FunDecl;
import org.apache.camel.component.dataweave.DataWeaveAst.FunctionCall;
import org.apache.camel.component.dataweave.DataWeaveAst.Identifier;
import org.apache.camel.component.dataweave.DataWeaveAst.IfElse;
import org.apache.camel.component.dataweave.DataWeaveAst.IndexAccess;
import org.apache.camel.component.dataweave.DataWeaveAst.Interpolation;
import org.apache.camel.component.dataweave.DataWeaveAst.Lambda;
import org.apache.camel.component.dataweave.DataWeaveAst.Match;
import org.apache.camel.component.dataweave.DataWeaveAst.MultiValueSelector;
import org.apache.camel.component.dataweave.DataWeaveAst.NumberLit;
import org.apache.camel.component.dataweave.DataWeaveAst.ObjectEntry;
import org.apache.camel.component.dataweave.DataWeaveAst.ObjectLit;
import org.apache.camel.component.dataweave.DataWeaveAst.Parens;
import org.apache.camel.component.dataweave.DataWeaveAst.Range;
import org.apache.camel.component.dataweave.DataWeaveAst.RegexLit;
import org.apache.camel.component.dataweave.DataWeaveAst.Script;
import org.apache.camel.component.dataweave.DataWeaveAst.StringLit;
import org.apache.camel.component.dataweave.DataWeaveAst.TemporalLit;
import org.apache.camel.component.dataweave.DataWeaveAst.TypeCheck;
import org.apache.camel.component.dataweave.DataWeaveAst.TypeCoercion;
import org.apache.camel.component.dataweave.DataWeaveAst.UnaryOp;
import org.apache.camel.component.dataweave.DataWeaveAst.Unsupported;
import org.apache.camel.component.dataweave.DataWeaveAst.VarDecl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link DataWeaveParser}: the shape of the {@link DataWeaveAst} for representative scripts, with
 * emphasis on DataWeave operator precedence, lambdas, selectors, and that malformed scripts fail.
 */
class DataWeaveParserTest {

    private static DataWeaveAst parseExpr(String src) {
        return new DataWeaveParser(new DataWeaveLexer(src).tokenize()).parseExpressionOnly();
    }

    private static Script parseScript(String src) {
        return assertInstanceOf(Script.class, new DataWeaveParser(new DataWeaveLexer(src).tokenize()).parse());
    }

    private static String name(DataWeaveAst node) {
        return assertInstanceOf(Identifier.class, node).name();
    }

    // -- Header

    @Test
    void shouldParseHeader() {
        Script script = parseScript("""
                %dw 2.0
                import * from dw::core::Strings
                input payload application/xml
                output application/json skipNullOn="everywhere", indent=false
                type Order = { id: Number }
                ---
                { greeting: "hello" }
                """);
        assertEquals("2.0", script.header().version());
        assertEquals("application/json", script.header().outputType());
        assertEquals(Map.of("skipNullOn", "everywhere", "indent", "false"), script.header().outputProperties());
        assertEquals("payload", script.header().inputs().get(0).name());
        assertEquals("application/xml", script.header().inputs().get(0).mediaType());
        assertEquals(List.of("dw::core::Strings"), script.header().imports());
        assertInstanceOf(ObjectLit.class, script.body());
    }

    @Test
    void shouldWrapHeaderDeclarationsAroundBody() {
        Script script = parseScript("""
                %dw 2.0
                var rate: Number = 0.08
                fun total(a, b = 1) = a * b
                ---
                total(rate)
                """);
        Block block = assertInstanceOf(Block.class, script.body());
        assertEquals("rate", assertInstanceOf(VarDecl.class, block.declarations().get(0)).name());
        FunDecl fun = assertInstanceOf(FunDecl.class, block.declarations().get(1));
        assertEquals("total", fun.name());
        assertEquals("b", fun.params().get(1).name());
        assertEquals("1", assertInstanceOf(NumberLit.class, fun.params().get(1).defaultValue()).value());
        assertEquals("total", assertInstanceOf(FunctionCall.class, block.expr()).name());
    }

    @Test
    void shouldRepresentNamespaceDeclarationAsUnsupported() {
        Script script = parseScript("%dw 2.0\nns ns0 http://example.com\n---\npayload");
        Block block = assertInstanceOf(Block.class, script.body());
        assertInstanceOf(Unsupported.class, block.declarations().get(0));
    }

    @Test
    void shouldRejectUnknownHeaderDirective() {
        assertThrows(DataWeaveConversionException.class, () -> parseScript("%dw 2.0\nfoo bar\n---\npayload"));
    }

    @Test
    void shouldParseScriptWithoutHeader() {
        Script script = parseScript("payload");
        assertNull(script.header().outputType());
        assertEquals("payload", name(script.body()));
    }

    // -- Literals

    @Test
    void shouldParseLiterals() {
        assertEquals("42", assertInstanceOf(NumberLit.class, parseExpr("42")).value());
        assertEquals("hi", assertInstanceOf(StringLit.class, parseExpr("\"hi\"")).value());
        assertEquals("it's \"x\"", assertInstanceOf(StringLit.class, parseExpr("'it\\'s \"x\"'")).value());
        assertEquals("\\s+", assertInstanceOf(RegexLit.class, parseExpr("/\\s+/")).pattern());
        assertEquals("2020-01-31", assertInstanceOf(TemporalLit.class, parseExpr("|2020-01-31|")).value());
        assertTrue(assertInstanceOf(TemporalLit.class, parseExpr("|P1D|")).isPeriod());
    }

    @Test
    void shouldParseStringInterpolation() {
        Interpolation i = assertInstanceOf(Interpolation.class, parseExpr("\"Hello $(upper(\"x\")) and $(payload.name)!\""));
        assertEquals(5, i.parts().size());
        assertEquals("Hello ", assertInstanceOf(StringLit.class, i.parts().get(0)).value());
        assertEquals("upper", assertInstanceOf(FunctionCall.class, i.parts().get(1)).name());
        assertInstanceOf(FieldAccess.class, i.parts().get(3));
        assertEquals("!", assertInstanceOf(StringLit.class, i.parts().get(4)).value());
    }

    // -- Precedence

    @Test
    void shouldParseArithmeticPrecedence() {
        BinaryOp add = assertInstanceOf(BinaryOp.class, parseExpr("1 + 2 * 3"));
        assertEquals("+", add.op());
        assertEquals("*", assertInstanceOf(BinaryOp.class, add.right()).op());
    }

    @Test
    void shouldGiveAndHigherPrecedenceThanOr() {
        BinaryOp or = assertInstanceOf(BinaryOp.class, parseExpr("a and b or c"));
        assertEquals("or", or.op());
        assertEquals("and", assertInstanceOf(BinaryOp.class, or.left()).op());
    }

    @Test
    void shouldGiveInfixFunctionsLowerPrecedenceThanComparison() {
        // payload filter $.qty > 1  is  payload filter ($.qty > 1)
        FunctionCall filter = assertInstanceOf(FunctionCall.class, parseExpr("payload filter $.qty > 1"));
        assertEquals("filter", filter.name());
        assertEquals("payload", name(filter.args().get(0)));
        assertEquals(">", assertInstanceOf(BinaryOp.class, filter.args().get(1)).op());
    }

    @Test
    void shouldChainInfixFunctionsLeftToRight() {
        // xs filter $.a map $.b  is  (xs filter $.a) map $.b
        FunctionCall map = assertInstanceOf(FunctionCall.class, parseExpr("xs filter $.a map $.b"));
        assertEquals("map", map.name());
        assertEquals("filter", assertInstanceOf(FunctionCall.class, map.args().get(0)).name());
    }

    @Test
    void shouldGiveDefaultHigherPrecedenceThanInfixFunctions() {
        // a default [] map $  is  (a default []) map $
        FunctionCall map = assertInstanceOf(FunctionCall.class, parseExpr("a default [] map $"));
        assertInstanceOf(DefaultExpr.class, map.args().get(0));
    }

    @Test
    void shouldParseConcatAtInfixLevel() {
        // "a" ++ 1 + 2  is  "a" ++ (1 + 2)
        BinaryOp concat = assertInstanceOf(BinaryOp.class, parseExpr("\"a\" ++ 1 + 2"));
        assertEquals("++", concat.op());
        assertEquals("+", assertInstanceOf(BinaryOp.class, concat.right()).op());
    }

    @Test
    void shouldParseAnyFunctionAsInfix() {
        FunctionCall mod = assertInstanceOf(FunctionCall.class, parseExpr("10 mod 3"));
        assertEquals("mod", mod.name());
        FunctionCall replace = assertInstanceOf(FunctionCall.class, parseExpr("s replace /a/ with \"b\""));
        assertEquals(3, replace.args().size());
        assertInstanceOf(RegexLit.class, replace.args().get(1));
    }

    @Test
    void shouldGiveCoercionHigherPrecedenceThanArithmetic() {
        BinaryOp mul = assertInstanceOf(BinaryOp.class, parseExpr("a * b as Number"));
        assertInstanceOf(TypeCoercion.class, mul.right());
    }

    @Test
    void shouldParseUnaryOperators() {
        UnaryOp not = assertInstanceOf(UnaryOp.class, parseExpr("not a"));
        assertEquals("not", not.op());
        UnaryOp neg = assertInstanceOf(UnaryOp.class, parseExpr("-$.price"));
        assertEquals("-", neg.op());
        assertInstanceOf(FieldAccess.class, neg.operand());
    }

    // -- Lambdas

    @Test
    void shouldParseLambdaWithAndWithoutOuterParentheses() {
        for (String src : List.of("payload map ((item, index) -> item.name)", "payload map (item, index) -> item.name")) {
            FunctionCall map = assertInstanceOf(FunctionCall.class, parseExpr(src));
            Lambda lambda = assertInstanceOf(Lambda.class, map.args().get(1));
            assertEquals(2, lambda.params().size());
            assertInstanceOf(FieldAccess.class, lambda.body());
        }
    }

    @Test
    void shouldExtendLambdaBodyAsFarAsPossible() {
        // xs map (x) -> x.a map ...: the second map is in the body of the lambda
        FunctionCall map = assertInstanceOf(FunctionCall.class, parseExpr("xs map (x) -> x.a map $.b"));
        Lambda lambda = assertInstanceOf(Lambda.class, map.args().get(1));
        assertEquals("map", assertInstanceOf(FunctionCall.class, lambda.body()).name());
    }

    @Test
    void shouldParseLambdaWithDefaultAndTypedParameters() {
        FunctionCall reduce = assertInstanceOf(FunctionCall.class,
                parseExpr("xs reduce ((item: Number, acc: Number = 0) -> acc + item)"));
        Lambda lambda = assertInstanceOf(Lambda.class, reduce.args().get(1));
        assertEquals("acc", lambda.params().get(1).name());
        assertEquals("0", assertInstanceOf(NumberLit.class, lambda.params().get(1).defaultValue()).value());
    }

    @Test
    void shouldParseLambdaAsFunctionArgument() {
        FunctionCall call = assertInstanceOf(FunctionCall.class, parseExpr("applyAll(xs, (n) -> n * 2)"));
        assertEquals(2, call.args().size());
        assertInstanceOf(Lambda.class, call.args().get(1));
    }

    @Test
    void shouldParseDollarShorthand() {
        FunctionCall map = assertInstanceOf(FunctionCall.class, parseExpr("payload map { k: $$, v: $.name }"));
        ObjectLit body = assertInstanceOf(ObjectLit.class, map.args().get(1));
        assertEquals(2, assertInstanceOf(Dollar.class, body.entries().get(0).value()).level());
        FieldAccess field = assertInstanceOf(FieldAccess.class, body.entries().get(1).value());
        assertEquals(1, assertInstanceOf(Dollar.class, field.object()).level());
    }

    // -- Selectors

    @Test
    void shouldParseSelectors() {
        IndexAccess idx = assertInstanceOf(IndexAccess.class, parseExpr("payload.items[0]"));
        assertEquals("items", assertInstanceOf(FieldAccess.class, idx.object()).field());
        assertEquals("first-name", assertInstanceOf(FieldAccess.class, parseExpr("payload.'first-name'")).field());
        assertEquals("id", assertInstanceOf(AttributeAccess.class, parseExpr("payload.order.@id")).attribute());
        assertEquals("item", assertInstanceOf(MultiValueSelector.class, parseExpr("payload.*item")).field());
        assertEquals("sku", assertInstanceOf(DescendantSelector.class, parseExpr("payload..sku")).field());
        assertInstanceOf(ExistenceCheck.class, parseExpr("payload.a?"));
        assertInstanceOf(FilterSelector.class, parseExpr("payload.items[?($.qty > 1)]"));
        Range range = assertInstanceOf(Range.class, assertInstanceOf(IndexAccess.class, parseExpr("xs[-1 to 0]")).index());
        assertEquals("-1", assertInstanceOf(NumberLit.class, range.from()).value());
    }

    // -- Objects

    @Test
    void shouldParseObjectEntries() {
        ObjectLit obj = assertInstanceOf(ObjectLit.class,
                parseExpr("{ name: x, (k): v, (a: 1) if c, (xs map { ($.k): $.v }), default: 2 }"));
        List<ObjectEntry> entries = obj.entries();
        assertEquals(5, entries.size());
        assertFalse(entries.get(0).dynamic());
        assertEquals("name", assertInstanceOf(StringLit.class, entries.get(0).key()).value());
        assertTrue(entries.get(1).dynamic());
        assertEquals("c", name(entries.get(2).condition()));
        assertNull(entries.get(3).key(), "an object spread has no key");
        assertEquals("default", assertInstanceOf(StringLit.class, entries.get(4).key()).value());
    }

    @Test
    void shouldParseArrayLiteral() {
        ArrayLit arr = assertInstanceOf(ArrayLit.class, parseExpr("[1, 2, 3]"));
        assertEquals(3, arr.elements().size());
    }

    // -- Control flow

    @Test
    void shouldParseIfElseAndUnless() {
        IfElse ie = assertInstanceOf(IfElse.class, parseExpr("if (a) b else c"));
        assertEquals("a", name(ie.condition()));
        IfElse unless = assertInstanceOf(IfElse.class, parseExpr("unless (a) b otherwise c"));
        assertEquals("c", name(unless.thenExpr()));
        assertEquals("b", name(unless.elseExpr()));
    }

    @Test
    void shouldParseMatch() {
        Match match = assertInstanceOf(Match.class, parseExpr("""
                x match {
                  case "A" -> 1
                  case is Number -> 2
                  case s matches /a(b)/ -> s[1]
                  case n if n > 3 -> 3
                  else -> 4
                }
                """));
        assertEquals(5, match.cases().size());
        assertEquals("A", assertInstanceOf(StringLit.class, match.cases().get(0).literal()).value());
        assertEquals("Number", match.cases().get(1).type());
        assertEquals("a(b)", match.cases().get(2).regex());
        assertEquals("s", match.cases().get(2).binding());
        assertEquals("n", match.cases().get(3).binding());
        assertInstanceOf(BinaryOp.class, match.cases().get(3).guard());
        assertTrue(match.cases().get(4).otherwise());
    }

    @Test
    void shouldParseDoBlock() {
        Block block = assertInstanceOf(Block.class, parseExpr("do { var x = 1\n fun f(y) = y\n ---\n f(x) }"));
        assertEquals(2, block.declarations().size());
    }

    @Test
    void shouldParseTypeCoercionAndCheck() {
        TypeCoercion tc = assertInstanceOf(TypeCoercion.class, parseExpr("x as Date {format: \"yyyy-MM-dd\"}"));
        assertEquals("Date", tc.type());
        assertEquals(Map.of("format", "yyyy-MM-dd"), tc.properties());
        assertEquals("String", assertInstanceOf(TypeCheck.class, parseExpr("x is String")).type());
    }

    @Test
    void shouldParseQualifiedFunctionName() {
        assertEquals("camelize", assertInstanceOf(FunctionCall.class, parseExpr("Strings::camelize(x)")).name());
    }

    @Test
    void shouldKeepParenthesizedGrouping() {
        Parens parens = assertInstanceOf(Parens.class, parseExpr("(a + b)"));
        assertEquals("+", assertInstanceOf(BinaryOp.class, parens.expr()).op());
    }

    @Test
    void shouldRepresentUpdateAsUnsupported() {
        Unsupported node = assertInstanceOf(Unsupported.class, parseExpr("payload update { case .a -> 1 }"));
        assertEquals("update operator", node.reason());
    }

    // -- Errors

    @Test
    void shouldRejectInputAfterTheExpression() {
        assertThrows(DataWeaveConversionException.class, () -> parseExpr("{ a: 1 } foo bar baz"));
        assertThrows(DataWeaveConversionException.class, () -> parseExpr("payload )"));
    }

    @Test
    void shouldRejectUnbalancedInput() {
        assertThrows(DataWeaveConversionException.class, () -> parseExpr("(a + b"));
        assertThrows(DataWeaveConversionException.class, () -> parseExpr("{ a: 1"));
    }

    @Test
    void shouldReportPosition() {
        DataWeaveConversionException e
                = assertThrows(DataWeaveConversionException.class, () -> parseScript("%dw 2.0\n---\n{ a: 1,,"));
        assertTrue(e.getMessage().contains("3:8"), e.getMessage());
    }
}
