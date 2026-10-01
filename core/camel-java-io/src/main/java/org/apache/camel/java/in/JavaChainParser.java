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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.java.in.JavaTokenizer.Kind;
import org.apache.camel.java.in.JavaTokenizer.Token;

/**
 * Reads the parts of a Java source that make up routes: the package and imports, the constants of the class, and the
 * statements of {@code configure()} as chains of calls. A source without {@code configure()} is read as a list of
 * statements, as a documentation snippet is written. What it does not understand becomes an {@link Opaque} node with
 * its source text, so the caller can report it.
 */
final class JavaChainParser {

    /** A node of an argument or statement. */
    sealed interface Node {
        int line();
    }

    record Str(String value, int line) implements Node {
    }

    record Num(String text, int line) implements Node {
    }

    /** A char literal: {@code 'i'}. */
    record Chr(char value, int line) implements Node {
    }

    record Bool(boolean value, int line) implements Node {
    }

    record Null(int line) implements Node {
    }

    /** String concatenation with {@code +}. */
    record Concat(List<Node> parts, int line) implements Node {
    }

    /** {@code Foo.class}. */
    record ClassLit(String type, int line) implements Node {
    }

    /** {@code Foo.class.getName()} and its siblings: the name of a class, as text. */
    record ClassName(String type, String method, int line) implements Node {
    }

    /** Arithmetic: {@code 5 * 1000L}, {@code DELAY / 2}, {@code a - b}. */
    record BinOp(String op, Node left, Node right, int line) implements Node {
    }

    /** A name: a local or class constant, or a static field such as {@code LoggingLevel.INFO}. */
    record Ref(String name, int line) implements Node {
    }

    /** A call of a method with its arguments. */
    record Call(String name, List<Node> args, int line) implements Node {
    }

    /**
     * A chain: an optional qualifier ({@code TimeUnit.SECONDS} in {@code TimeUnit.SECONDS.toMillis(5)}, null when the
     * chain starts with a call) and the calls after it.
     */
    record Chain(String qualifier, List<Call> calls, int line) implements Node {
    }

    /** {@code new Foo(...)}, with or without a class body. */
    record New(String type, List<Node> args, boolean anonymous, String text, int line) implements Node {
    }

    /**
     * A local variable holding what a route builder entry returns, continued by later statements:
     * {@code RouteDefinition route = from("direct:a");} then {@code route.to("mock:a");}.
     */
    record Local(String name, Node value, int line) implements Node {
    }

    /** A lambda or method reference. */
    record Lambda(String text, int line) implements Node {
    }

    /** Anything else, kept as its source text. */
    record Opaque(String text, int line) implements Node {
    }

    /**
     * What was read from the source: the statements of each route builder ({@code configure()} or
     * {@code configuration()}) on their own, as each is a builder of its own; a snippet is one.
     */
    record Source(
            String packageName, Map<String, String> imports, Map<String, Node> constants, List<List<Node>> builders,
            boolean endpointDsl, Map<String, String> staticImports, List<String> staticWildcards, Set<String> classes,
            List<String> builderParameters, List<String> superclasses) {
    }

    private final List<Token> tokens;
    private int pos;

    private JavaChainParser(List<Token> tokens) {
        this.tokens = tokens;
    }

    static Source parse(String source) {
        return new JavaChainParser(JavaTokenizer.tokenize(source)).read();
    }

    // ---- the file ----

    private Source read() {
        String pkg = null;
        boolean endpointDsl = false;
        Map<String, String> staticImports = new LinkedHashMap<>();
        List<String> staticWildcards = new ArrayList<>();
        Map<String, String> imports = new LinkedHashMap<>();
        Map<String, Node> constants = new LinkedHashMap<>();
        // package and imports
        while (peek().kind() != Kind.EOF) {
            if (peek().isIdent("package")) {
                pos++;
                pkg = qualifiedName();
                expect(";");
            } else if (peek().isIdent("import")) {
                pos++;
                boolean isStatic = peek().isIdent("static");
                if (isStatic) {
                    pos++;
                }
                String name = qualifiedName();
                expect(";");
                if (name.startsWith("org.apache.camel.builder.endpoint.")) {
                    endpointDsl = true;
                }
                if (!isStatic && !name.endsWith(".*")) {
                    imports.put(name.substring(name.lastIndexOf('.') + 1), name);
                } else if (isStatic && name.endsWith(".*")) {
                    // import static com.acme.Constants.*: its constants by simple name
                    staticWildcards.add(name.substring(0, name.length() - 2));
                } else if (isStatic) {
                    // import static com.acme.Constants.QUEUE: QUEUE is com.acme.Constants.QUEUE
                    staticImports.put(name.substring(name.lastIndexOf('.') + 1), name.substring(0, name.lastIndexOf('.')));
                }
            } else {
                break;
            }
        }
        int start = pos;
        List<int[]> bodies = findMethodBodies(constants);
        List<List<Node>> builders = new ArrayList<>();
        // the parameter of each builder lambda, null for configure()
        List<String> parameters = new ArrayList<>();
        if (bodies.isEmpty()) {
            // a snippet: the statements themselves
            pos = start;
            List<Node> statements = new ArrayList<>();
            statements(tokens.size() - 1, statements, constants);
            builders.add(statements);
            parameters.add(null);
        } else {
            for (int[] body : bodies) {
                pos = body[0];
                alias = body[2] >= 0 ? aliases.get(body[2]) : null;
                // the locals of one configure() are not those of the next, as the replay has them
                routeLocals.clear();
                List<Node> statements = new ArrayList<>();
                statements(body[1], statements, constants);
                builders.add(statements);
                parameters.add(alias);
            }
            alias = null;
        }
        // a class extending EndpointRouteBuilder (or LambdaEndpointRouteBuilder) uses the endpoint DSL too; the classes the
        // source declares, whose constants are its own
        Set<String> classes = new LinkedHashSet<>();
        List<String> superclasses = new ArrayList<>();
        for (int i = 0; i < tokens.size() - 2; i++) {
            Token t = tokens.get(i);
            if (t.kind() == Kind.IDENT && t.text().endsWith("EndpointRouteBuilder")) {
                endpointDsl = true;
            }
            if ((t.isIdent("class") || t.isIdent("interface") || t.isIdent("enum"))
                    && tokens.get(i + 1).kind() == Kind.IDENT && (i == 0 || !tokens.get(i - 1).is("."))) {
                classes.add(tokens.get(i + 1).text());
            }
            // class Foo extends Base: the constants Foo inherits from Base
            if (t.isIdent("extends") && tokens.get(i + 1).kind() == Kind.IDENT) {
                int p = i + 1;
                StringBuilder name = new StringBuilder(tokens.get(p).text());
                while (tokens.get(p + 1).is(".") && tokens.get(p + 2).kind() == Kind.IDENT) {
                    name.append('.').append(tokens.get(p + 2).text());
                    p += 2;
                }
                superclasses.add(name.toString());
            }
        }
        return new Source(
                pkg, imports, constants, builders, endpointDsl, staticImports, staticWildcards, classes, parameters,
                superclasses);
    }

    /**
     * The token ranges of the bodies of {@code configure()} (a RouteBuilder) and {@code configuration()} (a
     * RouteConfigurationBuilder), collecting the constants of the class on the way.
     */
    /** What a route builder is called with: the chains of its lambdas start on their parameter. */
    private static final Set<String> BUILDER_ENTRIES = Set.of(
            "from", "fromF", "fromV", "rest", "restConfiguration", "routeTemplate", "templatedRoute", "routeConfiguration",
            "onException", "onCompletion", "errorHandler", "intercept", "interceptFrom", "interceptSendToEndpoint");

    /** The parameter of the builder lambda being read, whose calls are the builder's; null in configure(). */
    private String alias;
    private final List<String> aliases = new ArrayList<>();

    /** Whether the lambda body at {@code start} calls a route builder entry on its parameter first. */
    private boolean isBuilderLambda(String param, int start) {
        int p = tokens.get(start).is("{") ? start + 1 : start;
        return tokens.get(p).isIdent(param) && tokens.get(p + 1).is(".") && tokens.get(p + 2).kind() == Kind.IDENT
                && BUILDER_ENTRIES.contains(tokens.get(p + 2).text()) && tokens.get(p + 3).is("(");
    }

    private List<int[]> findMethodBodies(Map<String, Node> constants) {
        List<int[]> bodies = new ArrayList<>();
        int depth = 0;
        while (peek().kind() != Kind.EOF) {
            Token t = peek();
            if (t.is("{")) {
                depth++;
                pos++;
            } else if (t.is("}")) {
                depth--;
                pos++;
            } else if ((t.isIdent("configure") || t.isIdent("configuration")) && at(1).is("(") && at(2).is(")")
                    && (at(3).is("{") || at(3).isIdent("throws"))) {
                // the declaration of configure(), not a call such as camel.configure().addRoutesBuilder(...), whose
                // anonymous RouteBuilder has a configure() of its own
                int p = pos + 3;
                // throws clause
                while (tokens.get(p).kind() != Kind.EOF && !tokens.get(p).is("{") && !tokens.get(p).is(";")) {
                    p++;
                }
                if (tokens.get(p).is("{")) {
                    int end = matching(p);
                    bodies.add(new int[] { p + 1, end, -1 });
                    pos = end + 1;
                } else {
                    pos = p + 1;
                }
            } else if (t.kind() == Kind.IDENT && at(1).is("->") && isBuilderLambda(t.text(), pos + 2)) {
                // a LambdaRouteBuilder: rb -> rb.from(...) or rb -> { rb.from(...); }
                aliases.add(t.text());
                int start = pos + 2;
                int end;
                if (tokens.get(start).is("{")) {
                    end = matching(start);
                    bodies.add(new int[] { start + 1, end, aliases.size() - 1 });
                } else {
                    pos = start;
                    skipArgument();
                    end = pos;
                    bodies.add(new int[] { start, end, aliases.size() - 1 });
                }
                pos = end + 1;
            } else if (t.isIdent("final") && depth >= 1) {
                // a field or local constant: final Type NAME = value;
                pos++;
                constant(constants);
            } else {
                pos++;
            }
        }
        return bodies;
    }

    /** After {@code final}: {@code Type NAME = value;} into the constants, or nothing. */
    private void constant(Map<String, Node> constants) {
        int save = pos;
        skipType();
        if (peek().kind() == Kind.IDENT && at(1).is("=")) {
            String name = next().text();
            pos++;
            Node value = expression();
            if (peek().is(";")) {
                constants.put(name, value);
                return;
            }
        }
        pos = save;
    }

    /** Statements with a block: the chains in the block are read, the loop or condition is not evaluated. */
    private static final Set<String> CONTROL = Set.of(
            "for", "while", "if", "else", "try", "catch", "finally", "do", "switch", "synchronized");

    private void statements(int end, List<Node> out, Map<String, Node> constants) {
        while (pos < end && peek().kind() != Kind.EOF) {
            int stmtStart = pos;
            Token t = peek();
            if (t.is(";") || t.is("{") || t.is("}")) {
                pos++;
                continue;
            }
            if (t.kind() == Kind.IDENT && CONTROL.contains(t.text())) {
                // for (...) { from(...)...; }: the routes inside, once; a loop variable stays unresolved
                pos++;
                if (peek().is("(")) {
                    pos = matching(pos) + 1;
                }
                if (peek().is("{")) {
                    int close = matching(pos);
                    pos++;
                    statements(close, out, constants);
                    pos = close + 1;
                }
                continue;
            }
            if (t.isIdent("return") || t.isIdent("throw") || t.isIdent("case") || t.isIdent("default")) {
                skipStatement(end);
                continue;
            }
            Local local = routeLocal();
            if (local != null) {
                out.add(local);
                continue;
            }
            if (t.isIdent("final") || t.isIdent("var") || isLocalDeclaration()) {
                if (t.isIdent("final")) {
                    pos++;
                }
                constant(constants);
                skipStatement(end);
                continue;
            }
            if ((t.isIdent("this") || alias != null && t.isIdent(alias)) && at(1).is(".")) {
                pos += 2;
            }
            if (peek().kind() == Kind.IDENT && (at(1).is("(") || routeLocals.contains(peek().text()) && at(1).is("."))) {
                // a chain, or one continuing a route kept in a local variable: route.to("mock:a")
                Node chain = chainFrom();
                // an expression lambda ends without a semicolon
                if (peek().is(";") || pos >= end) {
                    out.add(chain);
                    pos++;
                    continue;
                }
            }
            // not a chain we read: keep its text so a caller can tell something was skipped
            pos = stmtStart;
            int line = peek().line();
            String text = skipStatement(end);
            if (pos == stmtStart) {
                // never stand still
                pos++;
            }
            if (text.contains("from(") || text.contains("rest(")) {
                out.add(new Opaque(text, line));
            }
        }
    }

    /** The local variables holding a route (or a part of one) in the builder being read. */
    private final Set<String> routeLocals = new HashSet<>();

    /**
     * {@code [final] Type name = from(...)...;}: a route kept in a local variable, built where it is declared; null for
     * any other statement.
     */
    private Local routeLocal() {
        int p = pos;
        if (tokens.get(p).isIdent("final")) {
            p++;
        }
        Token type = tokens.get(p);
        Token name = tokens.get(p + 1);
        if (type.kind() != Kind.IDENT || name.kind() != Kind.IDENT || !tokens.get(p + 2).is("=")) {
            return null;
        }
        Token entry = tokens.get(p + 3);
        boolean fromLocal = routeLocals.contains(entry.text()) && tokens.get(p + 4).is(".");
        if (entry.kind() != Kind.IDENT || !(BUILDER_ENTRIES.contains(entry.text()) && tokens.get(p + 4).is("(")
                || fromLocal)) {
            return null;
        }
        int start = pos;
        pos = p + 3;
        Node value = chainFrom();
        if (!peek().is(";")) {
            pos = start;
            return null;
        }
        pos++;
        routeLocals.add(name.text());
        return new Local(name.text(), value, type.line());
    }

    /** Whether the statement is {@code Type name = ...;} with a simple type, a local variable. */
    private boolean isLocalDeclaration() {
        return peek().kind() == Kind.IDENT && at(1).kind() == Kind.IDENT && at(2).is("=");
    }

    // ---- expressions ----

    private static final Set<String> CLASS_NAME_METHODS
            = Set.of("getName", "getSimpleName", "getCanonicalName", "getTypeName");

    /** How deep arguments may nest ({@code a(b(c(...)))}) before the rest is kept opaque, so the stack holds. */
    static final int MAX_DEPTH = 100;

    private int depth;

    /** An argument: terms joined by {@code +}; other operators make it opaque. */
    private Node expression() {
        int start = pos;
        int line = peek().line();
        if (depth >= MAX_DEPTH) {
            return new Opaque(skipArgument(), line);
        }
        depth++;
        try {
            return expressionBody(start, line);
        } finally {
            depth--;
        }
    }

    private Node expressionBody(int start, int line) {
        List<Node> parts = new ArrayList<>();
        parts.add(product());
        while (peek().is("+") || peek().is("-")) {
            if (peek().is("-")) {
                // a - b: the sum so far minus the next product
                pos++;
                Node left = parts.size() == 1 ? parts.get(0) : new Concat(new ArrayList<>(parts), line);
                parts.clear();
                parts.add(new BinOp("-", left, product(), line));
                continue;
            }
            pos++;
            parts.add(product());
        }
        if (!isArgumentEnd()) {
            // an operator or a ternary we do not evaluate
            pos = start;
            return new Opaque(skipArgument(), line);
        }
        return parts.size() == 1 ? parts.get(0) : new Concat(parts, line);
    }

    /** Terms joined by {@code *}, {@code /} or {@code %}. */
    private Node product() {
        Node left = term();
        while (peek().is("*") || peek().is("/") || peek().is("%")) {
            String op = next().text();
            left = new BinOp(op, left, term(), left.line());
        }
        return left;
    }

    private boolean isArgumentEnd() {
        Token t = peek();
        return t.is(",") || t.is(")") || t.is(";") || t.is("}") || t.kind() == Kind.EOF;
    }

    private Node term() {
        Token t = peek();
        int line = t.line();
        switch (t.kind()) {
            case STRING -> {
                pos++;
                return new Str(t.text(), line);
            }
            case CHAR -> {
                pos++;
                return t.text().length() == 1 ? new Chr(t.text().charAt(0), line) : new Str(t.text(), line);
            }
            case NUMBER -> {
                pos++;
                return new Num(t.text(), line);
            }
            default -> {
            }
        }
        if (t.is("-") && at(1).kind() == Kind.NUMBER) {
            pos += 2;
            return new Num("-" + tokens.get(pos - 1).text(), line);
        }
        if (t.isIdent("true") || t.isIdent("false")) {
            pos++;
            return new Bool(t.text().equals("true"), line);
        }
        if (t.isIdent("null")) {
            pos++;
            return new Null(line);
        }
        if (isLambda()) {
            int start = pos;
            skipArgument();
            return new Lambda(text(start, pos), line);
        }
        if (t.isIdent("new")) {
            return newExpression();
        }
        if (t.is("(")) {
            // a cast or parentheses
            int close = matching(pos);
            if (tokens.get(close + 1).kind() == Kind.IDENT || tokens.get(close + 1).kind() == Kind.STRING
                    || tokens.get(close + 1).is("(")) {
                pos = close + 1;
                return term();
            }
            pos++;
            Node inner = expression();
            if (peek().is(")")) {
                pos++;
            }
            return inner;
        }
        if (t.kind() == Kind.IDENT) {
            return chainFrom();
        }
        int start = pos;
        skipArgument();
        return new Opaque(text(start, pos), line);
    }

    /** {@code x -> ...}, {@code (a, b) -> ...} or {@code Foo::bar}. */
    private boolean isLambda() {
        if (peek().kind() == Kind.IDENT && at(1).is("->")) {
            return true;
        }
        if (peek().is("(")) {
            int close = matching(pos);
            return tokens.get(close + 1).is("->");
        }
        // method reference: Name::name or name.name::name
        int p = pos;
        while (tokens.get(p).kind() == Kind.IDENT && tokens.get(p + 1).is(".")) {
            p += 2;
        }
        return tokens.get(p).kind() == Kind.IDENT && tokens.get(p + 1).is("::");
    }

    private Node newExpression() {
        int start = pos;
        int line = peek().line();
        pos++;
        String type = qualifiedName();
        if (peek().is("<")) {
            skipGenerics();
        }
        if (peek().is("[")) {
            // an array: keep it opaque
            pos = start;
            return new Opaque(skipArgument(), line);
        }
        List<Node> args = peek().is("(") ? arguments() : List.of();
        boolean anonymous = false;
        if (peek().is("{")) {
            pos = matching(pos) + 1;
            anonymous = true;
        }
        Node node = new New(type, args, anonymous, text(start, pos), line);
        // new Foo().bar(): the call on the new object is not followed
        if (peek().is(".")) {
            pos = start;
            return new Opaque(skipArgument(), line);
        }
        return node;
    }

    /**
     * A chain starting at an identifier: names up to the first call are its qualifier ({@code LoggingLevel.INFO} is
     * only a qualifier, a {@link Ref}), then calls. {@code Foo.class} is a {@link ClassLit}.
     */
    private Node chainFrom() {
        int line = peek().line();
        StringBuilder qualifier = new StringBuilder();
        while (peek().kind() == Kind.IDENT && !at(1).is("(")) {
            String name = next().text();
            if (name.equals("class") && !qualifier.isEmpty()) {
                // Foo.class.getName(): the name, as text
                if (peek().is(".") && at(1).kind() == Kind.IDENT && CLASS_NAME_METHODS.contains(at(1).text())
                        && at(2).is("(") && at(3).is(")")) {
                    String method = at(1).text();
                    pos += 4;
                    return new ClassName(qualifier.toString(), method, line);
                }
                return new ClassLit(qualifier.toString(), line);
            }
            qualifier.append(qualifier.isEmpty() ? "" : ".").append(name);
            // byte[].class, String[][].class
            while (peek().is("[") && at(1).is("]")) {
                qualifier.append("[]");
                pos += 2;
            }
            if (peek().is(".")) {
                pos++;
                if (peek().is("<")) {
                    skipGenerics();
                }
            } else {
                return new Ref(qualifier.toString(), line);
            }
        }
        List<Call> calls = new ArrayList<>();
        while (peek().kind() == Kind.IDENT && at(1).is("(")) {
            Token name = next();
            calls.add(new Call(name.text(), arguments(), name.line()));
            if (peek().is(".")) {
                pos++;
                if (peek().is("<")) {
                    skipGenerics();
                }
                if (peek().kind() == Kind.IDENT && !at(1).is("(")) {
                    // a field access after a call: not followed
                    break;
                }
            } else {
                break;
            }
        }
        return new Chain(qualifier.isEmpty() ? null : qualifier.toString(), calls, line);
    }

    private List<Node> arguments() {
        List<Node> args = new ArrayList<>();
        expect("(");
        while (!peek().is(")") && peek().kind() != Kind.EOF) {
            int before = pos;
            args.add(expression());
            if (peek().is(",")) {
                pos++;
            } else if (!peek().is(")")) {
                // something the argument parser left: skip to the end of this argument
                skipArgument();
                if (peek().is(",")) {
                    pos++;
                }
            }
            if (pos == before) {
                // a token no argument starts with (a ; or } in broken source): stop here
                break;
            }
        }
        expect(")");
        return args;
    }

    // ---- skipping ----

    /** Skips to the end of an argument (a comma or closing parenthesis at this level); returns its text. */
    private String skipArgument() {
        int start = pos;
        while (peek().kind() != Kind.EOF) {
            Token t = peek();
            if (t.is("(") || t.is("{") || t.is("[")) {
                pos = matching(pos) + 1;
            } else if (t.is(",") || t.is(")") || t.is(";") || t.is("}")) {
                break;
            } else {
                pos++;
            }
        }
        return text(start, pos);
    }

    /** Skips to the end of the statement; returns its text. */
    private String skipStatement(int end) {
        int start = pos;
        while (pos < end && peek().kind() != Kind.EOF) {
            Token t = peek();
            if (t.is("(") || t.is("[")) {
                pos = matching(pos) + 1;
            } else if (t.is("{")) {
                pos = matching(pos) + 1;
                // a block statement (if, for, a lambda body at the end) ends with its brace
                if (!peek().is(")") && !peek().is(".") && !peek().is(",")) {
                    break;
                }
            } else if (t.is(";")) {
                pos++;
                break;
            } else {
                pos++;
            }
        }
        return text(start, pos);
    }

    private void skipType() {
        qualifiedName();
        if (peek().is("<")) {
            skipGenerics();
        }
        while (peek().is("[") && at(1).is("]")) {
            pos += 2;
        }
    }

    private void skipGenerics() {
        int depth = 0;
        while (peek().kind() != Kind.EOF) {
            Token t = next();
            if (t.is("<")) {
                depth++;
            } else if (t.is(">")) {
                if (--depth == 0) {
                    return;
                }
            } else if (t.is(";") || t.is("{")) {
                pos--;
                return;
            }
        }
    }

    /** The index of the bracket closing the one at {@code open}. */
    private int matching(int open) {
        String o = tokens.get(open).text();
        String c = switch (o) {
            case "(" -> ")";
            case "{" -> "}";
            default -> "]";
        };
        int depth = 0;
        for (int i = open; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.is(o)) {
                depth++;
            } else if (t.is(c) && --depth == 0) {
                return i;
            }
        }
        return tokens.size() - 1;
    }

    // ---- tokens ----

    private String qualifiedName() {
        StringBuilder sb = new StringBuilder();
        while (peek().kind() == Kind.IDENT || peek().is("*")) {
            sb.append(next().text());
            if (peek().is(".") && (at(1).kind() == Kind.IDENT || at(1).is("*"))) {
                sb.append('.');
                pos++;
            } else {
                break;
            }
        }
        return sb.toString();
    }

    private Token peek() {
        return tokens.get(Math.min(pos, tokens.size() - 1));
    }

    private Token at(int offset) {
        return tokens.get(Math.min(pos + offset, tokens.size() - 1));
    }

    private Token next() {
        Token t = peek();
        pos++;
        return t;
    }

    private void expect(String punct) {
        if (peek().is(punct)) {
            pos++;
        }
    }

    /** The source text of tokens, joined by spaces where needed; strings are quoted again. */
    private String text(int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < to && i < tokens.size(); i++) {
            Token t = tokens.get(i);
            String s = t.kind() == Kind.STRING ? "\"" + t.text().replace("\"", "\\\"") + "\"" : t.text();
            if (!sb.isEmpty() && t.kind() == Kind.IDENT
                    && Character.isJavaIdentifierPart(sb.charAt(sb.length() - 1))) {
                sb.append(' ');
            }
            sb.append(s);
        }
        return sb.toString();
    }
}
