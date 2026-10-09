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

/**
 * AST node types for DataWeave 2.0 scripts.
 * <p>
 * DataWeave functions are represented by {@link FunctionCall} whether they are called as prefix functions
 * ({@code upper(x)}, {@code map(xs, f)}) or as infix (binary) functions ({@code xs map f}, {@code s splitBy ","}).
 */
public sealed interface DataWeaveAst {

    // -- Script structure

    record Script(Header header, DataWeaveAst body) implements DataWeaveAst {
    }

    /**
     * The header directives. {@code outputProperties} are the writer properties of the output directive (such as
     * {@code skipNullOn}), {@code imports} the imported module paths (such as {@code dw::core::Strings}), and
     * {@code namespaces} the URIs of the declared XML namespace prefixes ({@code ns prefix uri}).
     */
    record Header(String version, String outputType, Map<String, String> outputProperties, List<InputDecl> inputs,
            List<String> imports, Map<String, String> namespaces)
            implements
                DataWeaveAst {
    }

    record InputDecl(String name, String mediaType) implements DataWeaveAst {
    }

    /** Declarations ({@link VarDecl}, {@link FunDecl}) in scope of an expression: the header, a do block or using. */
    record Block(List<DataWeaveAst> declarations, DataWeaveAst expr) implements DataWeaveAst {
    }

    record VarDecl(String name, DataWeaveAst value) implements DataWeaveAst {
    }

    record FunDecl(String name, List<LambdaParam> params, DataWeaveAst body) implements DataWeaveAst {
    }

    // -- Literals

    /** A string literal; the value has the escape sequences of the DataWeave source resolved. */
    record StringLit(String value) implements DataWeaveAst {
    }

    /** A string with interpolated expressions ({@code "Hello $(name)"}): string literals and expressions. */
    record Interpolation(List<DataWeaveAst> parts) implements DataWeaveAst {
    }

    record NumberLit(String value) implements DataWeaveAst {
    }

    record BooleanLit(boolean value) implements DataWeaveAst {
    }

    record NullLit() implements DataWeaveAst {
    }

    /** A regular expression literal {@code /pattern/} (Java regular expression syntax). */
    record RegexLit(String pattern) implements DataWeaveAst {
    }

    /** A date, time or period literal such as {@code |2020-01-31|} or {@code |P1D|} (without the bars). */
    record TemporalLit(String value) implements DataWeaveAst {
        public boolean isPeriod() {
            return value.startsWith("P") || value.startsWith("-P");
        }
    }

    record ObjectLit(List<ObjectEntry> entries) implements DataWeaveAst {
    }

    /**
     * An object entry {@code key: value}, where a {@code dynamic} key is an expression ({@code (expr): value}). A
     * {@code null} key is an object spread {@code (expr)}, which adds the entries of an object (or array of objects).
     * The {@code condition} of a conditional entry {@code (key: value) if cond} is null for an unconditional entry, and
     * the {@code attributes} are the XML attributes of the key ({@code key @(name: value): ...}).
     */
    record ObjectEntry(DataWeaveAst key, DataWeaveAst value, boolean dynamic, DataWeaveAst condition,
            List<ObjectEntry> attributes)
            implements
                DataWeaveAst {

        public ObjectEntry(DataWeaveAst key, DataWeaveAst value, boolean dynamic, DataWeaveAst condition) {
            this(key, value, dynamic, condition, List.of());
        }
    }

    /** A name in an XML namespace: {@code prefix#name}, with the prefix declared in the header. */
    record QName(String prefix, String name) implements DataWeaveAst {
    }

    record ArrayLit(List<DataWeaveAst> elements) implements DataWeaveAst {
    }

    // -- References

    record Identifier(String name) implements DataWeaveAst {
    }

    /** The parameters of an implicit lambda: {@code $} (level 1), {@code $$} (level 2) and {@code $$$} (level 3). */
    record Dollar(int level) implements DataWeaveAst {
    }

    // -- Selectors

    /** Single-value selector {@code .field} (or {@code ."field"}). */
    record FieldAccess(DataWeaveAst object, String field) implements DataWeaveAst {
    }

    /** Attribute selector {@code .@attr}. */
    record AttributeAccess(DataWeaveAst object, String attribute) implements DataWeaveAst {
    }

    /** Attributes selector {@code .@}: all the attributes of an XML element. */
    record AllAttributes(DataWeaveAst object) implements DataWeaveAst {
    }

    /** Single-value selector of an element in an XML namespace {@code .prefix#field}. */
    record QualifiedFieldAccess(DataWeaveAst object, String prefix, String field) implements DataWeaveAst {
    }

    /** Multi-value selector {@code .*field}. */
    record MultiValueSelector(DataWeaveAst object, String field) implements DataWeaveAst {
    }

    /**
     * Descendants selector {@code ..field}, or {@code ..*field} (multi) for all the values of an XML element that
     * repeats.
     */
    record DescendantSelector(DataWeaveAst object, String field, boolean multi) implements DataWeaveAst {
        public DescendantSelector(DataWeaveAst object, String field) {
            this(object, field, false);
        }
    }

    /** Index or dynamic key selector {@code [expr]}; the index is a {@link Range} for {@code [a to b]}. */
    record IndexAccess(DataWeaveAst object, DataWeaveAst index) implements DataWeaveAst {
    }

    /** Filter selector {@code [?(condition)]}. */
    record FilterSelector(DataWeaveAst object, DataWeaveAst condition) implements DataWeaveAst {
    }

    /** Key-present selector {@code expr?}. */
    record ExistenceCheck(DataWeaveAst expr) implements DataWeaveAst {
    }

    /** A range {@code from to to}. */
    record Range(DataWeaveAst from, DataWeaveAst to) implements DataWeaveAst {
    }

    // -- Operators and expressions

    record BinaryOp(String op, DataWeaveAst left, DataWeaveAst right) implements DataWeaveAst {
    }

    record UnaryOp(String op, DataWeaveAst operand) implements DataWeaveAst {
    }

    record IfElse(DataWeaveAst condition, DataWeaveAst thenExpr, DataWeaveAst elseExpr) implements DataWeaveAst {
    }

    record DefaultExpr(DataWeaveAst expr, DataWeaveAst fallback) implements DataWeaveAst {
    }

    /** Type coercion {@code expr as Type {properties}}, where the properties are such as {@code format}. */
    record TypeCoercion(DataWeaveAst expr, String type, Map<String, String> properties) implements DataWeaveAst {
    }

    record TypeCheck(DataWeaveAst expr, String type) implements DataWeaveAst {
    }

    record FunctionCall(String name, List<DataWeaveAst> args) implements DataWeaveAst {
    }

    record Lambda(List<LambdaParam> params, DataWeaveAst body) implements DataWeaveAst {
    }

    record LambdaParam(String name, DataWeaveAst defaultValue) implements DataWeaveAst {
    }

    record Match(DataWeaveAst expr, List<MatchCase> cases) implements DataWeaveAst {
    }

    /**
     * A case of a match expression. The pattern is a literal ({@code case "A"}), a type ({@code case is String}), a
     * regular expression ({@code case matches /re/}) or none (a binding, optionally with a guard:
     * {@code case x if x > 3}). The {@code binding} is the name the matched value (for a regular expression: the
     * matched groups) is bound to, if any. {@code otherwise} is true for the {@code else} case.
     */
    record MatchCase(
            String binding, DataWeaveAst literal, String type, String regex, DataWeaveAst guard, DataWeaveAst body,
            boolean otherwise)
            implements
                DataWeaveAst {
    }

    /** A construct the parser recognises but the converter cannot convert. */
    record Unsupported(String originalText, String reason) implements DataWeaveAst {
    }

    record Parens(DataWeaveAst expr) implements DataWeaveAst {
    }
}
