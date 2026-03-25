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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.transform.DataWeaveAst.*;
import org.apache.camel.dsl.jbang.core.commands.transform.DataWeaveLexer.Token;

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

    // ── Emission ──

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
        // The lexer preserves escape sequences as-is, so don't double-escape
        return "\"" + s.value().replace("\"", "\\\"") + "\"";
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
        return "std.map(function(x) x." + mv.field() + ", " + collection + ")";
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
                yield expr + (includeComments ? " // TODO: manual conversion needed — as " + tc.type() : "");
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
                           + (includeComments ? " // NOTE: assumes JSON input — DW read() supports multiple formats" : "");
            case "write" -> "std.manifestJsonEx(" + argStr + ", \"  \")"
                            + (includeComments ? " // NOTE: outputs JSON — DW write() supports multiple formats" : "");
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
                // DW: map ((item, index) -> body) — DS: std.mapWithIndex(function(index, item) body, collection)
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
        return "std.foldl(" + emitNode(re.lambda()) + ", " + collection + ", null)";
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
            // distinctBy keeps first occurrence per key — use distinctBy helper
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
            return "c.groupBy(" + collection + ", function(" + paramNames.get(0) + ") " + body + ")";
        }
        return "c.groupBy(" + collection + ", " + emitNode(gbe.lambda()) + ")";
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
                ? "// TODO: manual conversion needed — " + u.reason() + ": " + u.originalText() + "\nnull"
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
