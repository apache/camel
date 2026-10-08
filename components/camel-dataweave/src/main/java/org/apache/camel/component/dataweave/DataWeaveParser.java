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

import org.apache.camel.component.dataweave.DataWeaveLexer.Token;
import org.apache.camel.component.dataweave.DataWeaveLexer.TokenType;

/**
 * Recursive descent parser for DataWeave 2.0 scripts producing {@link DataWeaveAst} nodes.
 */
public class DataWeaveParser {

    private final List<Token> tokens;
    private int pos;

    public DataWeaveParser(List<Token> tokens) {
        this.tokens = tokens;
        this.pos = 0;
    }

    public DataWeaveAst parse() {
        HeaderResult hr = parseHeader();
        DataWeaveAst body = parseExpression();
        // Wrap header var/fun declarations around the body if present
        if (!hr.declarations().isEmpty()) {
            body = new DataWeaveAst.Block(hr.declarations(), body);
        }
        return new DataWeaveAst.Script(hr.header(), body);
    }

    public DataWeaveAst parseExpressionOnly() {
        return parseExpression();
    }

    // -- Header parsing --

    /** Internal record carrying both the header directives and any var/fun declarations found in the header section. */
    private record HeaderResult(DataWeaveAst.Header header, List<DataWeaveAst> declarations) {
    }

    private HeaderResult parseHeader() {
        String version = "2.0";
        String outputType = null;
        List<DataWeaveAst.InputDecl> inputs = new ArrayList<>();
        List<DataWeaveAst> declarations = new ArrayList<>();

        // Only parse header if it starts with %dw or a known header directive
        boolean hasHeader = (check(TokenType.PERCENT) && peekAhead(1) != null && "dw".equals(peekAhead(1).value()))
                || checkIdentifier("output") || checkIdentifier("input");

        if (!hasHeader) {
            // No header section -- skip directly to body
            return new HeaderResult(new DataWeaveAst.Header(version, null, inputs), declarations);
        }

        // Check for %dw directive
        if (check(TokenType.PERCENT) && peekAhead(1) != null && "dw".equals(peekAhead(1).value())) {
            advance(); // %
            advance(); // dw
            if (check(TokenType.NUMBER)) {
                version = current().value();
                advance();
            }
        }

        // Parse directives before ---
        while (!check(TokenType.HEADER_SEPARATOR) && !check(TokenType.EOF)) {
            if (checkIdentifier("output")) {
                advance(); // output
                outputType = parseMediaType();
            } else if (checkIdentifier("input")) {
                advance(); // input
                String name = current().value();
                advance();
                String mediaType = parseMediaType();
                inputs.add(new DataWeaveAst.InputDecl(name, mediaType));
            } else if (checkIdentifier("import")) {
                // Skip import directives
                while (!check(TokenType.EOF) && !checkIdentifier("output") && !checkIdentifier("input")
                        && !checkIdentifier("var") && !checkIdentifier("fun")
                        && !check(TokenType.HEADER_SEPARATOR)) {
                    advance();
                }
            } else if (checkIdentifier("var")) {
                // var declaration in header: emit as local binding in body
                advance(); // var
                String name = current().value();
                advance(); // name
                // Skip optional type annotation: var rate: Number = 0.08
                if (check(TokenType.COLON)) {
                    advance(); // :
                    skipTypeExpression();
                }
                expect(TokenType.ASSIGN); // =
                DataWeaveAst value = parseExpression();
                declarations.add(new DataWeaveAst.VarDecl(name, value, null));
            } else if (checkIdentifier("fun")) {
                // fun declaration in header: emit as local function in body
                advance(); // fun
                String name = current().value();
                advance(); // name
                expect(TokenType.LPAREN);
                List<String> params = new ArrayList<>();
                while (!check(TokenType.RPAREN) && !check(TokenType.EOF)) {
                    String paramName = current().value();
                    advance();
                    // Skip type annotation: fun f(a: Number) or (a: Array<Number>) -> skip to next param
                    if (check(TokenType.COLON)) {
                        advance(); // :
                        skipTypeExpression(); // type expression (simple, generic, or union)
                    }
                    params.add(paramName);
                    if (check(TokenType.COMMA)) {
                        advance();
                    }
                }
                expect(TokenType.RPAREN);
                // Optional return type annotation: fun f(a): Number = ...
                if (check(TokenType.COLON)) {
                    advance(); // :
                    skipTypeExpression(); // return type
                }
                expect(TokenType.ASSIGN); // =
                DataWeaveAst funBody = parseExpression();
                declarations.add(new DataWeaveAst.FunDecl(name, params, funBody, null));
            } else {
                advance(); // skip unknown header tokens
            }
        }

        if (check(TokenType.HEADER_SEPARATOR)) {
            advance(); // ---
        }

        return new HeaderResult(new DataWeaveAst.Header(version, outputType, inputs), declarations);
    }

    private String parseMediaType() {
        StringBuilder sb = new StringBuilder();
        // e.g., application/json or application/xml
        if (check(TokenType.IDENTIFIER)) {
            sb.append(current().value());
            advance();
            if (check(TokenType.SLASH)) {
                sb.append("/");
                advance();
                if (check(TokenType.IDENTIFIER)) {
                    sb.append(current().value());
                    advance();
                }
            }
        }
        return sb.toString();
    }

    // -- Expression parsing (precedence climbing) --

    private DataWeaveAst parseExpression() {
        // Handle var/fun declarations at expression level
        if (checkIdentifier("var")) {
            return parseVarDecl();
        }
        if (checkIdentifier("fun")) {
            return parseFunDecl();
        }
        if (checkIdentifier("do")) {
            return parseDoBlock();
        }
        if (checkIdentifier("using")) {
            return parseUsingBlock();
        }
        return parseIfElse();
    }

    private DataWeaveAst parseVarDecl() {
        advance(); // var
        String name = current().value();
        advance(); // name
        expect(TokenType.ASSIGN); // =
        DataWeaveAst value = parseExpression();
        // The body follows after the var declaration (next expression in sequence)
        DataWeaveAst body = null;
        if (!check(TokenType.EOF) && !check(TokenType.RPAREN) && !check(TokenType.RBRACE)
                && !check(TokenType.RBRACKET)) {
            body = parseExpression();
        }
        return new DataWeaveAst.VarDecl(name, value, body);
    }

    private DataWeaveAst parseFunDecl() {
        advance(); // fun
        String name = current().value();
        advance(); // name
        expect(TokenType.LPAREN);
        List<String> params = new ArrayList<>();
        while (!check(TokenType.RPAREN) && !check(TokenType.EOF)) {
            String paramName = current().value();
            advance();
            // Skip type annotation: fun f(a: Number) or (a: Array<Number>) -> skip to next param
            if (check(TokenType.COLON)) {
                advance(); // :
                skipTypeExpression(); // type expression (simple, generic, or union)
            }
            params.add(paramName);
            if (check(TokenType.COMMA)) {
                advance();
            }
        }
        expect(TokenType.RPAREN);
        // Optional return type annotation: fun f(a): Number = ...
        if (check(TokenType.COLON)) {
            advance(); // :
            skipTypeExpression(); // return type
        }
        expect(TokenType.ASSIGN); // =
        DataWeaveAst funBody = parseExpression();
        DataWeaveAst next = null;
        if (!check(TokenType.EOF) && !check(TokenType.RPAREN) && !check(TokenType.RBRACE)) {
            next = parseExpression();
        }
        return new DataWeaveAst.FunDecl(name, params, funBody, next);
    }

    private DataWeaveAst parseDoBlock() {
        advance(); // do
        expect(TokenType.LBRACE);
        List<DataWeaveAst> declarations = new ArrayList<>();
        while ((checkIdentifier("var") || checkIdentifier("fun")) && !check(TokenType.EOF)) {
            if (checkIdentifier("var")) {
                advance(); // var
                String name = current().value();
                advance();
                expect(TokenType.ASSIGN);
                DataWeaveAst value = parseExpression();
                declarations.add(new DataWeaveAst.VarDecl(name, value, null));
            } else {
                declarations.add(parseFunDecl());
            }
        }
        // Parse the expression part
        if (check(TokenType.HEADER_SEPARATOR)) {
            advance(); // ---
        }
        DataWeaveAst body = parseExpression();
        expect(TokenType.RBRACE);
        return new DataWeaveAst.Block(declarations, body);
    }

    private DataWeaveAst parseUsingBlock() {
        advance(); // using
        expect(TokenType.LPAREN);
        List<DataWeaveAst> declarations = new ArrayList<>();
        while (!check(TokenType.RPAREN) && !check(TokenType.EOF)) {
            String name = current().value();
            advance();
            expect(TokenType.ASSIGN);
            DataWeaveAst value = parseOr();
            declarations.add(new DataWeaveAst.VarDecl(name, value, null));
            if (check(TokenType.COMMA)) {
                advance();
            }
        }
        expect(TokenType.RPAREN);
        DataWeaveAst body = parseExpression();
        return new DataWeaveAst.Block(declarations, body);
    }

    private DataWeaveAst parseIfElse() {
        if (checkIdentifier("if")) {
            advance(); // if
            boolean hasParen = check(TokenType.LPAREN);
            if (hasParen) {
                advance();
            }
            DataWeaveAst condition = parseOr();
            if (hasParen) {
                expect(TokenType.RPAREN);
            }
            DataWeaveAst thenExpr = parseExpression();
            DataWeaveAst elseExpr = null;
            if (checkIdentifier("else")) {
                advance();
                elseExpr = parseExpression();
            }
            return new DataWeaveAst.IfElse(condition, thenExpr, elseExpr);
        }
        return parseDefault();
    }

    private DataWeaveAst parseDefault() {
        DataWeaveAst expr = parseOr();
        while (checkIdentifier("default")) {
            advance();
            DataWeaveAst fallback = parseOr();
            expr = new DataWeaveAst.DefaultExpr(expr, fallback);
        }
        return expr;
    }

    private DataWeaveAst parseOr() {
        DataWeaveAst left = parseAnd();
        while (check(TokenType.OR)) {
            advance();
            DataWeaveAst right = parseAnd();
            left = new DataWeaveAst.BinaryOp("or", left, right);
        }
        return left;
    }

    private DataWeaveAst parseAnd() {
        DataWeaveAst left = parseComparison();
        while (check(TokenType.AND)) {
            advance();
            DataWeaveAst right = parseComparison();
            left = new DataWeaveAst.BinaryOp("and", left, right);
        }
        return left;
    }

    private DataWeaveAst parseComparison() {
        DataWeaveAst left = parseConcat();
        while (check(TokenType.EQ) || check(TokenType.NEQ) || check(TokenType.GT) || check(TokenType.GE)
                || check(TokenType.LT) || check(TokenType.LE)) {
            String op = current().value();
            advance();
            DataWeaveAst right = parseConcat();
            left = new DataWeaveAst.BinaryOp(op, left, right);
        }
        return left;
    }

    private DataWeaveAst parseConcat() {
        DataWeaveAst left = parseAddition();
        while (check(TokenType.PLUSPLUS)) {
            advance();
            DataWeaveAst right = parseAddition();
            left = new DataWeaveAst.BinaryOp("++", left, right);
        }
        return left;
    }

    private DataWeaveAst parseAddition() {
        DataWeaveAst left = parseMultiplication();
        while (check(TokenType.PLUS) || check(TokenType.MINUS)) {
            String op = current().value();
            advance();
            DataWeaveAst right = parseMultiplication();
            left = new DataWeaveAst.BinaryOp(op, left, right);
        }
        return left;
    }

    private DataWeaveAst parseMultiplication() {
        DataWeaveAst left = parseUnary();
        while (check(TokenType.STAR) || check(TokenType.SLASH)) {
            String op = current().value();
            advance();
            DataWeaveAst right = parseUnary();
            left = new DataWeaveAst.BinaryOp(op, left, right);
        }
        return left;
    }

    private DataWeaveAst parseUnary() {
        if (check(TokenType.NOT)) {
            advance();
            DataWeaveAst operand = parseUnary();
            return new DataWeaveAst.UnaryOp("not", operand);
        }
        if (check(TokenType.MINUS) && !isPreviousValueLike()) {
            advance();
            DataWeaveAst operand = parseUnary();
            return new DataWeaveAst.UnaryOp("-", operand);
        }
        return parsePostfix();
    }

    private boolean isPreviousValueLike() {
        if (pos == 0) {
            return false;
        }
        Token prev = tokens.get(pos - 1);
        return prev.type() == TokenType.IDENTIFIER || prev.type() == TokenType.NUMBER
                || prev.type() == TokenType.STRING || prev.type() == TokenType.RPAREN
                || prev.type() == TokenType.RBRACKET || prev.type() == TokenType.BOOLEAN;
    }

    private DataWeaveAst parsePostfix() {
        DataWeaveAst expr = parsePrimary();
        return parsePostfixOps(expr);
    }

    private DataWeaveAst parsePostfixOps(DataWeaveAst expr) {
        while (true) {
            if (check(TokenType.DOT)) {
                advance(); // .
                if (check(TokenType.STAR)) {
                    advance(); // *
                    String field = current().value();
                    advance();
                    expr = new DataWeaveAst.MultiValueSelector(expr, field);
                } else if (check(TokenType.AT)) {
                    // .@attrName — XML attribute selector
                    advance(); // @
                    String attr = current().value();
                    advance();
                    expr = new DataWeaveAst.AttributeAccess(expr, attr);
                } else if (check(TokenType.IDENTIFIER)) {
                    String field = current().value();
                    advance();
                    expr = new DataWeaveAst.FieldAccess(expr, field);
                }
            } else if (check(TokenType.QUESTION)) {
                // expr? — existence check
                advance(); // ?
                expr = new DataWeaveAst.ExistenceCheck(expr);
            } else if (check(TokenType.LBRACKET)) {
                advance(); // [
                DataWeaveAst index = parseExpression();
                expect(TokenType.RBRACKET);
                expr = new DataWeaveAst.IndexAccess(expr, index);
            } else if (checkIdentifier("map")) {
                advance();
                DataWeaveAst lambda = parseLambdaOrShorthand();
                expr = new DataWeaveAst.MapExpr(expr, lambda);
            } else if (checkIdentifier("filter")) {
                advance();
                DataWeaveAst lambda = parseLambdaOrShorthand();
                expr = new DataWeaveAst.FilterExpr(expr, lambda);
            } else if (checkIdentifier("reduce")) {
                advance();
                DataWeaveAst lambda = parseLambdaOrShorthand();
                expr = new DataWeaveAst.ReduceExpr(expr, lambda);
            } else if (checkIdentifier("flatMap")) {
                advance();
                DataWeaveAst lambda = parseLambdaOrShorthand();
                expr = new DataWeaveAst.FlatMapExpr(expr, lambda);
            } else if (checkIdentifier("distinctBy")) {
                advance();
                DataWeaveAst lambda = parseLambdaOrShorthand();
                expr = new DataWeaveAst.DistinctByExpr(expr, lambda);
            } else if (checkIdentifier("groupBy")) {
                advance();
                DataWeaveAst lambda = parseLambdaOrShorthand();
                expr = new DataWeaveAst.GroupByExpr(expr, lambda);
            } else if (checkIdentifier("orderBy")) {
                advance();
                DataWeaveAst lambda = parseLambdaOrShorthand();
                expr = new DataWeaveAst.OrderByExpr(expr, lambda);
            } else if (checkIdentifier("as")) {
                advance(); // as
                String type = current().value();
                advance();
                String format = null;
                if (check(TokenType.LBRACE)) {
                    advance(); // {
                    if (checkIdentifier("format")) {
                        advance(); // format
                        expect(TokenType.COLON);
                        format = current().value();
                        advance();
                    }
                    expect(TokenType.RBRACE);
                }
                expr = new DataWeaveAst.TypeCoercion(expr, type, format);
            } else if (checkIdentifier("is")) {
                advance(); // is
                String type = current().value();
                advance();
                expr = new DataWeaveAst.TypeCheck(expr, type);
            } else if (checkIdentifier("contains")) {
                advance();
                DataWeaveAst sub = parsePrimary();
                expr = new DataWeaveAst.ContainsExpr(expr, sub);
            } else if (checkIdentifier("startsWith")) {
                advance();
                DataWeaveAst prefix = parsePrimary();
                expr = new DataWeaveAst.StartsWithExpr(expr, prefix);
            } else if (checkIdentifier("endsWith")) {
                advance();
                DataWeaveAst suffix = parsePrimary();
                expr = new DataWeaveAst.EndsWithExpr(expr, suffix);
            } else if (checkIdentifier("splitBy")) {
                advance();
                DataWeaveAst sep = parsePrimary();
                expr = new DataWeaveAst.SplitByExpr(expr, sep);
            } else if (checkIdentifier("joinBy")) {
                advance();
                DataWeaveAst sep = parsePrimary();
                expr = new DataWeaveAst.JoinByExpr(expr, sep);
            } else if (checkIdentifier("replace")) {
                advance();
                DataWeaveAst target = parsePrimary();
                if (checkIdentifier("with")) {
                    advance();
                }
                DataWeaveAst replacement = parsePrimary();
                expr = new DataWeaveAst.ReplaceExpr(expr, target, replacement);
            } else if (checkIdentifier("match")) {
                advance(); // match
                // Capture the match block as unsupported -- skip braces
                StringBuilder matchText = new StringBuilder("match ");
                if (check(TokenType.LBRACE)) {
                    int depth = 1;
                    matchText.append("{");
                    advance();
                    while (depth > 0 && !check(TokenType.EOF)) {
                        if (check(TokenType.LBRACE)) {
                            depth++;
                        } else if (check(TokenType.RBRACE)) {
                            depth--;
                        }
                        matchText.append(current().value());
                        if (depth > 0) {
                            matchText.append(" ");
                        }
                        advance();
                    }
                }
                expr = new DataWeaveAst.Unsupported(matchText.toString().trim(), "match expression");
            } else {
                break;
            }
        }
        return expr;
    }

    private DataWeaveAst parseLambdaOrShorthand() {
        // Lambda forms:
        // ((item) -> expr)
        // ((item, index) -> expr)
        // ((item, acc = 0) -> expr)   (for reduce)
        // $.field                      (shorthand)
        // ($ -> expr)
        if (check(TokenType.LPAREN)) {
            int savedPos = pos;
            try {
                return parseLambda();
            } catch (Exception e) {
                // If lambda parsing fails, restore and try as expression
                pos = savedPos;
                return parsePrimary();
            }
        }
        if (check(TokenType.DOLLAR)) {
            return parseDollarShorthand();
        }
        if (check(TokenType.DOLLAR_DOLLAR)) {
            advance(); // $$
            return new DataWeaveAst.DoubleDollar();
        }
        return parsePrimary();
    }

    private DataWeaveAst parseLambda() {
        expect(TokenType.LPAREN);

        // Inner parens for parameter list: ((item) -> expr) or ((item, idx) -> expr)
        boolean innerParens = check(TokenType.LPAREN);
        if (innerParens) {
            advance();
        }

        List<DataWeaveAst.LambdaParam> params = new ArrayList<>();
        while (!check(TokenType.RPAREN) && !check(TokenType.ARROW) && !check(TokenType.EOF)) {
            // Only accept identifiers as parameter names; if we see something else, this is not a lambda
            if (!check(TokenType.IDENTIFIER)) {
                throw new IllegalStateException("Expected identifier for lambda parameter, got: " + current());
            }
            String paramName = current().value();
            advance();
            // Skip type annotation: (a: Number) or (a: Array<Number>) -> skip to next param
            if (check(TokenType.COLON)) {
                advance(); // :
                skipTypeExpression(); // type expression (simple, generic, or union)
            }
            DataWeaveAst defaultValue = null;
            if (check(TokenType.ASSIGN)) {
                advance();
                defaultValue = parseOr();
            }
            params.add(new DataWeaveAst.LambdaParam(paramName, defaultValue));
            if (check(TokenType.COMMA)) {
                advance();
            }
        }

        if (innerParens) {
            expect(TokenType.RPAREN);
        }

        expect(TokenType.ARROW);
        DataWeaveAst body = parseExpression();
        expect(TokenType.RPAREN);
        return new DataWeaveAst.Lambda(params, body);
    }

    private DataWeaveAst parseDollarShorthand() {
        advance(); // $
        List<String> fields = new ArrayList<>();
        while (check(TokenType.DOT) && isTokenAhead(1, TokenType.IDENTIFIER)) {
            advance(); // .
            fields.add(current().value());
            advance();
        }
        DataWeaveAst expr = new DataWeaveAst.LambdaShorthand(fields);
        // Selectors that belong to the shorthand itself ($.@id, $.*Item, $.a?, $[0], $.@id.x).
        // They are parsed here so they bind to $ also where the shorthand is a lambda argument
        // (payload map $.@id), which is not followed by the general postfix operators.
        while (true) {
            if (check(TokenType.DOT) && isTokenAhead(1, TokenType.AT)) {
                advance(); // .
                advance(); // @
                String attr = current().value();
                advance();
                expr = new DataWeaveAst.AttributeAccess(expr, attr);
            } else if (check(TokenType.DOT) && isTokenAhead(1, TokenType.STAR)) {
                advance(); // .
                advance(); // *
                String field = current().value();
                advance();
                expr = new DataWeaveAst.MultiValueSelector(expr, field);
            } else if (check(TokenType.DOT) && isTokenAhead(1, TokenType.IDENTIFIER)) {
                // a field after another selector, such as $.@id.x
                advance(); // .
                String field = current().value();
                advance();
                expr = new DataWeaveAst.FieldAccess(expr, field);
            } else if (check(TokenType.QUESTION)) {
                advance(); // ?
                expr = new DataWeaveAst.ExistenceCheck(expr);
            } else if (check(TokenType.LBRACKET)) {
                advance(); // [
                DataWeaveAst index = parseExpression();
                expect(TokenType.RBRACKET);
                expr = new DataWeaveAst.IndexAccess(expr, index);
            } else {
                return expr;
            }
        }
    }

    private boolean isTokenAhead(int offset, TokenType type) {
        Token token = peekAhead(offset);
        return token != null && token.type() == type;
    }

    private DataWeaveAst parsePrimary() {
        if (check(TokenType.STRING)) {
            String value = current().value();
            advance();
            return new DataWeaveAst.StringLit(value, false);
        }

        if (check(TokenType.NUMBER)) {
            String value = current().value();
            advance();
            return new DataWeaveAst.NumberLit(value);
        }

        if (check(TokenType.BOOLEAN)) {
            boolean value = "true".equals(current().value());
            advance();
            return new DataWeaveAst.BooleanLit(value);
        }

        if (check(TokenType.NULL_LIT)) {
            advance();
            return new DataWeaveAst.NullLit();
        }

        if (check(TokenType.DOLLAR)) {
            return parseDollarShorthand();
        }

        if (check(TokenType.DOLLAR_DOLLAR)) {
            advance(); // $$
            return new DataWeaveAst.DoubleDollar();
        }

        if (check(TokenType.LPAREN)) {
            advance(); // (
            DataWeaveAst expr = parseExpression();
            expect(TokenType.RPAREN);
            return new DataWeaveAst.Parens(expr);
        }

        if (check(TokenType.LBRACE)) {
            return parseObjectLiteral();
        }

        if (check(TokenType.LBRACKET)) {
            return parseArrayLiteral();
        }

        if (check(TokenType.IDENTIFIER)) {
            return parseIdentifierOrCall();
        }

        if (check(TokenType.MINUS)) {
            advance();
            DataWeaveAst operand = parsePrimary();
            return new DataWeaveAst.UnaryOp("-", operand);
        }

        // Unknown token — return an Unsupported node so best-effort callers (e.g. the CLI
        // `camel transform dataweave` command) can continue processing the rest of the file.
        // The language layer (DatasonnetLanguage) will detect the Unsupported node via
        // getTodoCount() and throw DataWeaveConversionException there, giving strict behaviour
        // at the language level while keeping the parser usable as a best-effort migration tool.
        Token cur = current();
        String tokenText = cur.value() != null && !cur.value().isEmpty() ? cur.value() : cur.type().name();
        advance();
        return new DataWeaveAst.Unsupported(
                tokenText, "unexpected token " + cur.type() + " at " + cur.line() + ":" + cur.col());
    }

    private DataWeaveAst parseIdentifierOrCall() {
        String name = current().value();
        advance();

        // Built-in function calls
        if (check(TokenType.LPAREN)) {
            return switch (name) {
                case "sizeOf", "upper", "lower", "trim", "capitalize", "now", "uuid", "p",
                        "isEmpty", "isBlank", "abs", "ceil", "floor", "round",
                        "log", "sqrt", "sum", "avg", "min", "max",
                        "read", "write", "typeOf" ->
                    parseFunctionCall(name);
                default -> {
                    // Could be a custom function call or lambda
                    // Check if it looks like a function call
                    if (isLikelyFunctionCall()) {
                        yield parseFunctionCall(name);
                    }
                    yield new DataWeaveAst.Identifier(name);
                }
            };
        }

        return new DataWeaveAst.Identifier(name);
    }

    private boolean isLikelyFunctionCall() {
        // Look ahead to determine if this LPAREN starts a function call
        // vs a lambda in a postfix operation
        if (!check(TokenType.LPAREN)) {
            return false;
        }
        int depth = 0;
        int look = pos;
        while (look < tokens.size()) {
            Token t = tokens.get(look);
            if (t.type() == TokenType.LPAREN) {
                depth++;
            } else if (t.type() == TokenType.RPAREN) {
                depth--;
                if (depth == 0) {
                    // Check what follows the closing paren
                    // If it's an arrow, this is a lambda, not a function call
                    return look + 1 >= tokens.size() || tokens.get(look + 1).type() != TokenType.ARROW;
                }
            } else if (t.type() == TokenType.ARROW && depth == 1) {
                // Arrow inside first level of parens = lambda
                return false;
            }
            look++;
        }
        return true;
    }

    private DataWeaveAst parseFunctionCall(String name) {
        expect(TokenType.LPAREN);
        List<DataWeaveAst> args = new ArrayList<>();
        while (!check(TokenType.RPAREN) && !check(TokenType.EOF)) {
            args.add(parseExpression());
            if (check(TokenType.COMMA)) {
                advance();
            }
        }
        expect(TokenType.RPAREN);
        return new DataWeaveAst.FunctionCall(name, args);
    }

    private DataWeaveAst parseObjectLiteral() {
        expect(TokenType.LBRACE);
        List<DataWeaveAst.ObjectEntry> entries = new ArrayList<>();

        while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) {
            // Check for dynamic key: (expr): value
            boolean dynamic = false;
            DataWeaveAst key;
            if (check(TokenType.LPAREN)) {
                advance();
                key = parseExpression();
                expect(TokenType.RPAREN);
                dynamic = true;
            } else if (check(TokenType.IDENTIFIER)) {
                String name = current().value();
                advance();
                key = new DataWeaveAst.Identifier(name);
            } else if (check(TokenType.STRING)) {
                key = new DataWeaveAst.StringLit(current().value(), false);
                advance();
            } else {
                break;
            }

            expect(TokenType.COLON);
            DataWeaveAst value = parseExpression();
            entries.add(new DataWeaveAst.ObjectEntry(key, value, dynamic));

            if (check(TokenType.COMMA)) {
                advance();
            }
        }

        expect(TokenType.RBRACE);
        return new DataWeaveAst.ObjectLit(entries);
    }

    private DataWeaveAst parseArrayLiteral() {
        expect(TokenType.LBRACKET);
        List<DataWeaveAst> elements = new ArrayList<>();

        while (!check(TokenType.RBRACKET) && !check(TokenType.EOF)) {
            elements.add(parseExpression());
            if (check(TokenType.COMMA)) {
                advance();
            }
        }

        expect(TokenType.RBRACKET);
        return new DataWeaveAst.ArrayLit(elements);
    }

    // -- Token helpers --

    /**
     * Skip a DataWeave type expression after a colon. Handles: - Simple: {@code Number} - Generic:
     * {@code Array<Number>}, {@code Array<String | Null>} - Union: {@code String | Null} - Object type: {@code {name:
     * String}} Stops when it encounters a {@code ,}, {@code )}, {@code =} or EOF at depth 0.
     */
    private void skipTypeExpression() {
        // Skip the leading type name (or opening brace/bracket for object types)
        if (!check(TokenType.EOF)) {
            if (check(TokenType.LBRACE)) {
                // Object type: {name: String} — skip balanced braces
                int depth = 1;
                advance(); // {
                while (depth > 0 && !check(TokenType.EOF)) {
                    if (check(TokenType.LBRACE)) {
                        depth++;
                    } else if (check(TokenType.RBRACE)) {
                        depth--;
                    }
                    advance();
                }
                return; // object type is self-contained, no trailing < or |
            }
            advance(); // consume base type name / '['
        }
        // Now handle trailing generic parameters '<...>' and union '|'
        while (!check(TokenType.EOF)) {
            if (check(TokenType.LT)) {
                // Generic type params: skip balanced < ... >
                int depth = 1;
                advance(); // <
                while (depth > 0 && !check(TokenType.EOF)) {
                    if (check(TokenType.LT)) {
                        depth++;
                    } else if (check(TokenType.GT)) {
                        depth--;
                    }
                    advance();
                }
            } else if (check(TokenType.PIPE)) {
                // Union type: skip '|' and the next type expression
                advance(); // |
                skipTypeExpression();
            } else {
                break;
            }
        }
    }

    private Token current() {
        return pos < tokens.size() ? tokens.get(pos) : tokens.get(tokens.size() - 1);
    }

    private Token peekAhead(int offset) {
        int idx = pos + offset;
        return idx < tokens.size() ? tokens.get(idx) : null;
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

    private void expect(TokenType type) {
        if (check(type)) {
            advance();
        } else {
            Token cur = current();
            throw new DataWeaveConversionException(
                    "DataWeave parse error at " + cur.line() + ":" + cur.col()
                                                   + ": expected " + type + " but found " + cur.type()
                                                   + " ('" + cur.value() + "')");
        }
    }
}
