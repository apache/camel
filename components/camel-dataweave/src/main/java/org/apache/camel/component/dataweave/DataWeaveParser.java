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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.component.dataweave.DataWeaveAst.AllAttributes;
import org.apache.camel.component.dataweave.DataWeaveAst.ArrayLit;
import org.apache.camel.component.dataweave.DataWeaveAst.AttributeAccess;
import org.apache.camel.component.dataweave.DataWeaveAst.BinaryOp;
import org.apache.camel.component.dataweave.DataWeaveAst.Block;
import org.apache.camel.component.dataweave.DataWeaveAst.BooleanLit;
import org.apache.camel.component.dataweave.DataWeaveAst.DefaultExpr;
import org.apache.camel.component.dataweave.DataWeaveAst.DescendantSelector;
import org.apache.camel.component.dataweave.DataWeaveAst.Dollar;
import org.apache.camel.component.dataweave.DataWeaveAst.ExistenceCheck;
import org.apache.camel.component.dataweave.DataWeaveAst.FieldAccess;
import org.apache.camel.component.dataweave.DataWeaveAst.FilterSelector;
import org.apache.camel.component.dataweave.DataWeaveAst.FunDecl;
import org.apache.camel.component.dataweave.DataWeaveAst.FunctionCall;
import org.apache.camel.component.dataweave.DataWeaveAst.Header;
import org.apache.camel.component.dataweave.DataWeaveAst.Identifier;
import org.apache.camel.component.dataweave.DataWeaveAst.IfElse;
import org.apache.camel.component.dataweave.DataWeaveAst.IndexAccess;
import org.apache.camel.component.dataweave.DataWeaveAst.InputDecl;
import org.apache.camel.component.dataweave.DataWeaveAst.Interpolation;
import org.apache.camel.component.dataweave.DataWeaveAst.Lambda;
import org.apache.camel.component.dataweave.DataWeaveAst.LambdaParam;
import org.apache.camel.component.dataweave.DataWeaveAst.Match;
import org.apache.camel.component.dataweave.DataWeaveAst.MatchCase;
import org.apache.camel.component.dataweave.DataWeaveAst.MultiValueSelector;
import org.apache.camel.component.dataweave.DataWeaveAst.NullLit;
import org.apache.camel.component.dataweave.DataWeaveAst.NumberLit;
import org.apache.camel.component.dataweave.DataWeaveAst.ObjectEntry;
import org.apache.camel.component.dataweave.DataWeaveAst.ObjectLit;
import org.apache.camel.component.dataweave.DataWeaveAst.Parens;
import org.apache.camel.component.dataweave.DataWeaveAst.QName;
import org.apache.camel.component.dataweave.DataWeaveAst.QualifiedFieldAccess;
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
import org.apache.camel.component.dataweave.DataWeaveLexer.Token;
import org.apache.camel.component.dataweave.DataWeaveLexer.TokenType;

/**
 * Recursive descent parser for DataWeave 2.0 scripts producing {@link DataWeaveAst} nodes.
 * <p>
 * Operator precedence follows DataWeave, from lowest to highest: lambda ({@code (x) -> body}, where the body extends as
 * far as possible), {@code if/else} and {@code unless/otherwise}, infix functions ({@code map}, {@code filter},
 * {@code ++}, {@code contains}, ... and any other function called as {@code a f b}; left associative), {@code default},
 * {@code or}, {@code and}, equality, relational and {@code is}, additive, multiplicative, unary ({@code -},
 * {@code not}), {@code as}, and selectors.
 * <p>
 * The parser fails with a {@link DataWeaveConversionException} on any syntax it does not understand, including input
 * left over after the expression, so a script is never converted partially.
 */
public class DataWeaveParser {

    // Identifiers that are keywords, and so never the name of an infix function
    private static final Set<String> KEYWORDS = Set.of(
            "if", "else", "unless", "otherwise", "default", "as", "is", "with", "case", "do", "using", "var", "fun",
            "import", "ns", "type", "output", "input", "from", "to", "match", "update");

    private static final Set<String> HEADER_DIRECTIVES = Set.of("var", "fun", "type", "import", "ns", "output", "input");

    private final List<Token> tokens;
    private int pos;

    public DataWeaveParser(List<Token> tokens) {
        this.tokens = tokens;
        this.pos = 0;
    }

    /**
     * Parses a complete script: an optional header (directives and declarations ending with {@code ---}) and the body.
     */
    public DataWeaveAst parse() {
        Header header = new Header("2.0", null, Map.of(), List.of(), List.of(), Map.of());
        List<DataWeaveAst> declarations = new ArrayList<>();
        if (hasHeaderSeparator()) {
            header = parseHeader(declarations);
        }
        DataWeaveAst body = parseExpression();
        expectEnd();
        if (!declarations.isEmpty()) {
            body = new Block(declarations, body);
        }
        return new Script(header, body);
    }

    /**
     * Parses an expression without header (declarations are in a header or a {@code do} block).
     */
    public DataWeaveAst parseExpressionOnly() {
        DataWeaveAst body = parseExpression();
        expectEnd();
        return body;
    }

    // -- Header

    private boolean hasHeaderSeparator() {
        int depth = 0;
        for (Token t : tokens) {
            switch (t.type()) {
                case LPAREN, LBRACE, LBRACKET -> depth++;
                case RPAREN, RBRACE, RBRACKET -> depth--;
                case HEADER_SEPARATOR -> {
                    if (depth == 0) {
                        return true;
                    }
                }
                default -> {
                    // continue
                }
            }
        }
        return false;
    }

    private Header parseHeader(List<DataWeaveAst> declarations) {
        String version = "2.0";
        String outputType = null;
        Map<String, String> outputProperties = new LinkedHashMap<>();
        List<InputDecl> inputs = new ArrayList<>();
        List<String> imports = new ArrayList<>();
        Map<String, String> namespaces = new LinkedHashMap<>();

        while (!check(TokenType.HEADER_SEPARATOR)) {
            Token start = current();
            if (check(TokenType.PERCENT)) {
                advance();
                if (!checkIdentifier("dw")) {
                    throw error("expected %dw directive");
                }
                advance();
                version = expect(TokenType.NUMBER).value();
            } else if (checkIdentifier("output")) {
                advance();
                outputType = parseMediaType();
                outputProperties.putAll(parseDirectiveProperties(start.line()));
            } else if (checkIdentifier("input")) {
                advance();
                String name = expectName();
                String mediaType = parseMediaType();
                parseDirectiveProperties(start.line());
                inputs.add(new InputDecl(name, mediaType));
            } else if (checkIdentifier("import")) {
                imports.add(parseImport());
            } else if (checkIdentifier("ns")) {
                // ns prefix uri (the lexer reads the URI as a string)
                advance();
                String prefix = expectName();
                namespaces.put(prefix, expect(TokenType.STRING).value());
            } else if (checkIdentifier("type")) {
                skipTypeDeclaration();
            } else if (checkIdentifier("var") || checkIdentifier("fun")) {
                declarations.add(parseDeclaration());
            } else {
                throw error("unexpected " + describe(current()) + " in the header");
            }
        }
        advance(); // ---
        return new Header(version, outputType, outputProperties, inputs, imports, namespaces);
    }

    private String parseMediaType() {
        // application/json, application/xml, text/plain, application/x-www-form-urlencoded, json, ...
        StringBuilder sb = new StringBuilder(expectName());
        while ((check(TokenType.SLASH) || check(TokenType.MINUS) || check(TokenType.PLUS) || check(TokenType.DOT))
                && isAdjacent()) {
            sb.append(current().value());
            advance();
            if (check(TokenType.IDENTIFIER) && isAdjacent()) {
                sb.append(current().value());
                advance();
            }
        }
        return sb.toString();
    }

    // Writer and reader properties after the media type: output application/csv header=false, separator=";"
    private Map<String, String> parseDirectiveProperties(int line) {
        Map<String, String> properties = new LinkedHashMap<>();
        while (current().line() == line && check(TokenType.IDENTIFIER)) {
            String name = current().value();
            advance();
            expect(TokenType.ASSIGN);
            properties.put(name, parseStringValue());
            if (check(TokenType.COMMA)) {
                advance();
            }
        }
        return properties;
    }

    private String parseImport() {
        // import dw::core::Strings | import * from dw::core::Strings | import camelize, dasherize from dw::core::Strings
        // | import dw::core::Strings as Str
        int line = current().line();
        advance(); // import
        StringBuilder module = new StringBuilder();
        while (current().line() == line && !check(TokenType.EOF) && !check(TokenType.HEADER_SEPARATOR)) {
            if (checkIdentifier("as")) {
                advance(); // as
                advance(); // alias
                continue;
            }
            if (checkIdentifier("from") || check(TokenType.COMMA) || check(TokenType.STAR)) {
                module.setLength(0);
            } else if (check(TokenType.IDENTIFIER) || check(TokenType.DOUBLE_COLON)) {
                module.append(current().value());
            }
            advance();
        }
        return module.toString();
    }

    private void skipTypeDeclaration() {
        // type Name = <type expression>, possibly over several lines; it ends at the next directive or ---
        advance(); // type
        while (!check(TokenType.EOF) && !check(TokenType.HEADER_SEPARATOR)) {
            if (isFirstOnLine() && check(TokenType.IDENTIFIER) && HEADER_DIRECTIVES.contains(current().value())) {
                return;
            }
            advance();
        }
    }

    private boolean isFirstOnLine() {
        return pos == 0 || tokens.get(pos - 1).line() < current().line();
    }

    // -- Declarations

    private DataWeaveAst parseDeclaration() {
        if (checkIdentifier("var")) {
            advance(); // var
            String name = expectName();
            if (check(TokenType.COLON)) {
                advance();
                skipTypeExpression();
            }
            expect(TokenType.ASSIGN);
            return new VarDecl(name, parseExpression());
        }
        advance(); // fun
        String name = expectName();
        if (check(TokenType.LT)) {
            skipGenerics();
        }
        expect(TokenType.LPAREN);
        List<LambdaParam> params = parseParams();
        if (check(TokenType.COLON)) {
            advance();
            skipTypeExpression(); // return type
        }
        expect(TokenType.ASSIGN);
        return new FunDecl(name, params, parseExpression());
    }

    // The parameters after the opening parenthesis, up to and including the closing one: (a, b: Number, c = 1)
    private List<LambdaParam> parseParams() {
        List<LambdaParam> params = new ArrayList<>();
        while (!check(TokenType.RPAREN)) {
            String name = expectName();
            if (check(TokenType.COLON)) {
                advance();
                skipTypeExpression();
            }
            DataWeaveAst defaultValue = null;
            if (check(TokenType.ASSIGN)) {
                advance();
                defaultValue = parseExpression();
            }
            params.add(new LambdaParam(name, defaultValue));
            if (!check(TokenType.RPAREN)) {
                expect(TokenType.COMMA);
            }
        }
        expect(TokenType.RPAREN);
        return params;
    }

    // -- Expressions

    private DataWeaveAst parseExpression() {
        if (check(TokenType.LPAREN) && isLambdaAhead()) {
            return parseLambda();
        }
        return parseConditional();
    }

    private boolean isLambdaAhead() {
        // ( ... ) -> with balanced parentheses
        int depth = 0;
        for (int i = pos; i < tokens.size(); i++) {
            TokenType type = tokens.get(i).type();
            if (type == TokenType.LPAREN || type == TokenType.LBRACKET || type == TokenType.LBRACE) {
                depth++;
            } else if (type == TokenType.RPAREN || type == TokenType.RBRACKET || type == TokenType.RBRACE) {
                depth--;
                if (depth == 0) {
                    return i + 1 < tokens.size() && tokens.get(i + 1).type() == TokenType.ARROW;
                }
            } else if (type == TokenType.EOF) {
                return false;
            }
        }
        return false;
    }

    private Lambda parseLambda() {
        expect(TokenType.LPAREN);
        List<LambdaParam> params = parseParams();
        expect(TokenType.ARROW);
        return new Lambda(params, parseExpression());
    }

    private DataWeaveAst parseConditional() {
        if (checkIdentifier("if")) {
            advance();
            DataWeaveAst condition = parseCondition();
            DataWeaveAst thenExpr = parseExpression();
            DataWeaveAst elseExpr = null;
            if (checkIdentifier("else")) {
                advance();
                elseExpr = parseExpression();
            }
            return new IfElse(condition, thenExpr, elseExpr);
        }
        if (checkIdentifier("unless")) {
            // unless (cond) a else b  is  if (cond) b else a  (DataWeave 1 has otherwise instead of else)
            advance();
            DataWeaveAst condition = parseCondition();
            DataWeaveAst unlessExpr = parseExpression();
            if (checkIdentifier("otherwise")) {
                advance();
            } else {
                expectIdentifier("else");
            }
            return new IfElse(condition, parseExpression(), unlessExpr);
        }
        return parseInfix();
    }

    private DataWeaveAst parseCondition() {
        expect(TokenType.LPAREN);
        DataWeaveAst condition = parseExpression();
        expect(TokenType.RPAREN);
        return condition;
    }

    private DataWeaveAst parseInfix() {
        DataWeaveAst left = parseDefault();
        while (true) {
            if (check(TokenType.PLUSPLUS)) {
                advance();
                left = new BinaryOp("++", left, parseDefault());
            } else if (check(TokenType.MINUSMINUS)) {
                advance();
                left = new BinaryOp("--", left, parseDefault());
            } else if (checkIdentifier("to")) {
                advance();
                left = new Range(left, parseDefault());
            } else if (checkIdentifier("match")) {
                advance();
                left = parseMatch(left);
            } else if (checkIdentifier("update")) {
                left = new Unsupported(skipBlock("update"), "update operator");
            } else if (checkIdentifier("replace")) {
                advance();
                DataWeaveAst target = parseInfixOperand();
                expectIdentifier("with");
                left = new FunctionCall("replace", List.of(left, target, parseInfixOperand()));
            } else if (check(TokenType.IDENTIFIER) && !KEYWORDS.contains(current().value())) {
                String name = parseQualifiedName();
                left = new FunctionCall(name, List.of(left, parseInfixOperand()));
            } else {
                return left;
            }
        }
    }

    private DataWeaveAst parseInfixOperand() {
        if (check(TokenType.LPAREN) && isLambdaAhead()) {
            return parseLambda();
        }
        return parseDefault();
    }

    private DataWeaveAst parseDefault() {
        DataWeaveAst expr = parseOr();
        while (checkIdentifier("default")) {
            advance();
            expr = new DefaultExpr(expr, parseOr());
        }
        return expr;
    }

    private DataWeaveAst parseOr() {
        DataWeaveAst left = parseAnd();
        while (check(TokenType.OR)) {
            advance();
            left = new BinaryOp("or", left, parseAnd());
        }
        return left;
    }

    private DataWeaveAst parseAnd() {
        DataWeaveAst left = parseEquality();
        while (check(TokenType.AND)) {
            advance();
            left = new BinaryOp("and", left, parseEquality());
        }
        return left;
    }

    private DataWeaveAst parseEquality() {
        DataWeaveAst left = parseRelational();
        while (check(TokenType.EQ) || check(TokenType.NEQ) || check(TokenType.SIMILAR)) {
            String op = current().value();
            advance();
            left = new BinaryOp(op, left, parseRelational());
        }
        return left;
    }

    private DataWeaveAst parseRelational() {
        DataWeaveAst left = parseAdditive();
        while (true) {
            if (check(TokenType.GT) || check(TokenType.GE) || check(TokenType.LT) || check(TokenType.LE)) {
                String op = current().value();
                advance();
                left = new BinaryOp(op, left, parseAdditive());
            } else if (checkIdentifier("is")) {
                advance();
                left = new TypeCheck(left, parseTypeName());
            } else {
                return left;
            }
        }
    }

    private DataWeaveAst parseAdditive() {
        DataWeaveAst left = parseMultiplicative();
        while (check(TokenType.PLUS) || check(TokenType.MINUS)) {
            String op = current().value();
            advance();
            left = new BinaryOp(op, left, parseMultiplicative());
        }
        return left;
    }

    private DataWeaveAst parseMultiplicative() {
        DataWeaveAst left = parseUnary();
        while (check(TokenType.STAR) || check(TokenType.SLASH)) {
            String op = current().value();
            advance();
            left = new BinaryOp(op, left, parseUnary());
        }
        return left;
    }

    private DataWeaveAst parseUnary() {
        if (check(TokenType.NOT)) {
            advance();
            return new UnaryOp("not", parseUnary());
        }
        if (check(TokenType.MINUS)) {
            advance();
            return new UnaryOp("-", parseUnary());
        }
        return parseCoercion();
    }

    private DataWeaveAst parseCoercion() {
        DataWeaveAst expr = parsePostfix(parsePrimary());
        while (checkIdentifier("as")) {
            advance();
            String type = parseTypeName();
            Map<String, String> properties = new LinkedHashMap<>();
            if (check(TokenType.LBRACE)) {
                advance();
                while (!check(TokenType.RBRACE)) {
                    String name = expectName();
                    expect(TokenType.COLON);
                    properties.put(name, parseStringValue());
                    if (!check(TokenType.RBRACE)) {
                        expect(TokenType.COMMA);
                    }
                }
                advance(); // }
            }
            expr = new TypeCoercion(expr, type, properties);
        }
        return expr;
    }

    private String parseStringValue() {
        Token token = current();
        if (token.type() == TokenType.STRING) {
            advance();
            return unescape(token.value(), token);
        }
        if (token.type() == TokenType.NUMBER || token.type() == TokenType.BOOLEAN
                || token.type() == TokenType.IDENTIFIER) {
            advance();
            return token.value();
        }
        throw error("expected a literal value but found " + describe(token));
    }

    private String parseTypeName() {
        String type = expectName();
        while (check(TokenType.DOUBLE_COLON)) {
            advance();
            type = expectName();
        }
        if (check(TokenType.LT)) {
            skipGenerics();
        }
        return type;
    }

    // -- Selectors

    private DataWeaveAst parsePostfix(DataWeaveAst expr) {
        while (true) {
            if (check(TokenType.DOT) && isTokenAhead(1, TokenType.DOT)) {
                advance(); // .
                advance(); // .
                if (check(TokenType.STAR)) {
                    advance(); // *
                    expr = new DescendantSelector(expr, expectSelectorName(), true);
                } else if (check(TokenType.AT)) {
                    advance(); // @
                    String name = "";
                    if (check(TokenType.IDENTIFIER)) {
                        name = current().value();
                        advance();
                    }
                    expr = new Unsupported("..@" + name, "descendants selector ..@");
                } else {
                    expr = new DescendantSelector(expr, expectSelectorName());
                }
            } else if (check(TokenType.DOT)) {
                advance(); // .
                expr = parseDotSelector(expr);
            } else if (check(TokenType.LBRACKET)) {
                advance(); // [
                if (check(TokenType.QUESTION)) {
                    advance(); // ?
                    expect(TokenType.LPAREN);
                    DataWeaveAst condition = parseExpression();
                    expect(TokenType.RPAREN);
                    expect(TokenType.RBRACKET);
                    expr = new FilterSelector(expr, condition);
                } else if (check(TokenType.AT) || check(TokenType.STAR) || check(TokenType.CARET)
                        || check(TokenType.AND)) {
                    expr = new Unsupported(skipBracket(), "selector");
                } else {
                    DataWeaveAst index = parseExpression();
                    expect(TokenType.RBRACKET);
                    expr = new IndexAccess(expr, index);
                }
            } else if (check(TokenType.QUESTION)) {
                advance();
                expr = new ExistenceCheck(expr);
            } else if (check(TokenType.NOT) && "!".equals(current().value()) && isAdjacent()) {
                // the assert-present selector payload.a! fails when the value is missing; otherwise the same value
                advance();
            } else {
                return expr;
            }
        }
    }

    private DataWeaveAst parseDotSelector(DataWeaveAst expr) {
        if (check(TokenType.AT)) {
            advance(); // @
            if (check(TokenType.IDENTIFIER) || check(TokenType.STRING)) {
                return new AttributeAccess(expr, expectSelectorName());
            }
            return new AllAttributes(expr);
        }
        if (check(TokenType.STAR)) {
            advance(); // *
            if (check(TokenType.AT)) {
                advance();
                return new Unsupported(".*@", "multi-value attribute selector .*@");
            }
            return new MultiValueSelector(expr, expectSelectorName());
        }
        if (check(TokenType.CARET)) {
            advance(); // ^
            String name = expectName();
            return new Unsupported(".^" + name, "metadata selector .^");
        }
        if (check(TokenType.HASH)) {
            advance(); // #
            return new Unsupported(".#", "namespace selector .#");
        }
        if (check(TokenType.IDENTIFIER) && isTokenAhead(1, TokenType.HASH)) {
            String prefix = expectName();
            advance(); // #
            return new QualifiedFieldAccess(expr, prefix, expectSelectorName());
        }
        return new FieldAccess(expr, expectSelectorName());
    }

    private String expectSelectorName() {
        Token token = current();
        if (token.type() == TokenType.STRING) {
            advance();
            return unescape(token.value(), token);
        }
        return expectName();
    }

    // -- Primary

    private DataWeaveAst parsePrimary() {
        Token token = current();
        switch (token.type()) {
            case STRING -> {
                advance();
                return parseString(token);
            }
            case NUMBER -> {
                advance();
                return new NumberLit(token.value());
            }
            case BOOLEAN -> {
                advance();
                return new BooleanLit("true".equals(token.value()));
            }
            case NULL_LIT -> {
                advance();
                return new NullLit();
            }
            case REGEX -> {
                advance();
                return new RegexLit(token.value());
            }
            case TEMPORAL -> {
                advance();
                return new TemporalLit(token.value());
            }
            case DOLLAR -> {
                advance();
                return new Dollar(1);
            }
            case DOLLAR_DOLLAR -> {
                advance();
                return new Dollar(2);
            }
            case DOLLAR_DOLLAR_DOLLAR -> {
                advance();
                return new Dollar(3);
            }
            case LPAREN -> {
                if (isLambdaAhead()) {
                    return parseLambda();
                }
                advance();
                DataWeaveAst expr = parseExpression();
                expect(TokenType.RPAREN);
                return expr instanceof Lambda ? expr : new Parens(expr);
            }
            case LBRACE -> {
                return parseObject();
            }
            case LBRACKET -> {
                return parseArray();
            }
            case IDENTIFIER -> {
                return parseIdentifier();
            }
            default -> throw error("unexpected " + describe(token));
        }
    }

    private DataWeaveAst parseIdentifier() {
        String name = current().value();
        if ("do".equals(name) && isTokenAhead(1, TokenType.LBRACE)) {
            return parseDoBlock();
        }
        if ("using".equals(name) && isTokenAhead(1, TokenType.LPAREN)) {
            return parseUsing();
        }
        if ("if".equals(name) || "unless".equals(name)) {
            return parseConditional();
        }
        name = parseQualifiedName();
        // f(x) is a call, also with a space before the parenthesis unless that starts a lambda
        if (check(TokenType.LPAREN) && (isAdjacent() || !isLambdaAhead())) {
            advance(); // (
            List<DataWeaveAst> args = new ArrayList<>();
            while (!check(TokenType.RPAREN)) {
                args.add(parseExpression());
                if (!check(TokenType.RPAREN)) {
                    expect(TokenType.COMMA);
                }
            }
            advance(); // )
            return new FunctionCall(name, args);
        }
        return new Identifier(name);
    }

    // Strings::camelize is camelize (the converter knows the functions of the DataWeave modules by name)
    private String parseQualifiedName() {
        String name = expectName();
        while (check(TokenType.DOUBLE_COLON)) {
            advance();
            name = expectName();
        }
        return name;
    }

    private DataWeaveAst parseDoBlock() {
        advance(); // do
        expect(TokenType.LBRACE);
        List<DataWeaveAst> declarations = new ArrayList<>();
        while (!check(TokenType.HEADER_SEPARATOR)) {
            if (checkIdentifier("var") || checkIdentifier("fun")) {
                declarations.add(parseDeclaration());
            } else if (checkIdentifier("type")) {
                skipTypeDeclaration();
            } else if (declarations.isEmpty()) {
                // do { expr } without declarations
                DataWeaveAst body = parseExpression();
                expect(TokenType.RBRACE);
                return new Parens(body);
            } else {
                throw error("expected --- in do block but found " + describe(current()));
            }
        }
        advance(); // ---
        DataWeaveAst body = parseExpression();
        expect(TokenType.RBRACE);
        return new Block(declarations, body);
    }

    private DataWeaveAst parseUsing() {
        advance(); // using
        expect(TokenType.LPAREN);
        List<DataWeaveAst> declarations = new ArrayList<>();
        while (!check(TokenType.RPAREN)) {
            String name = expectName();
            expect(TokenType.ASSIGN);
            declarations.add(new VarDecl(name, parseExpression()));
            if (!check(TokenType.RPAREN)) {
                expect(TokenType.COMMA);
            }
        }
        advance(); // )
        return new Block(declarations, parseExpression());
    }

    private DataWeaveAst parseObject() {
        expect(TokenType.LBRACE);
        List<ObjectEntry> entries = new ArrayList<>();
        while (!check(TokenType.RBRACE)) {
            if (check(TokenType.LPAREN) && isConditionalEntriesAhead()) {
                // (key: value, ...) if condition
                advance(); // (
                List<ObjectEntry> group = new ArrayList<>();
                while (!check(TokenType.RPAREN)) {
                    group.add(parseObjectEntry());
                    if (!check(TokenType.RPAREN)) {
                        expect(TokenType.COMMA);
                    }
                }
                advance(); // )
                DataWeaveAst condition = parseEntryCondition();
                for (ObjectEntry entry : group) {
                    entries.add(new ObjectEntry(entry.key(), entry.value(), entry.dynamic(), condition, entry.attributes()));
                }
            } else if (check(TokenType.LPAREN)) {
                // (expr): value is a dynamic key, and (expr) alone an object spread
                advance(); // (
                DataWeaveAst expr = parseExpression();
                expect(TokenType.RPAREN);
                if (check(TokenType.AT)) {
                    List<ObjectEntry> attributes = parseAttributes();
                    expect(TokenType.COLON);
                    entries.add(new ObjectEntry(expr, parseExpression(), true, null, attributes));
                } else if (check(TokenType.COLON)) {
                    advance();
                    entries.add(new ObjectEntry(expr, parseExpression(), true, null));
                } else {
                    entries.add(new ObjectEntry(null, expr, false, parseEntryCondition()));
                }
            } else {
                entries.add(parseObjectEntry());
            }
            if (!check(TokenType.RBRACE)) {
                expect(TokenType.COMMA);
            }
        }
        advance(); // }
        return new ObjectLit(entries);
    }

    private DataWeaveAst parseEntryCondition() {
        if (checkIdentifier("if")) {
            advance();
            return parseInfix();
        }
        return null;
    }

    // ( key : ... at the start of a parenthesised group of entries
    private boolean isConditionalEntriesAhead() {
        Token key = peekAhead(1);
        Token next = peekAhead(2);
        if (key == null || next == null) {
            return false;
        }
        boolean keyLike = key.type() == TokenType.IDENTIFIER || key.type() == TokenType.STRING
                || key.type() == TokenType.BOOLEAN || key.type() == TokenType.NULL_LIT;
        return keyLike && (next.type() == TokenType.COLON || next.type() == TokenType.HASH
                || next.type() == TokenType.AT);
    }

    private ObjectEntry parseObjectEntry() {
        Token token = current();
        DataWeaveAst key;
        boolean dynamic = false;
        if (token.type() == TokenType.STRING) {
            advance();
            key = parseString(token);
            dynamic = key instanceof Interpolation;
        } else if (token.type() == TokenType.LPAREN) {
            advance();
            key = parseExpression();
            expect(TokenType.RPAREN);
            dynamic = true;
        } else {
            key = new StringLit(expectName());
        }
        if (check(TokenType.HASH) && !dynamic && key instanceof StringLit prefix) {
            // prefix#name: a name in an XML namespace
            advance(); // #
            key = new QName(prefix.value(), expectName());
        }
        List<ObjectEntry> attributes = check(TokenType.AT) ? parseAttributes() : List.of();
        expect(TokenType.COLON);
        return new ObjectEntry(key, parseExpression(), dynamic, null, attributes);
    }

    // The XML attributes of a key: @(name: value, ...)
    private List<ObjectEntry> parseAttributes() {
        advance(); // @
        expect(TokenType.LPAREN);
        List<ObjectEntry> attributes = new ArrayList<>();
        while (!check(TokenType.RPAREN)) {
            ObjectEntry attribute = parseObjectEntry();
            if (!attribute.attributes().isEmpty()) {
                throw error("an attribute has no attributes");
            }
            attributes.add(attribute);
            if (!check(TokenType.RPAREN)) {
                expect(TokenType.COMMA);
            }
        }
        advance(); // )
        return attributes;
    }

    private DataWeaveAst parseArray() {
        expect(TokenType.LBRACKET);
        List<DataWeaveAst> elements = new ArrayList<>();
        while (!check(TokenType.RBRACKET)) {
            elements.add(parseExpression());
            if (!check(TokenType.RBRACKET)) {
                expect(TokenType.COMMA);
            }
        }
        advance(); // ]
        return new ArrayLit(elements);
    }

    private DataWeaveAst parseMatch(DataWeaveAst expr) {
        expect(TokenType.LBRACE);
        List<MatchCase> cases = new ArrayList<>();
        while (!check(TokenType.RBRACE)) {
            if (checkIdentifier("else")) {
                advance();
                expect(TokenType.ARROW);
                cases.add(new MatchCase(null, null, null, null, null, parseExpression(), true));
                continue;
            }
            expectIdentifier("case");
            String binding = null;
            DataWeaveAst literal = null;
            String type = null;
            String regex = null;
            if (check(TokenType.IDENTIFIER) && !KEYWORDS.contains(current().value())
                    && !"matches".equals(current().value())) {
                binding = current().value();
                advance();
                if (check(TokenType.COLON)) {
                    // case x: "literal" -> binds the matched literal
                    advance();
                    literal = parseInfix();
                }
            }
            if (checkIdentifier("is")) {
                advance();
                type = parseTypeName();
            } else if (checkIdentifier("matches")) {
                advance();
                regex = expect(TokenType.REGEX).value();
            } else if (check(TokenType.REGEX)) {
                regex = current().value();
                advance();
            } else if (binding == null) {
                literal = parseInfix();
            }
            DataWeaveAst guard = null;
            if (checkIdentifier("if")) {
                advance();
                guard = parseInfix();
            }
            expect(TokenType.ARROW);
            cases.add(new MatchCase(binding, literal, type, regex, guard, parseExpression(), false));
        }
        advance(); // }
        return new Match(expr, cases);
    }

    // -- String literals

    // A string with $(expression), $name, $, $$ or $$$ is interpolated; \$ is a dollar
    private DataWeaveAst parseString(Token token) {
        String raw = token.value();
        if (!hasInterpolation(raw)) {
            return new StringLit(unescape(raw, token));
        }
        List<DataWeaveAst> parts = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        int i = 0;
        while (i < raw.length()) {
            char ch = raw.charAt(i);
            if (ch == '\\' && i + 1 < raw.length()) {
                text.append(ch).append(raw.charAt(i + 1));
                i += 2;
                continue;
            }
            if (ch != '$') {
                text.append(ch);
                i++;
                continue;
            }
            if (!text.isEmpty()) {
                parts.add(new StringLit(unescape(text.toString(), token)));
                text.setLength(0);
            }
            if (i + 1 < raw.length() && raw.charAt(i + 1) == '(') {
                int end = findInterpolationEnd(raw, i + 1, token);
                String inner = raw.substring(i + 2, end);
                parts.add(new DataWeaveParser(new DataWeaveLexer(inner).tokenize()).parseExpressionOnly());
                i = end + 1;
            } else if (i + 1 < raw.length() && (Character.isLetter(raw.charAt(i + 1)) || raw.charAt(i + 1) == '_')) {
                int end = i + 1;
                while (end < raw.length() && (Character.isLetterOrDigit(raw.charAt(end)) || raw.charAt(end) == '_')) {
                    end++;
                }
                parts.add(new Identifier(raw.substring(i + 1, end)));
                i = end;
            } else {
                int level = 1;
                while (level < 3 && i + level < raw.length() && raw.charAt(i + level) == '$') {
                    level++;
                }
                parts.add(new Dollar(level));
                i += level;
            }
        }
        if (!text.isEmpty()) {
            parts.add(new StringLit(unescape(text.toString(), token)));
        }
        return new Interpolation(parts);
    }

    private static boolean hasInterpolation(String raw) {
        for (int i = 0; i < raw.length(); i++) {
            if (raw.charAt(i) == '\\') {
                i++;
            } else if (raw.charAt(i) == '$') {
                return true;
            }
        }
        return false;
    }

    private static int findInterpolationEnd(String raw, int open, Token token) {
        int depth = 0;
        char quote = 0;
        for (int i = open; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (quote != 0) {
                if (ch == '\\') {
                    i++;
                } else if (ch == quote) {
                    quote = 0;
                }
            } else if (ch == '"' || ch == '\'') {
                quote = ch;
            } else if (ch == '(') {
                depth++;
            } else if (ch == ')' && --depth == 0) {
                return i;
            }
        }
        throw errorAt(token, "unterminated string interpolation");
    }

    private static String unescape(String raw, Token token) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (ch != '\\' || i + 1 >= raw.length()) {
                sb.append(ch);
                continue;
            }
            char next = raw.charAt(++i);
            switch (next) {
                case 'n' -> sb.append('\n');
                case 't' -> sb.append('\t');
                case 'r' -> sb.append('\r');
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'u' -> {
                    if (i + 4 >= raw.length()) {
                        throw errorAt(token, "invalid unicode escape in string");
                    }
                    sb.append((char) Integer.parseInt(raw.substring(i + 1, i + 5), 16));
                    i += 4;
                }
                default -> sb.append(next); // \" \' \\ \$ \/ \`
            }
        }
        return sb.toString();
    }

    // -- Type expressions (skipped: they do not change the converted result)

    private void skipTypeExpression() {
        if (check(TokenType.LBRACE)) {
            skipBalanced(TokenType.LBRACE, TokenType.RBRACE);
        } else if (check(TokenType.LPAREN)) {
            // a function type: (a: String) -> Number
            skipBalanced(TokenType.LPAREN, TokenType.RPAREN);
            if (check(TokenType.ARROW)) {
                advance();
                skipTypeExpression();
            }
        } else {
            parseTypeName();
        }
        if (check(TokenType.PIPE)) {
            advance();
            skipTypeExpression();
        }
    }

    private void skipGenerics() {
        int depth = 0;
        do {
            if (check(TokenType.LT)) {
                depth++;
            } else if (check(TokenType.GT)) {
                depth--;
            } else if (check(TokenType.EOF)) {
                throw error("unterminated type parameters");
            }
            advance();
        } while (depth > 0);
    }

    private void skipBalanced(TokenType open, TokenType close) {
        int depth = 0;
        do {
            if (check(open)) {
                depth++;
            } else if (check(close)) {
                depth--;
            } else if (check(TokenType.EOF)) {
                throw error("unbalanced " + open);
            }
            advance();
        } while (depth > 0);
    }

    private String skipBlock(String keyword) {
        StringBuilder text = new StringBuilder(keyword).append(' ');
        advance(); // keyword
        if (!check(TokenType.LBRACE)) {
            throw error("expected { after " + keyword);
        }
        int depth = 0;
        do {
            if (check(TokenType.LBRACE)) {
                depth++;
            } else if (check(TokenType.RBRACE)) {
                depth--;
            } else if (check(TokenType.EOF)) {
                throw error("unterminated " + keyword + " block");
            }
            text.append(current().value()).append(' ');
            advance();
        } while (depth > 0);
        return text.toString().trim();
    }

    private String skipBracket() {
        StringBuilder text = new StringBuilder("[");
        while (!check(TokenType.RBRACKET)) {
            if (check(TokenType.EOF)) {
                throw error("unterminated selector");
            }
            text.append(current().value());
            advance();
        }
        advance(); // ]
        return text.append(']').toString();
    }

    // -- Token helpers

    private Token current() {
        return tokens.get(pos);
    }

    private Token previous() {
        return tokens.get(Math.max(0, pos - 1));
    }

    private Token peekAhead(int offset) {
        int idx = pos + offset;
        return idx < tokens.size() ? tokens.get(idx) : null;
    }

    private boolean isTokenAhead(int offset, TokenType type) {
        Token token = peekAhead(offset);
        return token != null && token.type() == type;
    }

    // True if the current token directly follows the previous one, without whitespace
    private boolean isAdjacent() {
        Token prev = previous();
        Token cur = current();
        return prev.line() == cur.line() && prev.col() + prev.value().length() == cur.col();
    }

    private boolean check(TokenType type) {
        return current().type() == type;
    }

    private boolean checkIdentifier(String name) {
        return check(TokenType.IDENTIFIER) && name.equals(current().value());
    }

    private void advance() {
        if (pos < tokens.size() - 1) {
            pos++;
        }
    }

    private Token expect(TokenType type) {
        Token token = current();
        if (token.type() != type) {
            throw error("expected " + type + " but found " + describe(token));
        }
        advance();
        return token;
    }

    private void expectIdentifier(String name) {
        if (!checkIdentifier(name)) {
            throw error("expected '" + name + "' but found " + describe(current()));
        }
        advance();
    }

    // A name: an identifier, or a word with its own token type (such as a field named "null" or "and")
    private String expectName() {
        Token token = current();
        if (token.type() == TokenType.IDENTIFIER || token.type() == TokenType.BOOLEAN
                || token.type() == TokenType.NULL_LIT || token.type() == TokenType.AND || token.type() == TokenType.OR
                || token.type() == TokenType.NOT && !"!".equals(token.value())) {
            advance();
            return token.value();
        }
        throw error("expected a name but found " + describe(token));
    }

    private void expectEnd() {
        if (!check(TokenType.EOF)) {
            throw error("unexpected " + describe(current()) + " after the end of the expression");
        }
    }

    private static String describe(Token token) {
        return token.type() == TokenType.EOF ? "end of script" : token.type() + " ('" + token.value() + "')";
    }

    private DataWeaveConversionException error(String message) {
        return errorAt(current(), message);
    }

    private static DataWeaveConversionException errorAt(Token token, String message) {
        return new DataWeaveConversionException(
                "DataWeave parse error at " + token.line() + ":" + token.col() + ": " + message);
    }
}
