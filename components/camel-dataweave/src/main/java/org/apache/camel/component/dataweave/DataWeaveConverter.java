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

import org.apache.camel.component.dataweave.DataWeaveAst.*;
import org.apache.camel.component.dataweave.DataWeaveLexer.Token;

/**
 * Converts DataWeave 2.0 scripts to DataSonnet. Parses the DataWeave input, walks the AST, and emits equivalent
 * DataSonnet code.
 */
public class DataWeaveConverter {

    private boolean needsCamelLib;
    private int todoCount;
    private int convertedCount;
    private boolean includeComments = true;

    public DataWeaveConverter() {
    }

    public void setIncludeComments(boolean includeComments) {
        this.includeComments = includeComments;
    }

    public int getTodoCount() {
        return todoCount;
    }

    public int getConvertedCount() {
        return convertedCount;
    }

    public boolean needsCamelLib() {
        return needsCamelLib;
    }

    /**
     * Convert a full DataWeave script (with header) to DataSonnet.
     */
    public String convert(String dataWeave) {
        needsCamelLib = false;
        todoCount = 0;
        convertedCount = 0;

        DataWeaveLexer lexer = new DataWeaveLexer(dataWeave);
        List<Token> tokens = lexer.tokenize();
        DataWeaveParser parser = new DataWeaveParser(tokens);
        DataWeaveAst ast = parser.parse();

        return emit(ast);
    }

    /**
     * Convert a single DataWeave expression (no header) to DataSonnet.
     */
    public String convertExpression(String expression) {
        needsCamelLib = false;
        todoCount = 0;
        convertedCount = 0;

        DataWeaveLexer lexer = new DataWeaveLexer(expression);
        List<Token> tokens = lexer.tokenize();
        DataWeaveParser parser = new DataWeaveParser(tokens);
        DataWeaveAst ast = parser.parseExpressionOnly();

        return emitNode(ast);
    }

    // -- Emission --

    private String emit(DataWeaveAst node) {
        if (node instanceof Script script) {
            return emitScript(script);
        }
        return emitNode(node);
    }

    private String emitScript(Script script) {
        StringBuilder sb = new StringBuilder();

        // Emit DataSonnet header
        Header header = script.header();
        if (header.outputType() != null) {
            sb.append("/** DataSonnet\n");
            sb.append("version=").append(header.version()).append("\n");
            sb.append("output ").append(header.outputType()).append("\n");
            for (InputDecl input : header.inputs()) {
                sb.append("input ").append(input.name()).append(" ").append(input.mediaType()).append("\n");
            }
            sb.append("*/\n");
        }

        // First pass: emit body to determine if camel lib is needed
        String body = emitNode(script.body());

        // Add camel lib import if needed
        if (needsCamelLib) {
            sb.append("local c = import 'camel.libsonnet';\n");
        }

        sb.append(body);
        return sb.toString();
    }

    private String emitNode(DataWeaveAst node) {
        if (node == null) {
            return "";
        }

        convertedCount++;

        if (node instanceof Script s) {
            return emitScript(s);
        } else if (node instanceof Header) {
            return "";
        } else if (node instanceof InputDecl) {
            return "";
        } else if (node instanceof StringLit s) {
            return emitStringLit(s);
        } else if (node instanceof NumberLit n) {
            return n.value();
        } else if (node instanceof BooleanLit b) {
            return String.valueOf(b.value());
        } else if (node instanceof NullLit) {
            return "null";
        } else if (node instanceof Identifier id) {
            return emitIdentifier(id);
        } else if (node instanceof FieldAccess fa) {
            return emitFieldAccess(fa);
        } else if (node instanceof IndexAccess ia) {
            return emitNode(ia.object()) + "[" + emitNode(ia.index()) + "]";
        } else if (node instanceof MultiValueSelector mv) {
            return emitMultiValueSelector(mv);
        } else if (node instanceof AttributeAccess aa) {
            return emitAttributeAccess(aa);
        } else if (node instanceof ExistenceCheck ec) {
            return emitExistenceCheck(ec);
        } else if (node instanceof DoubleDollar) {
            return "acc"; // $$ is the reduce accumulator; use the 'acc' name convention
        } else if (node instanceof ObjectLit obj) {
            return emitObjectLit(obj);
        } else if (node instanceof ArrayLit arr) {
            return emitArrayLit(arr);
        } else if (node instanceof BinaryOp op) {
            return emitBinaryOp(op);
        } else if (node instanceof UnaryOp op) {
            return emitUnaryOp(op);
        } else if (node instanceof IfElse ie) {
            return emitIfElse(ie);
        } else if (node instanceof DefaultExpr def) {
            return emitDefault(def);
        } else if (node instanceof TypeCoercion tc) {
            return emitTypeCoercion(tc);
        } else if (node instanceof FunctionCall fc) {
            return emitFunctionCall(fc);
        } else if (node instanceof Lambda lam) {
            return emitLambda(lam);
        } else if (node instanceof LambdaParam lp) {
            return lp.name();
        } else if (node instanceof LambdaShorthand ls) {
            return emitLambdaShorthand(ls);
        } else if (node instanceof MapExpr me) {
            return emitMap(me);
        } else if (node instanceof FilterExpr fe) {
            return emitFilter(fe);
        } else if (node instanceof ReduceExpr re) {
            return emitReduce(re);
        } else if (node instanceof FlatMapExpr fme) {
            return emitFlatMap(fme);
        } else if (node instanceof DistinctByExpr dbe) {
            return emitDistinctBy(dbe);
        } else if (node instanceof GroupByExpr gbe) {
            return emitGroupBy(gbe);
        } else if (node instanceof OrderByExpr obe) {
            return emitOrderBy(obe);
        } else if (node instanceof ContainsExpr ce) {
            return emitContains(ce);
        } else if (node instanceof StartsWithExpr swe) {
            return emitStartsWith(swe);
        } else if (node instanceof EndsWithExpr ewe) {
            return emitEndsWith(ewe);
        } else if (node instanceof SplitByExpr sbe) {
            return emitSplitBy(sbe);
        } else if (node instanceof JoinByExpr jbe) {
            return emitJoinBy(jbe);
        } else if (node instanceof ReplaceExpr re) {
            return emitReplace(re);
        } else if (node instanceof VarDecl vd) {
            return emitVarDecl(vd);
        } else if (node instanceof FunDecl fd) {
            return emitFunDecl(fd);
        } else if (node instanceof TypeCheck tc) {
            return emitTypeCheck(tc);
        } else if (node instanceof Unsupported u) {
            return emitUnsupported(u);
        } else if (node instanceof Parens p) {
            return "(" + emitNode(p.expr()) + ")";
        } else if (node instanceof Block b) {
            return emitBlock(b);
        }
        return "";
    }

    private String emitStringLit(StringLit s) {
        String value = s.value();
        // Jsonnet has no in-string interpolation. DataWeave uses "Hello $(expr)" but there is
        // no faithful automated conversion (the sub-expression must be re-emitted through the AST).
        // Emit as a TODO comment and a placeholder so the converter output is syntactically valid.
        if (value.contains("$(")) {
            todoCount++;
            return includeComments
                    ? "/* TODO: manual conversion needed -- string interpolation: \"" + value + "\"*/\n\""
                      + value.replace("\"", "\\\"") + "\""
                    : "\"" + value.replace("\"", "\\\"") + "\"";
        }
        // The lexer preserves escape sequences verbatim (e.g. \" is stored as \").
        // When the source was a single-quoted DataWeave string, bare double-quote characters
        // in the value would make the output invalid Jsonnet. Re-escape any unescaped " chars.
        // Strategy: replace every " that is NOT already preceded by a backslash with \".
        String escaped = value.replaceAll("(?<!\\\\)\"", "\\\\\"");
        return "\"" + escaped + "\"";
    }

    private String emitIdentifier(Identifier id) {
        return switch (id.name()) {
            case "payload" -> "body";
            case "flowVars" -> "cml.variable"; // DW 1.0
            default -> id.name();
        };
    }

    private String emitFieldAccess(FieldAccess fa) {
        // Special handling for payload.x -> body.x
        // vars.x -> cml.variable('x')
        // attributes.headers.x -> cml.header('x')
        // attributes.queryParams.x -> cml.header('x')

        if (fa.object() instanceof Identifier id) {
            if ("vars".equals(id.name())) {
                return "cml.variable('" + fa.field() + "')";
            }
            if ("flowVars".equals(id.name())) {
                return "cml.variable('" + fa.field() + "')";
            }
        }

        if (fa.object() instanceof FieldAccess outer) {
            if (outer.object() instanceof Identifier id && "attributes".equals(id.name())) {
                if ("headers".equals(outer.field()) || "queryParams".equals(outer.field())) {
                    return "cml.header('" + fa.field() + "')";
                }
            }
        }

        return emitNode(fa.object()) + "." + fa.field();
    }

    private String emitMultiValueSelector(MultiValueSelector mv) {
        String collection = emitNode(mv.object());
        // DataWeave .*field collects all values for key 'field' from each element of the collection.
        // In DataSonnet/Jsonnet: std.map(function(x) x.<field>, collection).
        // Note: camel.libsonnet has no multiValue helper, so we emit directly.
        return "std.map(function(x) x." + mv.field() + ", " + collection + ")";
    }

    private String emitAttributeAccess(AttributeAccess aa) {
        // DataWeave .@attr accesses an XML attribute; in DataSonnet XML attributes are exposed
        // as object keys prefixed with '@', e.g. body.Order['@id'].
        return emitNode(aa.object()) + "[\"@" + aa.attribute() + "\"]";
    }

    private String emitExistenceCheck(ExistenceCheck ec) {
        // DataWeave expr? (key-present selector) returns true/false.
        // In Jsonnet, accessing a missing field raises an error, so we cannot simply wrap the expression.
        // For FieldAccess and AttributeAccess we can use std.objectHas(obj, "key") safely.
        // For other shapes the semantics cannot be faithfully reproduced without a helper; emit as TODO.
        if (ec.expr() instanceof FieldAccess fa) {
            return "std.objectHas(" + emitNode(fa.object()) + ", \"" + fa.field() + "\")";
        }
        if (ec.expr() instanceof AttributeAccess aa) {
            return "std.objectHas(" + emitNode(aa.object()) + ", \"@" + aa.attribute() + "\")";
        }
        todoCount++;
        return includeComments
                ? "/* TODO: manual conversion needed -- existence check on non-field expression: "
                  + emitNode(ec.expr()) + "*/\nfalse"
                : "false";
    }

    private String emitObjectLit(ObjectLit obj) {
        if (obj.entries().isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{\n");
        for (int i = 0; i < obj.entries().size(); i++) {
            ObjectEntry entry = obj.entries().get(i);
            String key;
            if (entry.dynamic()) {
                key = "[" + emitNode(entry.key()) + "]";
            } else if (entry.key() instanceof Identifier id) {
                key = id.name();
            } else if (entry.key() instanceof StringLit sl) {
                key = "\"" + sl.value() + "\"";
            } else {
                key = emitNode(entry.key());
            }
            sb.append("    ").append(key).append(": ").append(emitNode(entry.value()));
            if (i < obj.entries().size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("}");
        return sb.toString();
    }

    private String emitArrayLit(ArrayLit arr) {
        if (arr.elements().isEmpty()) {
            return "[]";
        }
        List<String> parts = new ArrayList<>();
        for (DataWeaveAst element : arr.elements()) {
            parts.add(emitNode(element));
        }
        return "[" + String.join(", ", parts) + "]";
    }

    private String emitBinaryOp(BinaryOp op) {
        String left = emitNode(op.left());
        String right = emitNode(op.right());
        return switch (op.op()) {
            case "++" -> left + " + " + right; // DW concat -> DS concat
            case "and" -> left + " && " + right;
            case "or" -> left + " || " + right;
            default -> left + " " + op.op() + " " + right;
        };
    }

    private String emitUnaryOp(UnaryOp op) {
        return switch (op.op()) {
            case "not" -> "!" + emitNode(op.operand());
            default -> op.op() + emitNode(op.operand());
        };
    }

    private String emitIfElse(IfElse ie) {
        String cond = emitNode(ie.condition());
        String thenPart = emitNode(ie.thenExpr());
        if (ie.elseExpr() != null) {
            String elsePart = emitNode(ie.elseExpr());
            return "if " + cond + " then " + thenPart + " else " + elsePart;
        }
        return "if " + cond + " then " + thenPart;
    }

    private String emitDefault(DefaultExpr def) {
        String expr = emitNode(def.expr());
        String fallback = emitNode(def.fallback());
        return "cml.defaultVal(" + expr + ", " + fallback + ")";
    }

    private String emitTypeCoercion(TypeCoercion tc) {
        if (tc.format() != null) {
            // Optimize: now() as String {format: "..."} -> cml.now("...")
            if ("String".equals(tc.type()) && tc.expr() instanceof FunctionCall fc && "now".equals(fc.name())) {
                return "cml.nowFmt(\"" + tc.format() + "\")";
            }
            String expr = emitNode(tc.expr());
            // as String {format: "..."} -> cml.formatDate(expr, "...")
            if ("String".equals(tc.type())) {
                return "cml.formatDate(" + expr + ", \"" + tc.format() + "\")";
            }
            // as Date {format: "..."} -> cml.parseDate(expr, "...")
            if ("Date".equals(tc.type()) || "DateTime".equals(tc.type()) || "LocalDateTime".equals(tc.type())) {
                return "cml.parseDate(" + expr + ", \"" + tc.format() + "\")";
            }
        }
        String expr = emitNode(tc.expr());
        return switch (tc.type()) {
            case "Number" -> "cml.toDecimal(" + expr + ")";
            case "String" -> "std.toString(" + expr + ")";
            case "Boolean" -> "cml.toBoolean(" + expr + ")";
            default -> {
                todoCount++;
                yield expr + (includeComments ? " // TODO: manual conversion needed -- as " + tc.type() : "");
            }
        };
    }

    private String emitFunctionCall(FunctionCall fc) {
        List<String> args = new ArrayList<>();
        for (DataWeaveAst arg : fc.args()) {
            args.add(emitNode(arg));
        }
        String argStr = String.join(", ", args);

        return switch (fc.name()) {
            case "sizeOf" -> "std.length(" + argStr + ")";
            case "upper" -> "std.asciiUpper(" + argStr + ")";
            case "lower" -> "std.asciiLower(" + argStr + ")";
            case "trim" -> {
                needsCamelLib = true;
                yield "c.trim(" + argStr + ")";
            }
            case "capitalize" -> {
                needsCamelLib = true;
                yield "c.capitalize(" + argStr + ")";
            }
            case "now" -> args.isEmpty() ? "cml.now()" : "cml.now(" + argStr + ")";
            case "uuid" -> "cml.uuid()";
            case "p" -> "cml.properties(" + argStr + ")";
            case "typeOf" -> "cml.typeOf(" + argStr + ")";
            case "isEmpty" -> "cml.isEmpty(" + argStr + ")";
            case "isBlank" -> "cml.isEmpty(" + argStr + ")";
            case "abs" -> {
                needsCamelLib = true;
                yield "c.abs(" + argStr + ")";
            }
            case "ceil" -> "std.ceil(" + argStr + ")";
            case "floor" -> "std.floor(" + argStr + ")";
            case "round" -> {
                needsCamelLib = true;
                yield "c.round(" + argStr + ")";
            }
            case "sqrt" -> "cml.sqrt(" + argStr + ")";
            case "avg" -> {
                needsCamelLib = true;
                yield "c.avg(" + argStr + ")";
            }
            case "sum" -> {
                needsCamelLib = true;
                yield "c.sum(" + argStr + ")";
            }
            case "min" -> {
                needsCamelLib = true;
                yield "c.min(" + argStr + ")";
            }
            case "max" -> {
                needsCamelLib = true;
                yield "c.max(" + argStr + ")";
            }
            case "read" -> "std.parseJson(" + argStr + ")"
                           + (includeComments ? " // NOTE: assumes JSON input -- DW read() supports multiple formats" : "");
            case "write" -> "std.manifestJsonEx(" + argStr + ", \"  \")"
                            + (includeComments ? " // NOTE: outputs JSON -- DW write() supports multiple formats" : "");
            default -> fc.name() + "(" + argStr + ")";
        };
    }

    private String emitLambda(Lambda lam) {
        List<String> paramNames = lambdaParamNames(lam);
        return "function(" + String.join(", ", paramNames) + ") " + emitNode(lam.body());
    }

    private String emitLambdaShorthand(LambdaShorthand ls) {
        if (ls.fields().isEmpty()) {
            return "function(x) x";
        }
        String path = String.join(".", ls.fields());
        return "function(x) x." + path;
    }

    private String emitMap(MapExpr me) {
        String collection = emitNode(me.collection());
        if (me.lambda() instanceof Lambda lam) {
            List<String> paramNames = lambdaParamNames(lam);
            String body = emitNode(lam.body());
            if (paramNames.size() == 2) {
                // DW: map ((item, index) -> body) -- DS: std.mapWithIndex(function(index, item) body, collection)
                // Parameter order is swapped: DW is (item, index), DS is (index, item)
                return "std.mapWithIndex(function(" + paramNames.get(1) + ", " + paramNames.get(0)
                       + ") " + body + ", " + collection + ")";
            }
            return "std.map(function(" + paramNames.get(0) + ") " + body + ", " + collection + ")";
        }
        if (me.lambda() instanceof LambdaShorthand ls) {
            // $.field -> function(x) x.field
            String path = String.join(".", ls.fields());
            return "std.map(function(x) x." + path + ", " + collection + ")";
        }
        return "std.map(" + emitNode(me.lambda()) + ", " + collection + ")";
    }

    private String emitFilter(FilterExpr fe) {
        String collection = emitNode(fe.collection());
        if (fe.lambda() instanceof Lambda lam) {
            List<String> paramNames = lambdaParamNames(lam);
            String body = emitNode(lam.body());
            return "std.filter(function(" + paramNames.get(0) + ") " + body + ", " + collection + ")";
        }
        return "std.filter(" + emitNode(fe.lambda()) + ", " + collection + ")";
    }

    private String emitReduce(ReduceExpr re) {
        String collection = emitNode(re.collection());
        if (re.lambda() instanceof Lambda lam) {
            // DataWeave reduce: (item, acc = init) -> expr
            // DataSonnet foldl: function(acc, item) expr, arr, init
            // NOTE: parameter order is SWAPPED
            List<LambdaParam> params = lam.params();
            if (params.size() >= 2) {
                String itemParam = params.get(0).name();
                String accParam = params.get(1).name();
                DataWeaveAst initValue = params.get(1).defaultValue();
                String init = initValue != null ? emitNode(initValue) : "null";
                String body = emitNode(lam.body());
                // Swap acc and item in the function signature for std.foldl
                return "std.foldl(function(" + accParam + ", " + itemParam + ") " + body + ", "
                       + collection + ", " + init + ")";
            }
        }
        // Shorthand form: payload.items reduce ($$ + $.price)
        // $$ is the accumulator ($$ -> acc) and $ is the current item ($ -> item).
        // DataWeave shorthand without an explicit initial value uses the first element
        // as the starting accumulator: std.foldl(function(acc, item) body, arr[1:], arr[0]).
        String body = emitReduceShorthandBody(re.lambda());
        return "local _arr = " + collection + ";\n"
               + "std.foldl(function(acc, item) " + body + ", _arr[1:], _arr[0])";
    }

    /**
     * Emit a reduce shorthand body, rewriting {@code $$} to {@code acc} and {@code $} (optionally with field access) to
     * {@code item} or {@code item.field}. Falls back to normal {@code emitNode} for any sub-expression that doesn't
     * contain shorthand references.
     */
    private String emitReduceShorthandBody(DataWeaveAst node) {
        if (node instanceof DoubleDollar) {
            return "acc";
        }
        if (node instanceof LambdaShorthand ls) {
            if (ls.fields().isEmpty()) {
                return "item";
            }
            return "item." + String.join(".", ls.fields());
        }
        if (node instanceof BinaryOp op) {
            String left = emitReduceShorthandBody(op.left());
            String right = emitReduceShorthandBody(op.right());
            return switch (op.op()) {
                case "++" -> left + " + " + right;
                case "and" -> left + " && " + right;
                case "or" -> left + " || " + right;
                default -> left + " " + op.op() + " " + right;
            };
        }
        if (node instanceof Parens p) {
            return "(" + emitReduceShorthandBody(p.expr()) + ")";
        }
        if (node instanceof FieldAccess fa) {
            return emitReduceShorthandBody(fa.object()) + "." + fa.field();
        }
        if (node instanceof UnaryOp op) {
            return switch (op.op()) {
                case "not" -> "!" + emitReduceShorthandBody(op.operand());
                default -> op.op() + emitReduceShorthandBody(op.operand());
            };
        }
        // For anything else: if the sub-expression contains a shorthand reference ($ or $$)
        // that emitNode cannot rewrite, emit a TODO to avoid silently producing wrong code.
        // Pure literals and identifiers without shorthand references are safe to emit normally.
        if (containsShorthand(node)) {
            todoCount++;
            return includeComments
                    ? "/* TODO: manual conversion needed -- reduce shorthand in unsupported context: "
                      + node.getClass().getSimpleName() + "*/\nnull"
                    : "null";
        }
        return emitNode(node);
    }

    /**
     * Returns true if the given AST node or any of its children contain a LambdaShorthand ($) or DoubleDollar ($$) that
     * would be emitted incorrectly by the normal emitNode path in a reduce shorthand context.
     */
    private boolean containsShorthand(DataWeaveAst node) {
        if (node == null) {
            return false;
        }
        if (node instanceof LambdaShorthand || node instanceof DoubleDollar) {
            return true;
        }
        if (node instanceof BinaryOp op) {
            return containsShorthand(op.left()) || containsShorthand(op.right());
        }
        if (node instanceof UnaryOp op) {
            return containsShorthand(op.operand());
        }
        if (node instanceof Parens p) {
            return containsShorthand(p.expr());
        }
        if (node instanceof FieldAccess fa) {
            return containsShorthand(fa.object());
        }
        if (node instanceof FunctionCall fc) {
            return fc.args().stream().anyMatch(this::containsShorthand);
        }
        if (node instanceof DefaultExpr def) {
            return containsShorthand(def.expr()) || containsShorthand(def.fallback());
        }
        if (node instanceof IfElse ie) {
            return containsShorthand(ie.condition()) || containsShorthand(ie.thenExpr()) || containsShorthand(ie.elseExpr());
        }
        return false;
    }

    private String emitFlatMap(FlatMapExpr fme) {
        String collection = emitNode(fme.collection());
        if (fme.lambda() instanceof Lambda lam) {
            List<String> paramNames = lambdaParamNames(lam);
            String body = emitNode(lam.body());
            return "std.flatMap(function(" + paramNames.get(0) + ") " + body + ", " + collection + ")";
        }
        return "std.flatMap(" + emitNode(fme.lambda()) + ", " + collection + ")";
    }

    private String emitDistinctBy(DistinctByExpr dbe) {
        needsCamelLib = true;
        String collection = emitNode(dbe.collection());
        if (dbe.lambda() instanceof Lambda lam) {
            List<String> paramNames = lambdaParamNames(lam);
            String body = emitNode(lam.body());
            // distinctBy keeps first occurrence per key -- use distinctBy helper
            return "c.distinctBy(" + collection + ", function(" + paramNames.get(0) + ") " + body + ")";
        }
        return "c.distinct(" + collection + ")";
    }

    private String emitGroupBy(GroupByExpr gbe) {
        needsCamelLib = true;
        String collection = emitNode(gbe.collection());
        if (gbe.lambda() instanceof Lambda lam) {
            List<String> paramNames = lambdaParamNames(lam);
            String body = emitNode(lam.body());
            // Jsonnet object keys must be strings. DataWeave allows any type as a groupBy key,
            // so we wrap with std.toString() unconditionally to ensure valid Jsonnet output.
            // If the key expression is already a string, std.toString() is a no-op.
            return "c.groupBy(" + collection + ", function(" + paramNames.get(0) + ") std.toString(" + body + "))";
        }
        return "c.groupBy(" + collection + ", function(x) std.toString((" + emitNode(gbe.lambda()) + ")(x)))";
    }

    private String emitOrderBy(OrderByExpr obe) {
        needsCamelLib = true;
        String collection = emitNode(obe.collection());
        if (obe.lambda() instanceof Lambda lam) {
            List<String> paramNames = lambdaParamNames(lam);
            String body = emitNode(lam.body());
            return "c.sortBy(" + collection + ", function(" + paramNames.get(0) + ") " + body + ")";
        }
        return "c.sortBy(" + collection + ", " + emitNode(obe.lambda()) + ")";
    }

    private String emitContains(ContainsExpr ce) {
        needsCamelLib = true;
        return "c.contains(" + emitNode(ce.string()) + ", " + emitNode(ce.substring()) + ")";
    }

    private String emitStartsWith(StartsWithExpr swe) {
        needsCamelLib = true;
        return "c.startsWith(" + emitNode(swe.string()) + ", " + emitNode(swe.prefix()) + ")";
    }

    private String emitEndsWith(EndsWithExpr ewe) {
        needsCamelLib = true;
        return "c.endsWith(" + emitNode(ewe.string()) + ", " + emitNode(ewe.suffix()) + ")";
    }

    private String emitSplitBy(SplitByExpr sbe) {
        return "std.split(" + emitNode(sbe.string()) + ", " + emitNode(sbe.separator()) + ")";
    }

    private String emitJoinBy(JoinByExpr jbe) {
        return "std.join(" + emitNode(jbe.separator()) + ", " + emitNode(jbe.array()) + ")";
    }

    private String emitReplace(ReplaceExpr re) {
        return "std.strReplace(" + emitNode(re.string()) + ", " + emitNode(re.target()) + ", "
               + emitNode(re.replacement()) + ")";
    }

    private String emitVarDecl(VarDecl vd) {
        String value = emitNode(vd.value());
        String body = vd.body() != null ? emitNode(vd.body()) : "";
        return "local " + vd.name() + " = " + value + ";\n" + body;
    }

    private String emitFunDecl(FunDecl fd) {
        String params = String.join(", ", fd.params());
        String funBody = emitNode(fd.funBody());
        String next = fd.next() != null ? emitNode(fd.next()) : "";
        return "local " + fd.name() + "(" + params + ") = " + funBody + ";\n" + next;
    }

    private String emitBlock(Block block) {
        StringBuilder sb = new StringBuilder();
        for (DataWeaveAst decl : block.declarations()) {
            sb.append(emitNode(decl));
        }
        sb.append(emitNode(block.expr()));
        return sb.toString();
    }

    private String emitTypeCheck(TypeCheck tc) {
        String expr = emitNode(tc.expr());
        return switch (tc.type()) {
            case "String" -> "std.isString(" + expr + ")";
            case "Number" -> "std.isNumber(" + expr + ")";
            case "Boolean" -> "std.isBoolean(" + expr + ")";
            case "Object" -> "std.isObject(" + expr + ")";
            case "Array" -> "std.isArray(" + expr + ")";
            case "Null" -> expr + " == null";
            default -> "cml.typeOf(" + expr + ") == \"" + tc.type().toLowerCase() + "\"";
        };
    }

    private String emitUnsupported(Unsupported u) {
        todoCount++;
        convertedCount--;
        return includeComments
                ? "// TODO: manual conversion needed -- " + u.reason() + ": " + u.originalText() + "\nnull"
                : "null";
    }

    private List<String> lambdaParamNames(Lambda lam) {
        List<String> names = new ArrayList<>();
        for (LambdaParam p : lam.params()) {
            names.add(p.name());
        }
        return names;
    }
}
