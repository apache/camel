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

/**
 * AST node types for DataWeave 2.0 expressions.
 */
public sealed interface DataWeaveAst {

    record Script(Header header, DataWeaveAst body) implements DataWeaveAst {
    }

    record Header(String version, String outputType, List<InputDecl> inputs) implements DataWeaveAst {
    }

    record InputDecl(String name, String mediaType) implements DataWeaveAst {
    }

    // Literals
    record StringLit(String value, boolean singleQuoted) implements DataWeaveAst {
    }

    record NumberLit(String value) implements DataWeaveAst {
    }

    record BooleanLit(boolean value) implements DataWeaveAst {
    }

    record NullLit() implements DataWeaveAst {
    }

    // Expressions
    record Identifier(String name) implements DataWeaveAst {
    }

    record FieldAccess(DataWeaveAst object, String field) implements DataWeaveAst {
    }

    record IndexAccess(DataWeaveAst object, DataWeaveAst index) implements DataWeaveAst {
    }

    record MultiValueSelector(DataWeaveAst object, String field) implements DataWeaveAst {
    }

    record ObjectLit(List<ObjectEntry> entries) implements DataWeaveAst {
    }

    record ObjectEntry(DataWeaveAst key, DataWeaveAst value, boolean dynamic) implements DataWeaveAst {
    }

    record ArrayLit(List<DataWeaveAst> elements) implements DataWeaveAst {
    }

    record BinaryOp(String op, DataWeaveAst left, DataWeaveAst right) implements DataWeaveAst {
    }

    record UnaryOp(String op, DataWeaveAst operand) implements DataWeaveAst {
    }

    record IfElse(DataWeaveAst condition, DataWeaveAst thenExpr, DataWeaveAst elseExpr) implements DataWeaveAst {
    }

    record DefaultExpr(DataWeaveAst expr, DataWeaveAst fallback) implements DataWeaveAst {
    }

    record TypeCoercion(DataWeaveAst expr, String type, String format) implements DataWeaveAst {
    }

    record FunctionCall(String name, List<DataWeaveAst> args) implements DataWeaveAst {
    }

    record Lambda(List<LambdaParam> params, DataWeaveAst body) implements DataWeaveAst {
    }

    record LambdaParam(String name, DataWeaveAst defaultValue) implements DataWeaveAst {
    }

    record LambdaShorthand(List<String> fields) implements DataWeaveAst {
    }

    // Collection operations
    record MapExpr(DataWeaveAst collection, DataWeaveAst lambda) implements DataWeaveAst {
    }

    record FilterExpr(DataWeaveAst collection, DataWeaveAst lambda) implements DataWeaveAst {
    }

    record ReduceExpr(DataWeaveAst collection, DataWeaveAst lambda) implements DataWeaveAst {
    }

    record FlatMapExpr(DataWeaveAst collection, DataWeaveAst lambda) implements DataWeaveAst {
    }

    record DistinctByExpr(DataWeaveAst collection, DataWeaveAst lambda) implements DataWeaveAst {
    }

    record GroupByExpr(DataWeaveAst collection, DataWeaveAst lambda) implements DataWeaveAst {
    }

    record OrderByExpr(DataWeaveAst collection, DataWeaveAst lambda) implements DataWeaveAst {
    }

    // String postfix operations
    record ContainsExpr(DataWeaveAst string, DataWeaveAst substring) implements DataWeaveAst {
    }

    record StartsWithExpr(DataWeaveAst string, DataWeaveAst prefix) implements DataWeaveAst {
    }

    record EndsWithExpr(DataWeaveAst string, DataWeaveAst suffix) implements DataWeaveAst {
    }

    record SplitByExpr(DataWeaveAst string, DataWeaveAst separator) implements DataWeaveAst {
    }

    record JoinByExpr(DataWeaveAst array, DataWeaveAst separator) implements DataWeaveAst {
    }

    record ReplaceExpr(DataWeaveAst string, DataWeaveAst target, DataWeaveAst replacement) implements DataWeaveAst {
    }

    // Variable and function declarations
    record VarDecl(String name, DataWeaveAst value, DataWeaveAst body) implements DataWeaveAst {
    }

    record FunDecl(String name, List<String> params, DataWeaveAst funBody, DataWeaveAst next) implements DataWeaveAst {
    }

    // Type check
    record TypeCheck(DataWeaveAst expr, String type) implements DataWeaveAst {
    }

    // Unsupported construct (kept as raw text)
    record Unsupported(String originalText, String reason) implements DataWeaveAst {
    }

    // Parenthesized expression (for preserving grouping)
    record Parens(DataWeaveAst expr) implements DataWeaveAst {
    }

    // Block of local declarations followed by an expression
    record Block(List<DataWeaveAst> declarations, DataWeaveAst expr) implements DataWeaveAst {
    }
}
