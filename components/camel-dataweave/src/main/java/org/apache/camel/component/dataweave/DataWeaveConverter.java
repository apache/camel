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

import java.lang.reflect.RecordComponent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

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

/**
 * Converts DataWeave 2.0 scripts to DataSonnet.
 * <p>
 * The generated DataSonnet keeps the DataWeave semantics with the {@code dataweave.libsonnet} library of the
 * camel-datasonnet component (imported as {@code dw}): a selector on a missing field, an index out of range or a
 * {@code null} value gives {@code null}, a selector on an array selects from every element, {@code default} applies to
 * missing values, the core functions accept {@code null}, and so on.
 * <p>
 * A construct that cannot be converted, including any unknown function or variable, is emitted as a TODO comment and
 * {@code null}, and counted in {@link #getTodoCount()}, so the caller can reject the conversion rather than run a
 * script that gives a different result. Invalid DataWeave fails with a {@link DataWeaveConversionException}.
 */
public class DataWeaveConverter {

    private static final String DW_IMPORT = "local dw = import 'dataweave.libsonnet';\n";

    private static final Set<String> JSONNET_RESERVED = Set.of(
            "assert", "else", "error", "false", "for", "function", "if", "import", "importstr", "importbin", "in",
            "local", "null", "tailstrict", "then", "self", "super", "true", "std", "ds", "cml", "dw", "body");

    // A function argument of a higher-order function: its position, and the parameters DataWeave passes to the
    // function (the names of the variables $, $$ and $$$ refer to in an implicit lambda)
    private record FunctionArg(int position, List<String> params) {
    }

    private static final List<String> ITEM_INDEX = List.of("item", "index");
    private static final List<String> VALUE_KEY_INDEX = List.of("value", "key", "index");
    private static final List<String> ITEM = List.of("item");

    private static final Map<String, FunctionArg> FUNCTION_ARGS = new HashMap<>();

    static {
        for (String name : List.of("map", "filter", "flatMap", "distinctBy", "groupBy", "orderBy")) {
            FUNCTION_ARGS.put(name, new FunctionArg(1, ITEM_INDEX));
        }
        for (String name : List.of("mapObject", "filterObject", "pluck")) {
            FUNCTION_ARGS.put(name, new FunctionArg(1, VALUE_KEY_INDEX));
        }
        for (String name : List.of("maxBy", "minBy", "sumBy", "countBy", "every", "some", "partition", "firstWith",
                "takeWhile", "dropWhile")) {
            FUNCTION_ARGS.put(name, new FunctionArg(1, ITEM));
        }
        FUNCTION_ARGS.put("reduce", new FunctionArg(1, List.of("item", "acc")));
        FUNCTION_ARGS.put("then", new FunctionArg(1, List.of("value")));
    }

    // dw::core::Strings functions provided by DataSonnet's ds.strings (with the string as first argument)
    private static final Set<String> DS_STRINGS = Set.of(
            "appendIfMissing", "camelize", "capitalize", "charCode", "charCodeAt", "dasherize", "isAlpha",
            "isAlphanumeric", "isLowerCase", "isNumeric", "isUpperCase", "isWhitespace", "leftPad", "ordinalize",
            "pluralize", "prependIfMissing", "repeat", "rightPad", "singularize", "substringAfter",
            "substringAfterLast", "substringBefore", "substringBeforeLast", "underscore", "unwrap", "withMaxSize",
            "wrapIfMissing", "wrapWith");

    // Functions of dataweave.libsonnet, by the number of arguments
    private static final Set<String> LIB_FUNCTIONS_1 = Set.of(
            "upper", "lower", "trim", "sizeOf", "isEmpty", "isBlank", "typeOf", "flatten", "keysOf", "namesOf",
            "valuesOf", "entriesOf", "sum", "avg", "min", "max", "abs", "round", "isEven", "isOdd", "isInteger",
            "isDecimal");
    private static final Set<String> LIB_FUNCTIONS_2 = Set.of(
            "contains", "startsWith", "endsWith", "splitBy", "joinBy", "zip", "indexOf", "lastIndexOf", "take",
            "drop", "splitAt", "divideBy");

    // The DataWeave modules the converter knows the functions of
    private static final Set<String> KNOWN_MODULES = Set.of(
            "dw::Core", "dw::core::Strings", "dw::core::Arrays", "dw::core::Objects", "dw::core::Numbers",
            "dw::core::Types");

    // HTTP request attributes of Mule, and the Camel message headers with the same information
    private static final Map<String, String> HTTP_ATTRIBUTES = Map.of(
            "method", "CamelHttpMethod",
            "requestPath", "CamelHttpPath",
            "requestUri", "CamelHttpUri",
            "queryString", "CamelHttpQuery",
            "statusCode", "CamelHttpResponseCode",
            "reasonPhrase", "CamelHttpResponseText");

    private static final Set<String> ATTRIBUTE_MAPS = Set.of("headers", "queryParams", "uriParams");

    private static final Set<String> DATE_TYPES = Set.of("Date", "DateTime", "LocalDateTime", "Time", "LocalTime");

    // The selectors of a date or time: (payload.date as Date).year
    private static final Set<String> DATE_PARTS = Set.of(
            "year", "month", "day", "hour", "minutes", "seconds", "milliseconds", "nanoseconds", "dayOfWeek",
            "dayOfYear", "offsetSeconds", "quarter", "timezone");

    // A variable or function in scope; the arity of a function, or -1 for any other value
    private record Binding(String name, int arity) {
        static final int VALUE = -1;
    }

    private boolean includeComments = true;
    private int todoCount;
    private int convertedCount;
    private boolean needsDataWeaveLib;
    private int matchCount;
    private final Deque<Map<String, Binding>> scopes = new ArrayDeque<>();
    // the variables $, $$ and $$$ refer to in the body of an implicit lambda, or null
    private List<String> dollars;

    public DataWeaveConverter() {
    }

    public void setIncludeComments(boolean includeComments) {
        this.includeComments = includeComments;
    }

    /** The number of constructs that could not be converted, which are emitted as a TODO comment and null. */
    public int getTodoCount() {
        return todoCount;
    }

    public int getConvertedCount() {
        return convertedCount;
    }

    /** Whether the converted DataSonnet imports {@code dataweave.libsonnet}. */
    public boolean needsDataWeaveLib() {
        return needsDataWeaveLib;
    }

    /**
     * Convert a DataWeave script (with or without header) to DataSonnet.
     */
    public String convert(String dataWeave) {
        reset();
        DataWeaveAst ast = new DataWeaveParser(new DataWeaveLexer(dataWeave).tokenize()).parse();
        return emitScript((Script) ast);
    }

    /**
     * Convert a DataWeave expression (without header) to DataSonnet.
     */
    public String convertExpression(String expression) {
        reset();
        DataWeaveAst ast = new DataWeaveParser(new DataWeaveLexer(expression).tokenize()).parseExpressionOnly();
        String body = emit(ast);
        return needsDataWeaveLib ? DW_IMPORT + body : body;
    }

    private void reset() {
        todoCount = 0;
        convertedCount = 0;
        matchCount = 0;
        needsDataWeaveLib = false;
        scopes.clear();
        dollars = null;
    }

    // -- Script

    private String emitScript(Script script) {
        Header header = script.header();
        StringBuilder sb = new StringBuilder();
        String output = header.outputType() != null ? mediaType(header.outputType()) : null;
        if (output != null || !header.inputs().isEmpty()) {
            sb.append("/** DataSonnet\nversion=2.0\n");
            if (output != null) {
                sb.append("output ").append(output).append('\n');
            }
            for (InputDecl input : header.inputs()) {
                sb.append("input ").append(input.name()).append(' ').append(mediaType(input.mediaType())).append('\n');
            }
            sb.append("*/\n");
        }
        StringBuilder todos = new StringBuilder();
        for (String module : header.imports()) {
            if (!KNOWN_MODULES.contains(module)) {
                todos.append(todoComment("import of module", module));
            }
        }
        String body = emit(script.body());
        body = applyWriterProperties(body, output, header.outputProperties());
        if (needsDataWeaveLib) {
            sb.append(DW_IMPORT);
        }
        return sb.append(todos).append(body).toString();
    }

    private static String mediaType(String mediaType) {
        return switch (mediaType) {
            case "json" -> "application/json";
            case "xml" -> "application/xml";
            case "csv" -> "application/csv";
            case "java", "application/java" -> "application/x-java-object";
            case "text", "plain" -> "text/plain";
            default -> mediaType;
        };
    }

    private String applyWriterProperties(String body, String output, Map<String, String> properties) {
        String result = body;
        for (Map.Entry<String, String> property : properties.entrySet()) {
            String name = property.getKey();
            if ("indent".equals(name) || "encoding".equals(name)) {
                continue; // formatting only
            }
            if ("skipNullOn".equals(name) && (output == null || output.endsWith("json"))) {
                result = lib("skipNulls") + "(" + result + ", " + string(property.getValue()) + ")";
            } else {
                result = todoComment("writer property", name + "=" + property.getValue()) + result;
            }
        }
        return result;
    }

    // -- Expressions

    private String emit(DataWeaveAst node) {
        convertedCount++;
        if (node instanceof StringLit s) {
            return string(s.value());
        } else if (node instanceof NumberLit n) {
            return n.value();
        } else if (node instanceof BooleanLit b) {
            return String.valueOf(b.value());
        } else if (node instanceof NullLit) {
            return "null";
        } else if (node instanceof Interpolation i) {
            return emitInterpolation(i);
        } else if (node instanceof RegexLit r) {
            return todo("regular expression outside of a function argument", "/" + r.pattern() + "/");
        } else if (node instanceof TemporalLit t) {
            return t.isPeriod() ? todo("period outside of date arithmetic", "|" + t.value() + "|") : string(t.value());
        } else if (node instanceof Identifier id) {
            return emitIdentifier(id);
        } else if (node instanceof Dollar d) {
            return emitDollar(d);
        } else if (node instanceof FieldAccess || node instanceof IndexAccess || node instanceof MultiValueSelector) {
            return emitSelector(node, false);
        } else if (node instanceof AttributeAccess aa) {
            return lib("attr") + "(" + emitSelector(aa.object(), true) + ", " + string(aa.attribute()) + ")";
        } else if (node instanceof DescendantSelector ds) {
            return lib("desc") + "(" + emit(ds.object()) + ", " + string(ds.field()) + ")";
        } else if (node instanceof FilterSelector fs) {
            return lib("filter") + "(" + emit(fs.object()) + ", " + emitImplicitLambda(fs.condition(), ITEM_INDEX)
                   + ")";
        } else if (node instanceof ExistenceCheck ec) {
            return emitExistenceCheck(ec);
        } else if (node instanceof Range r) {
            return lib("range") + "(" + emit(r.from()) + ", " + emit(r.to()) + ")";
        } else if (node instanceof ObjectLit obj) {
            return emitObject(obj);
        } else if (node instanceof ArrayLit arr) {
            List<String> parts = new ArrayList<>();
            for (DataWeaveAst element : arr.elements()) {
                parts.add(emit(element));
            }
            return "[" + String.join(", ", parts) + "]";
        } else if (node instanceof BinaryOp op) {
            return emitBinaryOp(op);
        } else if (node instanceof UnaryOp op) {
            return ("not".equals(op.op()) ? "!" : "-") + operand(op.operand());
        } else if (node instanceof IfElse ie) {
            return "if " + emit(ie.condition()) + " then " + emit(ie.thenExpr()) + " else "
                   + (ie.elseExpr() != null ? emit(ie.elseExpr()) : "null");
        } else if (node instanceof DefaultExpr def) {
            return lib("default") + "(" + emit(def.expr()) + ", " + emit(def.fallback()) + ")";
        } else if (node instanceof TypeCoercion tc) {
            return emitCoercion(tc);
        } else if (node instanceof TypeCheck tc) {
            return typeCheck(emit(tc.expr()), tc.type());
        } else if (node instanceof FunctionCall fc) {
            return emitCall(fc);
        } else if (node instanceof Lambda lam) {
            return emitLambda(lam, lam.params().size());
        } else if (node instanceof Match m) {
            return emitMatch(m);
        } else if (node instanceof Block b) {
            return emitBlock(b);
        } else if (node instanceof Parens p) {
            return "(" + emit(p.expr()) + ")";
        } else if (node instanceof Unsupported u) {
            return todo(u.reason(), u.originalText());
        }
        return todo("unsupported construct", node.getClass().getSimpleName());
    }

    // An operand of an operator, in parentheses unless it is a simple expression
    private String operand(DataWeaveAst node) {
        String s = emit(node);
        boolean simple = node instanceof StringLit || node instanceof NumberLit || node instanceof BooleanLit
                || node instanceof NullLit || node instanceof Identifier || node instanceof Dollar
                || node instanceof Parens || node instanceof FunctionCall || node instanceof FieldAccess
                || node instanceof IndexAccess || node instanceof ObjectLit || node instanceof ArrayLit
                || node instanceof DefaultExpr || node instanceof AttributeAccess || node instanceof Interpolation;
        return simple ? s : "(" + s + ")";
    }

    private String emitInterpolation(Interpolation interpolation) {
        List<String> parts = new ArrayList<>();
        for (DataWeaveAst part : interpolation.parts()) {
            parts.add(part instanceof StringLit ? emit(part) : lib("str") + "(" + emit(part) + ")");
        }
        return parts.size() == 1 ? parts.get(0) : "(" + String.join(" + ", parts) + ")";
    }

    private String emitIdentifier(Identifier id) {
        Binding binding = lookup(id.name());
        if (binding != null) {
            return binding.name();
        }
        return switch (id.name()) {
            case "payload" -> "body";
            case "vars", "attributes", "flowVars" -> todo("'" + id.name() + "' without selector", id.name());
            default -> todo("unknown variable", id.name());
        };
    }

    private String emitDollar(Dollar d) {
        if (dollars != null && d.level() <= dollars.size()) {
            return dollars.get(d.level() - 1);
        }
        return todo("$ outside of a lambda", "$".repeat(d.level()));
    }

    // -- Selectors

    // A selector; raw keeps an XML element with only text as an object (for its attributes)
    private String emitSelector(DataWeaveAst node, boolean raw) {
        if (isExchangeAccess(node)) {
            return emitExchangeAccess(node);
        }
        if (node instanceof FieldAccess fa && DATE_PARTS.contains(fa.field()) && isDateValue(fa.object())) {
            DataWeaveAst date = fa.object();
            while (date instanceof Parens p) {
                date = p.expr();
            }
            return "cml.datePart(" + emit(date) + ", " + string(fa.field()) + ")";
        }
        if (node instanceof MultiValueSelector mv) {
            return lib(raw ? "multiRaw" : "multi") + "(" + emitSelector(mv.object(), true) + ", " + string(mv.field())
                   + ")";
        }
        if (node instanceof FieldAccess fa) {
            // a.b.c -> dw.path(a, ["b", "c"])
            List<String> fields = new ArrayList<>();
            DataWeaveAst base = fa;
            while (base instanceof FieldAccess f && !isExchangeAccess(f)) {
                fields.add(0, string(f.field()));
                base = f.object();
            }
            String object = base instanceof FieldAccess || base instanceof IndexAccess
                    || base instanceof MultiValueSelector
                            ? emitSelector(base, true) : emit(base);
            if (fields.size() == 1) {
                return lib(raw ? "selRaw" : "sel") + "(" + object + ", " + fields.get(0) + ")";
            }
            return lib(raw ? "pathRaw" : "path") + "(" + object + ", [" + String.join(", ", fields) + "])";
        }
        if (node instanceof IndexAccess ia) {
            String object = emitSelector(ia.object(), true);
            if (ia.index() instanceof Range r) {
                return lib("slice") + "(" + object + ", " + emit(r.from()) + ", " + emit(r.to()) + ")";
            }
            return lib("idx") + "(" + object + ", " + emit(ia.index()) + ")";
        }
        return emit(node);
    }

    // A date or time: a date literal, now(), or a coercion to a date or time type
    private static boolean isDateValue(DataWeaveAst node) {
        DataWeaveAst n = node;
        while (n instanceof Parens p) {
            n = p.expr();
        }
        return n instanceof TemporalLit t && !t.isPeriod()
                || n instanceof TypeCoercion tc && DATE_TYPES.contains(tc.type())
                || n instanceof FunctionCall fc && "now".equals(fc.name()) && fc.args().isEmpty();
    }

    // vars.x, attributes.headers.x, attributes.method, ...
    private boolean isExchangeAccess(DataWeaveAst node) {
        String key = selectedKey(node);
        if (key == null) {
            return false;
        }
        DataWeaveAst object = selectedObject(node);
        if (isGlobal(object, "vars") || isGlobal(object, "flowVars")) {
            return true;
        }
        if (isGlobal(object, "attributes")) {
            return !ATTRIBUTE_MAPS.contains(key);
        }
        String mapKey = selectedKey(object);
        return mapKey != null && ATTRIBUTE_MAPS.contains(mapKey) && isGlobal(selectedObject(object), "attributes");
    }

    // vars.x -> cml.variable("x"), attributes.headers.x -> cml.header("x"), attributes.method -> cml.header(...)
    private String emitExchangeAccess(DataWeaveAst node) {
        String key = selectedKey(node);
        DataWeaveAst object = selectedObject(node);
        if (isGlobal(object, "vars") || isGlobal(object, "flowVars")) {
            return "cml.variable(" + string(key) + ")";
        }
        if (isGlobal(object, "attributes")) {
            String header = HTTP_ATTRIBUTES.get(key);
            return header != null ? "cml.header(" + string(header) + ")" : todo("Mule attribute", "attributes." + key);
        }
        return "cml.header(" + string(key) + ")";
    }

    private boolean isGlobal(DataWeaveAst node, String name) {
        return node instanceof Identifier id && id.name().equals(name) && lookup(name) == null;
    }

    // The key of x.key or x["key"], or null
    private static String selectedKey(DataWeaveAst node) {
        if (node instanceof FieldAccess fa) {
            return fa.field();
        }
        if (node instanceof IndexAccess ia && ia.index() instanceof StringLit s) {
            return s.value();
        }
        return null;
    }

    private static DataWeaveAst selectedObject(DataWeaveAst node) {
        if (node instanceof FieldAccess fa) {
            return fa.object();
        }
        return node instanceof IndexAccess ia ? ia.object() : null;
    }

    private String emitExistenceCheck(ExistenceCheck ec) {
        DataWeaveAst expr = ec.expr();
        if (expr instanceof FieldAccess fa && !isExchangeAccess(fa)) {
            return lib("has") + "(" + emitSelector(fa.object(), true) + ", " + string(fa.field()) + ")";
        }
        if (expr instanceof AttributeAccess aa) {
            return lib("has") + "(" + emitSelector(aa.object(), true) + ", " + string("@" + aa.attribute()) + ")";
        }
        return "(" + emit(expr) + " != null)";
    }

    // -- Objects

    private String emitObject(ObjectLit obj) {
        // fields are emitted as object literals, and an object spread (expr) is merged in between
        List<String> parts = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        for (ObjectEntry entry : obj.entries()) {
            if (entry.key() == null) {
                if (!fields.isEmpty()) {
                    parts.add(objectLiteral(fields));
                    fields = new ArrayList<>();
                }
                String spread = lib("toObject") + "(" + emit(entry.value()) + ")";
                if (entry.condition() != null) {
                    spread = "(if " + emit(entry.condition()) + " then " + spread + " else {})";
                }
                parts.add(spread);
            } else {
                fields.add(emitField(entry));
            }
        }
        if (!fields.isEmpty() || parts.isEmpty()) {
            parts.add(objectLiteral(fields));
        }
        return parts.size() == 1 ? parts.get(0) : "(" + String.join(" + ", parts) + ")";
    }

    private static String objectLiteral(List<String> fields) {
        if (fields.isEmpty()) {
            return "{}";
        }
        return "{\n" + indent(String.join(",\n", fields)) + "\n}";
    }

    private String emitField(ObjectEntry entry) {
        String key;
        if (entry.dynamic()) {
            DataWeaveAst k = entry.key();
            key = k instanceof StringLit || k instanceof Interpolation ? emit(k) : lib("str") + "(" + emit(k) + ")";
        } else {
            key = string(((StringLit) entry.key()).value());
        }
        String value = emit(entry.value());
        if (entry.condition() != null) {
            // a field with a null name is left out
            return "[if " + emit(entry.condition()) + " then " + key + " else null]: " + value;
        }
        if (entry.dynamic()) {
            return "[" + key + "]: " + value;
        }
        String name = ((StringLit) entry.key()).value();
        return (isJsonnetIdentifier(name) ? name : key) + ": " + value;
    }

    private static boolean isJsonnetIdentifier(String name) {
        return name.matches("[A-Za-z_][A-Za-z0-9_]*") && !JSONNET_RESERVED.contains(name);
    }

    // -- Operators

    private String emitBinaryOp(BinaryOp op) {
        DataWeaveAst left = op.left();
        DataWeaveAst right = op.right();
        String o = op.op();
        if (("+".equals(o) || "-".equals(o)) && isPeriod(right)) {
            String period = ((TemporalLit) right).value();
            if ("-".equals(o)) {
                period = period.startsWith("-") ? period.substring(1) : "-" + period;
            }
            return "cml.dateAdd(" + emit(left) + ", " + string(period) + ")";
        }
        if ("+".equals(o) && isPeriod(left)) {
            return "cml.dateAdd(" + emit(right) + ", " + string(((TemporalLit) left).value()) + ")";
        }
        if ("-".equals(o) && (right instanceof StringLit || right instanceof ArrayLit)) {
            // payload - "password" removes a key, and xs - [1] an element
            return lib("minus") + "(" + emit(left) + ", " + emit(right) + ")";
        }
        return switch (o) {
            case "--" -> lib("removeAll") + "(" + emit(left) + ", " + emit(right) + ")";
            case "~=" -> lib("similar") + "(" + emit(left) + ", " + emit(right) + ")";
            case "++" -> operand(left) + " + " + operand(right);
            case "and" -> operand(left) + " && " + operand(right);
            case "or" -> operand(left) + " || " + operand(right);
            default -> operand(left) + " " + o + " " + operand(right);
        };
    }

    private static boolean isPeriod(DataWeaveAst node) {
        return node instanceof TemporalLit t && t.isPeriod();
    }

    private String emitCoercion(TypeCoercion tc) {
        Map<String, String> properties = new LinkedHashMap<>(tc.properties());
        String format = properties.remove("format");
        properties.remove("class"); // the Java class only matters for Java output
        if (!properties.isEmpty()) {
            return todo("coercion property", tc.type() + " " + properties);
        }
        String expr = emit(tc.expr());
        return switch (tc.type()) {
            case "String" -> format != null
                    ? "cml.format(" + expr + ", " + string(format) + ")" : lib("toString") + "(" + expr + ")";
            case "Number" -> format != null ? todo("Number coercion with a format", format) : "cml.toDecimal(" + expr + ")";
            case "Boolean" -> "cml.toBoolean(" + expr + ")";
            case "Date", "DateTime", "LocalDateTime", "Time", "LocalTime" -> "cml.parseDateTime(" + expr + ", "
                                                                             + (format != null ? string(format) : "null")
                                                                             + ", " + string(tc.type()) + ")";
            case "Object", "Array", "Any" -> expr;
            default -> todo("coercion to type", tc.type());
        };
    }

    private String typeCheck(String expr, String type) {
        return switch (type) {
            case "String" -> "std.isString(" + expr + ")";
            case "Number" -> "std.isNumber(" + expr + ")";
            case "Boolean" -> "std.isBoolean(" + expr + ")";
            case "Object" -> "std.isObject(" + expr + ")";
            case "Array" -> "std.isArray(" + expr + ")";
            case "Function" -> "std.isFunction(" + expr + ")";
            case "Null" -> "(" + expr + " == null)";
            case "Any" -> "true";
            default -> todo("type check", "is " + type);
        };
    }

    // -- Functions

    private String emitCall(FunctionCall fc) {
        String name = fc.name();
        List<DataWeaveAst> args = fc.args();
        Binding binding = lookup(name);
        if (binding != null) {
            List<String> emitted = new ArrayList<>();
            for (DataWeaveAst arg : args) {
                emitted.add(emit(arg));
            }
            return binding.name() + "(" + String.join(", ", emitted) + ")";
        }
        if ("reduce".equals(name)) {
            return emitReduce(args);
        }
        FunctionArg functionArg = FUNCTION_ARGS.get(name);
        if (functionArg != null) {
            String target = switch (name) {
                case "takeWhile", "dropWhile" -> "ds.arrays." + name;
                case "then" -> lib("pipe"); // then is a Jsonnet keyword
                default -> lib(name);
            };
            return call(name, target, args, 2, functionArg);
        }
        if (DS_STRINGS.contains(name)) {
            if (args.size() == 1) {
                return lib("nullSafe") + "(ds.strings." + name + ", " + emit(args.get(0)) + ")";
            }
            return call(name, "ds.strings." + name, args, args.size(), null);
        }
        if (LIB_FUNCTIONS_1.contains(name)) {
            return call(name, lib(name), args, 1, null);
        }
        if (LIB_FUNCTIONS_2.contains(name)) {
            if (isRegex(args, 1) && ("contains".equals(name) || "splitBy".equals(name))) {
                // contains /re/ -> any match; splitBy /re/ -> split on the matches
                String fn = "contains".equals(name) ? "containsMatch" : "splitByMatch";
                String scan = "contains".equals(name) ? "ds.scan" : "ds.splitBy";
                return lib(fn) + "(" + emit(args.get(0)) + ", " + scan + ", " + regex(args.get(1)) + ")";
            }
            return call(name, lib(name), args, 2, null);
        }
        return switch (name) {
            case "replace" -> emitReplace(args);
            case "matches", "scan", "find" -> args.size() == 2
                    ? "ds." + name + "(" + emit(args.get(0)) + ", " + regex(args.get(1)) + ")"
                    : wrongArguments(name, args);
            // numbers
            case "ceil", "floor", "sqrt" -> call(name, "std." + name, args, 1, null);
            case "pow", "mod" -> call(name, "std." + name, args, 2, null);
            case "random" -> call(name, "ds.math.random", args, 0, null);
            case "randomInt" -> call(name, "ds.math.randomInt", args, 1, null);
            // objects and arrays
            case "mergeWith" -> call(name, "ds.objects.mergeWith", args, 2, null);
            case "unzip" -> call(name, "ds.unzip", args, 1, null);
            case "to" -> call(name, lib("range"), args, 2, null);
            // others
            case "now" -> call(name, "cml.now", args, 0, null);
            case "uuid" -> call(name, "cml.uuid", args, 0, null);
            case "p" -> call(name, "cml.properties", args, 1, null);
            case "log" -> emitLog(args);
            case "fail" -> args.size() == 1 ? "error " + operand(args.get(0)) : wrongArguments(name, args);
            case "read", "write" -> emitReadWrite(name, args);
            default -> todo("unknown function", name);
        };
    }

    private String emitReplace(List<DataWeaveAst> args) {
        if (args.size() != 3) {
            return wrongArguments("replace", args);
        }
        if (args.get(2) instanceof Lambda) {
            return todo("replace with a function", "replace ... with (m) -> ...");
        }
        if (isRegex(args, 1)) {
            return "ds.replace(" + emit(args.get(0)) + ", " + regex(args.get(1)) + ", " + emit(args.get(2)) + ")";
        }
        return call("replace", lib("replace"), args, 3, null);
    }

    private String emitLog(List<DataWeaveAst> args) {
        // log(value) and log(prefix, value) return the value
        if (args.size() == 1) {
            return emit(args.get(0));
        }
        if (args.size() == 2) {
            return "std.trace(" + lib("str") + "(" + emit(args.get(0)) + "), " + emit(args.get(1)) + ")";
        }
        return wrongArguments("log", args);
    }

    private String emitReadWrite(String name, List<DataWeaveAst> args) {
        if (args.isEmpty() || args.size() > 3) {
            return wrongArguments(name, args);
        }
        if (args.size() == 3) {
            return todo(name + " with reader or writer properties", name);
        }
        String mediaType = args.size() == 2 ? emit(args.get(1)) : string("application/json");
        return "ds." + name + "(" + emit(args.get(0)) + ", " + mediaType + ")";
    }

    private static boolean isRegex(List<DataWeaveAst> args, int index) {
        return args.size() > index && args.get(index) instanceof RegexLit;
    }

    // A regular expression argument: a regex literal, or a string used as pattern
    private String regex(DataWeaveAst arg) {
        if (arg instanceof RegexLit r) {
            return string(r.pattern());
        }
        return emit(arg);
    }

    // target(args...), where the argument at the position of functionArg is a function
    private String call(String name, String target, List<DataWeaveAst> args, int arity, FunctionArg functionArg) {
        if (args.size() != arity) {
            return wrongArguments(name, args);
        }
        List<String> emitted = new ArrayList<>();
        for (int i = 0; i < args.size(); i++) {
            if (functionArg != null && i == functionArg.position()) {
                emitted.add(emitFunctionArg(args.get(i), functionArg.params()));
            } else if (functionArg != null && i == 0 && readsItemAttributes(args.get(functionArg.position()))) {
                // keep XML elements with only text as objects, as the function reads their attributes
                emitted.add(emitSelector(args.get(i), true));
            } else {
                emitted.add(emit(args.get(i)));
            }
        }
        return target + "(" + String.join(", ", emitted) + ")";
    }

    private String wrongArguments(String name, List<DataWeaveAst> args) {
        return todo("unexpected number of arguments (" + args.size() + ")", name);
    }

    private String emitReduce(List<DataWeaveAst> args) {
        if (args.size() != 2) {
            return wrongArguments("reduce", args);
        }
        String array = emit(args.get(0));
        DataWeaveAst fn = args.get(1);
        if (fn instanceof Lambda lam) {
            // (item, acc = init) -> body; without an initial value the accumulator starts with the first item
            if (lam.params().size() != 2) {
                return todo("reduce with a lambda of " + lam.params().size() + " parameter(s)", "reduce");
            }
            DataWeaveAst init = lam.params().get(1).defaultValue();
            Lambda withoutInit = new Lambda(
                    List.of(lam.params().get(0), new LambdaParam(lam.params().get(1).name(), null)), lam.body());
            String function = emitLambda(withoutInit, 2);
            if (init != null) {
                return lib("reduce") + "(" + array + ", " + function + ", " + emit(init) + ")";
            }
            return lib("reduce1") + "(" + array + ", " + function + ")";
        }
        return lib("reduce1") + "(" + array + ", " + emitFunctionArg(fn, List.of("item", "acc")) + ")";
    }

    // A function argument of a higher-order function, which DataWeave calls with params.size() arguments
    private String emitFunctionArg(DataWeaveAst arg, List<String> params) {
        if (arg instanceof Lambda lam) {
            if (lam.params().size() > params.size()) {
                return todo("lambda with " + lam.params().size() + " parameters", "expected at most " + params.size());
            }
            return emitLambda(lam, params.size());
        }
        if (containsDollar(arg)) {
            return emitImplicitLambda(arg, params);
        }
        if (arg instanceof Identifier id && lookup(id.name()) != null && lookup(id.name()).arity() >= 0) {
            // a function by name: pass the arguments it accepts
            Binding fn = lookup(id.name());
            if (fn.arity() > params.size()) {
                return todo("function with " + fn.arity() + " parameters", id.name());
            }
            if (fn.arity() == params.size()) {
                return fn.name();
            }
            List<String> names = freshNames(params);
            return "function(" + String.join(", ", names) + ") " + fn.name() + "("
                   + String.join(", ", names.subList(0, fn.arity())) + ")";
        }
        // an expression that gives a function
        return lib("fn") + "(" + emit(arg) + ")";
    }

    // An expression with $, $$, $$$ (such as payload map $.name): a function of the parameters
    private String emitImplicitLambda(DataWeaveAst body, List<String> params) {
        List<String> names = freshNames(params);
        Map<String, Binding> scope = new HashMap<>();
        for (String name : names) {
            scope.put("$" + name, new Binding(name, Binding.VALUE)); // reserve the names
        }
        List<String> saved = dollars;
        dollars = names;
        scopes.push(scope);
        try {
            return "function(" + String.join(", ", names) + ") " + emit(body);
        } finally {
            scopes.pop();
            dollars = saved;
        }
    }

    // A lambda with at least the given number of parameters (DataWeave passes arguments a lambda does not declare)
    private String emitLambda(Lambda lam, int arity) {
        Map<String, Binding> scope = new HashMap<>();
        List<String> params = new ArrayList<>();
        Set<String> used = new HashSet<>();
        for (LambdaParam param : lam.params()) {
            String name = jsonnetName(param.name());
            used.add(name);
            String p = name;
            if (param.defaultValue() != null) {
                p += " = " + emit(param.defaultValue());
            }
            params.add(p);
            scope.put(param.name(), new Binding(name, Binding.VALUE));
        }
        for (int i = lam.params().size(); i < arity; i++) {
            String name = "_" + i;
            while (used.contains(name)) {
                name = "_" + name;
            }
            params.add(name);
        }
        List<String> saved = dollars;
        dollars = null;
        scopes.push(scope);
        try {
            return "function(" + String.join(", ", params) + ") " + emit(lam.body());
        } finally {
            scopes.pop();
            dollars = saved;
        }
    }

    // Names for the given parameters that do not hide a variable in scope
    private List<String> freshNames(List<String> params) {
        List<String> names = new ArrayList<>();
        for (String param : params) {
            String name = param;
            int n = 1;
            while (isNameInScope(name) || names.contains(name)) {
                name = param + ++n;
            }
            names.add(name);
        }
        return names;
    }

    // Whether a function argument reads an attribute of its (first) parameter: (e) -> e.@id, or $.@id
    private static boolean readsItemAttributes(DataWeaveAst fn) {
        if (fn instanceof Lambda lam) {
            if (lam.params().isEmpty()) {
                return false;
            }
            String name = lam.params().get(0).name();
            return anyNode(lam.body(), n -> n instanceof AttributeAccess aa && aa.object() instanceof Identifier id
                    && id.name().equals(name));
        }
        return anyNode(fn, n -> n instanceof AttributeAccess aa && aa.object() instanceof Dollar d && d.level() == 1);
    }

    private static boolean anyNode(DataWeaveAst node, Predicate<DataWeaveAst> predicate) {
        if (node == null) {
            return false;
        }
        if (predicate.test(node)) {
            return true;
        }
        for (Object child : children(node)) {
            if (child instanceof DataWeaveAst c && anyNode(c, predicate)) {
                return true;
            }
        }
        return false;
    }

    // The child nodes of a node (its record components that are nodes or lists of nodes)
    private static List<Object> children(DataWeaveAst node) {
        List<Object> children = new ArrayList<>();
        for (RecordComponent component : node.getClass().getRecordComponents()) {
            Object value;
            try {
                value = component.getAccessor().invoke(node);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
            if (value instanceof DataWeaveAst) {
                children.add(value);
            } else if (value instanceof List<?> list) {
                children.addAll(list);
            }
        }
        return children;
    }

    // Whether the node uses $, $$ or $$$ of the enclosing implicit lambda; not those of a nested function argument
    private static boolean containsDollar(DataWeaveAst node) {
        if (node == null || node instanceof Lambda) {
            return false;
        }
        if (node instanceof Dollar) {
            return true;
        }
        if (node instanceof FilterSelector fs) {
            return containsDollar(fs.object());
        }
        if (node instanceof FunctionCall fc) {
            FunctionArg functionArg = FUNCTION_ARGS.get(fc.name());
            for (int i = 0; i < fc.args().size(); i++) {
                if ((functionArg == null || i != functionArg.position()) && containsDollar(fc.args().get(i))) {
                    return true;
                }
            }
            return false;
        }
        for (Object child : children(node)) {
            if (child instanceof DataWeaveAst c && containsDollar(c)) {
                return true;
            }
        }
        return false;
    }

    // -- Match

    private String emitMatch(Match m) {
        String value = "match" + (matchCount++ == 0 ? "" : String.valueOf(matchCount));
        StringBuilder sb = new StringBuilder("(local ").append(value).append(" = ").append(emit(m.expr())).append(";\n");
        scopes.push(Map.of("$" + value, new Binding(value, Binding.VALUE))); // reserve the name
        try {
            for (MatchCase c : m.cases()) {
                if (c.otherwise()) {
                    return sb.append(emit(c.body())).append(')').toString();
                }
                sb.append("if ").append(emitCaseCondition(c, value)).append(" then ").append(emitCaseBody(c, value))
                        .append("\nelse ");
            }
            return sb.append("error 'No case of the match expression matched: ' + std.toString(").append(value)
                    .append("))").toString();
        } finally {
            scopes.pop();
        }
    }

    // The value a case binds: the matched value, or for a regular expression the matched groups
    private static String caseValue(MatchCase c, String value) {
        return c.regex() != null ? "ds.scan(" + value + ", " + string(c.regex()) + ")[0]" : value;
    }

    private String emitCaseCondition(MatchCase c, String value) {
        List<String> conditions = new ArrayList<>();
        if (c.regex() != null) {
            conditions.add("std.isString(" + value + ") && ds.matches(" + value + ", " + string(c.regex()) + ")");
        }
        if (c.literal() != null) {
            conditions.add(value + " == " + operand(c.literal()));
        }
        if (c.type() != null) {
            conditions.add(typeCheck(value, c.type()));
        }
        if (c.guard() != null) {
            if (c.binding() != null) {
                String name = jsonnetName(c.binding());
                scopes.push(Map.of(c.binding(), new Binding(name, Binding.VALUE)));
                try {
                    conditions.add("(local " + name + " = " + caseValue(c, value) + "; " + emit(c.guard()) + ")");
                } finally {
                    scopes.pop();
                }
            } else {
                conditions.add(operand(c.guard()));
            }
        }
        return conditions.isEmpty() ? "true" : String.join(" && ", conditions);
    }

    private String emitCaseBody(MatchCase c, String value) {
        if (c.binding() == null) {
            return emit(c.body());
        }
        String name = jsonnetName(c.binding());
        scopes.push(Map.of(c.binding(), new Binding(name, Binding.VALUE)));
        try {
            return "(local " + name + " = " + caseValue(c, value) + "; " + emit(c.body()) + ")";
        } finally {
            scopes.pop();
        }
    }

    // -- Declarations

    private String emitBlock(Block block) {
        Map<String, Binding> scope = new HashMap<>();
        Set<String> functions = new HashSet<>();
        StringBuilder sb = new StringBuilder();
        for (DataWeaveAst decl : block.declarations()) {
            if (decl instanceof VarDecl vd) {
                int arity = vd.value() instanceof Lambda lam ? lam.params().size() : Binding.VALUE;
                scope.put(vd.name(), new Binding(jsonnetName(vd.name()), arity));
            } else if (decl instanceof FunDecl fd) {
                if (!functions.add(fd.name())) {
                    sb.append(todoComment("overloaded function", fd.name()));
                }
                scope.put(fd.name(), new Binding(jsonnetName(fd.name()), fd.params().size()));
            }
        }
        scopes.push(scope);
        try {
            List<String> bindings = new ArrayList<>();
            for (DataWeaveAst decl : block.declarations()) {
                if (decl instanceof VarDecl vd) {
                    bindings.add(jsonnetName(vd.name()) + " = " + emit(vd.value()));
                } else if (decl instanceof FunDecl fd) {
                    bindings.add(emitFunDecl(fd));
                } else if (decl instanceof Unsupported u) {
                    sb.append(todoComment(u.reason(), u.originalText()));
                } else {
                    sb.append(todoComment("unsupported declaration", decl.getClass().getSimpleName()));
                }
            }
            if (!bindings.isEmpty()) {
                // one local: the functions may call each other
                sb.append("local ").append(String.join(",\n      ", bindings)).append(";\n");
            }
            return sb.append(emit(block.expr())).toString();
        } finally {
            scopes.pop();
        }
    }

    private String emitFunDecl(FunDecl fd) {
        Map<String, Binding> scope = new HashMap<>();
        List<String> params = new ArrayList<>();
        for (LambdaParam param : fd.params()) {
            String name = jsonnetName(param.name());
            params.add(param.defaultValue() != null ? name + " = " + emit(param.defaultValue()) : name);
            scope.put(param.name(), new Binding(name, Binding.VALUE));
        }
        List<String> saved = dollars;
        dollars = null;
        scopes.push(scope);
        try {
            return jsonnetName(fd.name()) + "(" + String.join(", ", params) + ") = " + emit(fd.body());
        } finally {
            scopes.pop();
            dollars = saved;
        }
    }

    // -- Helpers

    private Binding lookup(String name) {
        for (Map<String, Binding> scope : scopes) {
            Binding binding = scope.get(name);
            if (binding != null) {
                return binding;
            }
        }
        return null;
    }

    private boolean isNameInScope(String jsonnetName) {
        if (JSONNET_RESERVED.contains(jsonnetName)) {
            return true;
        }
        for (Map<String, Binding> scope : scopes) {
            for (Binding binding : scope.values()) {
                if (binding.name().equals(jsonnetName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String jsonnetName(String name) {
        return JSONNET_RESERVED.contains(name) ? name + "_" : name;
    }

    private String lib(String function) {
        needsDataWeaveLib = true;
        return "dw." + function;
    }

    // A construct that cannot be converted, in place of an expression
    private String todo(String reason, String text) {
        String comment = todoComment(reason, text);
        return comment.isEmpty() ? "null" : comment.substring(0, comment.length() - 1) + " null";
    }

    // A construct that cannot be converted, as a line before the expression
    private String todoComment(String reason, String text) {
        todoCount++;
        if (!includeComments) {
            return "";
        }
        String comment = reason + (text != null && !text.isEmpty() ? ": " + text : "");
        return "/* TODO: manual conversion needed -- " + comment.replace("*/", "* /") + " */\n";
    }

    private static String indent(String text) {
        return "  " + text.replace("\n", "\n  ");
    }

    /** A Jsonnet string literal. */
    static String string(String value) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (ch < 0x20) {
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
