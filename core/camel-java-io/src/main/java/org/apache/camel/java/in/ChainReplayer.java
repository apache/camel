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

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IllegalFormatException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.CamelContext;
import org.apache.camel.LineNumberAware;
import org.apache.camel.builder.AggregationStrategies;
import org.apache.camel.builder.Builder;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.builder.RouteConfigurationBuilder;
import org.apache.camel.builder.ValueBuilder;
import org.apache.camel.java.in.JavaChainParser.BinOp;
import org.apache.camel.java.in.JavaChainParser.Bool;
import org.apache.camel.java.in.JavaChainParser.Call;
import org.apache.camel.java.in.JavaChainParser.Chain;
import org.apache.camel.java.in.JavaChainParser.Chr;
import org.apache.camel.java.in.JavaChainParser.ClassLit;
import org.apache.camel.java.in.JavaChainParser.ClassName;
import org.apache.camel.java.in.JavaChainParser.Concat;
import org.apache.camel.java.in.JavaChainParser.Lambda;
import org.apache.camel.java.in.JavaChainParser.New;
import org.apache.camel.java.in.JavaChainParser.Node;
import org.apache.camel.java.in.JavaChainParser.Null;
import org.apache.camel.java.in.JavaChainParser.Num;
import org.apache.camel.java.in.JavaChainParser.Opaque;
import org.apache.camel.java.in.JavaChainParser.Ref;
import org.apache.camel.java.in.JavaChainParser.Str;
import org.apache.camel.model.ChoiceDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteConfigurationsDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteTemplatesDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.SwitchCaseDefinition;
import org.apache.camel.model.SwitchDefinition;
import org.apache.camel.model.WhenDefinition;
import org.apache.camel.model.language.XPathExpression;
import org.apache.camel.model.rest.RestsDefinition;
import org.apache.camel.support.builder.Namespaces;

/**
 * Builds the model by replaying the chains of a Java source against Camel's own DSL: {@code from("a").to("b")} calls
 * {@code from("a")} on a route builder and {@code to("b")} on the route it returns. So {@code end()}, blocks,
 * expression clauses and ids behave exactly as in a compiled route, for every EIP.
 * <p/>
 * Nothing of the parsed project runs or is loaded. Only methods of Camel's model and builder types are called (see
 * {@link #isAllowed(Method)}); a class of the project becomes an empty stub with its name ({@link StubClassLoader}); a
 * lambda, an anonymous class or {@code new Foo()} becomes a placeholder object of the type the DSL asks for; a value
 * that cannot be worked out becomes a marked string. Each of those is reported as unresolved.
 */
final class ChainReplayer {

    /** The DSL: a route configuration builder is also a route builder, so it covers routes, rests and templates. */
    static final class ReplayBuilder extends RouteConfigurationBuilder {
        @Override
        public void configure() {
            // the chains are replayed on it from outside
        }

        @Override
        public void configuration() {
            // the chains are replayed on it from outside
        }

        /** As the DSL builds it, but keeping property placeholders as written: there is no context to resolve them. */
        @Override
        public ValueBuilder xpath(String value, Class<?> resultType, Namespaces namespaces) {
            XPathExpression exp = new XPathExpression(value);
            exp.setResultType(resultType);
            if (namespaces != null) {
                exp.setNamespaces(namespaces.getNamespaces());
            }
            return new ValueBuilder(exp);
        }
    }

    private static final Map<String, Class<?>> PRIMITIVES = Map.of(
            "byte", byte.class, "short", short.class, "int", int.class, "long", long.class, "float", float.class,
            "double", double.class, "boolean", boolean.class, "char", char.class);

    private static final Set<String> READABLE_JDK = Set.of(
            "java.lang.Integer", "java.lang.Long", "java.lang.Short", "java.lang.Byte", "java.lang.Double",
            "java.lang.Float", "java.lang.Boolean", "java.lang.Character", "java.util.concurrent.TimeUnit",
            "java.nio.charset.StandardCharsets", "java.util.zip.Deflater");

    /** Classes whose static DSL methods a route may call, qualified or statically imported: language(...), and(...). */
    private static final List<Class<?>> STATIC_DSL
            = List.of(Builder.class, PredicateBuilder.class, AggregationStrategies.class);

    /** Where a Camel type named without an import is looked for, as a RouteBuilder usually imports them. */
    private static final List<String> CAMEL_PACKAGES = List.of(
            "org.apache.camel.", "org.apache.camel.model.", "org.apache.camel.builder.", "org.apache.camel.model.language.",
            "org.apache.camel.model.dataformat.", "org.apache.camel.model.rest.", "org.apache.camel.model.loadbalancer.");

    /** A value the replay could not work out, until the DSL method it goes to says what type it must be. */
    private record Unknown(Node node, String reason) {
    }

    private static final Set<String> DENIED_METHODS = Set.of(
            "getContext", "getCamelContext", "setContext", "setCamelContext", "addRoutesToCamelContext",
            "addRouteConfigurationsToCamelContext", "addTemplatedRoutesToCamelContext",
            "includeRoutes", "bindToRegistry", "propertyInject", "endpoint", "getClass", "wait", "notify", "notifyAll");

    /** The life cycle of a route builder, which a parse never runs: only the DSL it offers is called. */
    private static final List<String> BUILDER_LIFE_CYCLE = List.of(
            "populate", "configure", "configuration", "prepare", "update", "initialize", "set", "check", "add", "remove",
            "customize");

    /** The builder of the configure() being replayed. */
    private ReplayBuilder builder;
    /** The parameter naming the builder in a builder lambda (rb -> rb.from(...)), null in configure(). */
    private String builderParameter;
    private final JavaChainParser.Source source;
    private final StubClassLoader stubs;
    private final EndpointDslResolver endpointDsl;
    private final ConstantResolver constants;
    private final List<JavaParseResult.Unresolved> unresolved = new ArrayList<>();
    private final Set<String> resolving = new HashSet<>();

    ChainReplayer(JavaChainParser.Source source) {
        this(source, null, null);
    }

    /**
     * @param endpointDsl the resolver of endpoint DSL calls: asked for every call that is not the DSL when given; null
     *                    uses the naming rules, and only for sources that use the endpoint DSL
     */
    ChainReplayer(JavaChainParser.Source source, EndpointDslResolver endpointDsl) {
        this(source, endpointDsl, null);
    }

    ChainReplayer(JavaChainParser.Source source, EndpointDslResolver endpointDsl, ConstantResolver constants) {
        this.constants = constants;
        this.source = source;
        this.endpointDsl = endpointDsl != null ? endpointDsl : source.endpointDsl() ? EndpointDslResolver.NAMING : null;
        this.stubs = new StubClassLoader(ChainReplayer.class.getClassLoader());
    }

    JavaParseResult replay() {
        RoutesDefinition routes = null;
        RestsDefinition rests = null;
        RouteTemplatesDefinition templates = null;
        RouteConfigurationsDefinition configurations = null;
        // each configure() is a route builder of its own: its global onException and errorHandler come before its routes
        for (int b = 0; b < source.builders().size(); b++) {
            List<Node> statements = source.builders().get(b);
            builderParameter = source.builderParameters().get(b);
            builder = new ReplayBuilder();
            for (Node statement : statements) {
                if (statement instanceof Chain chain && configuresTheContext(chain)) {
                    // getContext().getComponent("sql", SqlComponent.class).setDataSource(ds): not a route
                    report(chain, CONFIGURES_THE_CONTEXT);
                } else if (statement instanceof Chain chain) {
                    Object value = evaluate(chain);
                    if (value instanceof Unknown u) {
                        report(u.node(), u.reason());
                    }
                } else {
                    report(statement, "not a route chain");
                }
            }
            // the model the other DSLs have: class names, and languages rather than the Java objects standing for them
            ModelNormalizer.normalize(builder.getRouteCollection());
            ModelNormalizer.normalize(builder.getRestCollection());
            ModelNormalizer.normalize(builder.getRouteTemplateCollection());
            ModelNormalizer.normalize(builder.getRouteConfigurationCollection());
            if (routes == null) {
                routes = builder.getRouteCollection();
                rests = builder.getRestCollection();
                templates = builder.getRouteTemplateCollection();
                configurations = builder.getRouteConfigurationCollection();
            } else {
                RoutesDefinition more = builder.getRouteCollection();
                routes.getRoutes().addAll(more.getRoutes());
                routes.getOnExceptions().addAll(more.getOnExceptions());
                routes.getIntercepts().addAll(more.getIntercepts());
                routes.getInterceptFroms().addAll(more.getInterceptFroms());
                routes.getInterceptSendTos().addAll(more.getInterceptSendTos());
                routes.getOnCompletions().addAll(more.getOnCompletions());
                rests.getRests().addAll(builder.getRestCollection().getRests());
                templates.getRouteTemplates().addAll(builder.getRouteTemplateCollection().getRouteTemplates());
                configurations.getRouteConfigurations()
                        .addAll(builder.getRouteConfigurationCollection().getRouteConfigurations());
            }
        }
        if (routes == null) {
            builder = new ReplayBuilder();
            routes = builder.getRouteCollection();
            rests = builder.getRestCollection();
            templates = builder.getRouteTemplateCollection();
            configurations = builder.getRouteConfigurationCollection();
        }
        return new JavaParseResult(routes, rests, templates, configurations, List.copyOf(unresolved));
    }

    /** The constants of the source that are data (Strings, numbers, booleans), by name. */
    Map<String, Object> constantValues() {
        Map<String, Object> answer = new LinkedHashMap<>();
        source.constants().forEach((name, node) -> {
            Object v = evaluate(node);
            if (v instanceof String || v instanceof Number || v instanceof Boolean) {
                answer.put(name, v);
            }
        });
        return answer;
    }

    // ---- values ----

    /** The value of a node, or an {@link Unknown} for what only a DSL parameter type can settle. */
    private Object evaluate(Node node) {
        if (node instanceof Str s) {
            return s.value();
        } else if (node instanceof Chr c) {
            return c.value();
        } else if (node instanceof Num n) {
            return number(n.text());
        } else if (node instanceof Bool b) {
            return b.value();
        } else if (node instanceof Null) {
            return null;
        } else if (node instanceof Concat c) {
            return concat(c);
        } else if (node instanceof Ref r) {
            return reference(r);
        } else if (node instanceof ClassLit c) {
            return classLiteral(c);
        } else if (node instanceof ClassName c) {
            return className(c);
        } else if (node instanceof BinOp b) {
            return arithmetic(b);
        } else if (node instanceof Chain c) {
            return chain(c);
        } else if (node instanceof Lambda) {
            return new Unknown(node, "a lambda or method reference");
        } else if (node instanceof New n) {
            Object created = exception(n);
            if (created != null) {
                return created;
            }
            return new Unknown(n, n.anonymous() ? "an anonymous class" : "an object created in the route");
        }
        return new Unknown(node, "an expression the parser does not evaluate");
    }

    /**
     * {@code new IllegalArgumentException("Forced")}: an exception of the JDK or Camel is created, as creating one runs
     * no code of the project and has no side effect; null for anything else.
     */
    private Object exception(New n) {
        if (n.anonymous()) {
            return null;
        }
        Class<?> type = loadable(n.type());
        if (type == null || !Throwable.class.isAssignableFrom(type) || Modifier.isAbstract(type.getModifiers())) {
            return null;
        }
        Object[] values = new Object[n.args().size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = evaluate(n.args().get(i));
            if (values[i] instanceof Unknown) {
                return null;
            }
        }
        for (Constructor<?> c : type.getConstructors()) {
            if (c.getParameterCount() != values.length || c.isVarArgs() || !onlyMessagesAndCauses(c)) {
                continue;
            }
            Object[] args = convertAll(c, values, n.args(), new ArrayList<>(), new int[1]);
            if (args != null) {
                try {
                    return c.newInstance(args);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /** A constructor of an exception that takes messages and causes only, as they all do: nothing else is created. */
    private static boolean onlyMessagesAndCauses(Constructor<?> c) {
        for (Class<?> p : c.getParameterTypes()) {
            if (p != String.class && !Throwable.class.isAssignableFrom(p)) {
                return false;
            }
        }
        return true;
    }

    private static Object number(String text) {
        String t = text.replace("_", "");
        try {
            if (t.endsWith("L") || t.endsWith("l")) {
                return Long.parseLong(t.substring(0, t.length() - 1));
            }
            if (t.endsWith("d") || t.endsWith("D") || t.endsWith("f") || t.endsWith("F") || t.contains(".")) {
                return Double.parseDouble(t.replaceAll("[dDfF]$", ""));
            }
            long l = Long.decode(t);
            return l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE ? (Object) (int) l : (Object) l;
        } catch (NumberFormatException e) {
            return t;
        }
    }

    /** {@code a + b + ...} as Java works it out: numbers are added until a String makes the rest text. */
    private Object concat(Concat c) {
        Object acc = null;
        boolean first = true;
        for (Node part : c.parts()) {
            Object v = evaluate(part);
            if (v instanceof Unknown) {
                return new Unknown(c, "a value built from parts the parser cannot work out");
            }
            if (first) {
                acc = v;
                first = false;
            } else if (acc instanceof Number a && v instanceof Number b) {
                acc = add(a, b);
            } else {
                acc = String.valueOf(acc) + v;
            }
        }
        return acc instanceof String ? acc : acc instanceof Number ? acc : String.valueOf(acc);
    }

    private static Number add(Number a, Number b) {
        if (a instanceof Double || b instanceof Double) {
            return a.doubleValue() + b.doubleValue();
        }
        long sum = a.longValue() + b.longValue();
        return a instanceof Long || b instanceof Long ? (Number) sum : narrow(sum);
    }

    /** An int when it fits and both sides were ints, as Java types it. */
    private static Number narrow(long value) {
        return value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE ? (Number) (int) value : (Number) value;
    }

    /** {@code 5 * 1000L}, {@code DELAY / 2}: arithmetic on numbers the parser knows. */
    private Object arithmetic(BinOp b) {
        Object l = evaluate(b.left());
        Object r = evaluate(b.right());
        if (!(l instanceof Number a) || !(r instanceof Number c)) {
            return new Unknown(b, "an expression the parser does not evaluate");
        }
        boolean floating = a instanceof Double || c instanceof Double;
        boolean wide = a instanceof Long || c instanceof Long;
        try {
            if (floating) {
                double x = a.doubleValue();
                double y = c.doubleValue();
                return switch (b.op()) {
                    case "*" -> x * y;
                    case "/" -> x / y;
                    case "%" -> x % y;
                    // only '-' reaches here: '+' is a Concat, not a BinOp
                    default -> x - y;
                };
            }
            long x = a.longValue();
            long y = c.longValue();
            long v = switch (b.op()) {
                case "*" -> x * y;
                case "/" -> x / y;
                case "%" -> x % y;
                // only '-' reaches here: '+' is a Concat, not a BinOp
                default -> x - y;
            };
            return wide ? (Number) v : narrow(v);
        } catch (ArithmeticException e) {
            return new Unknown(b, "an expression the parser does not evaluate");
        }
    }

    /** {@code Foo.class.getName()}: the name of the class, from the imports, without loading it. */
    private Object className(ClassName c) {
        Class<?> type = loadable(c.type());
        String qualified = type != null ? type.getName() : binaryName(qualified(c.type()));
        return switch (c.method()) {
            case "getSimpleName" -> qualified.substring(Math.max(qualified.lastIndexOf('.'), qualified.lastIndexOf('$')) + 1);
            case "getName" -> qualified;
            default -> qualified.replace('$', '.');
        };
    }

    /** A constant of the source, or a static field of a JDK or Camel class such as {@code LoggingLevel.INFO}. */
    private Object reference(Ref r) {
        String name = r.name();
        String simple = name.substring(name.lastIndexOf('.') + 1);
        Node constant = source.constants().get(name);
        if (constant == null && !name.contains(".")) {
            // a statically imported constant, or one inherited from a superclass
            String owner = source.staticImports().get(name);
            List<String> owners = new ArrayList<>();
            if (owner != null) {
                owners.add(owner);
            } else {
                owners.addAll(source.staticWildcards());
                source.superclasses().forEach(sup -> owners.add(qualified(sup)));
            }
            for (String o : owners) {
                Object v = resolved(o, name);
                if (v != null) {
                    return v;
                }
            }
        }
        if (constant == null && name.contains(".")) {
            String ownerName = name.substring(0, name.lastIndexOf('.'));
            if (source.classes().contains(ownerName)) {
                // MyRoutes.URI in MyRoutes
                constant = source.constants().get(simple);
            } else if (loadable(ownerName) == null) {
                // a class not on the class path: a component's header constant, another source file of the project
                Object v = resolved(qualified(ownerName), simple);
                if (v != null) {
                    return v;
                }
                return new Unknown(r, "a constant of a class the parser cannot see");
            }
        }
        if (constant == null && name.contains(".")) {
            Class<?> owner = loadable(name.substring(0, name.lastIndexOf('.')));
            if (owner != null) {
                if (!constantsReadable(owner)) {
                    return new Unknown(r, "a field of a class whose constants the parser does not read");
                }
                try {
                    Field f = owner.getField(simple);
                    if (isConstant(f)) {
                        return f.get(null);
                    }
                    return new Unknown(r, "not a constant");
                } catch (ReflectiveOperationException | LinkageError e) {
                    return new Unknown(r, "no such constant");
                }
            }
        }
        if (constant != null && resolving.add(name)) {
            try {
                Object v = evaluate(constant);
                return v instanceof Unknown ? new Unknown(r, "a constant the parser cannot work out") : v;
            } finally {
                resolving.remove(name);
            }
        }
        return new Unknown(r, "a variable or field the parser cannot see");
    }

    /** A constant the plugged resolver knows, when it is data (a String, a number, a boolean); else null. */
    private Object resolved(String className, String field) {
        if (constants == null) {
            return null;
        }
        try {
            Object v = constants.constant(className, field);
            return v instanceof String || v instanceof Number || v instanceof Boolean ? v : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Reading a static field initializes its class, so only classes whose initialization is known to be harmless:
     * Camel's, and a few of the JDK's.
     */
    static boolean constantsReadable(Class<?> owner) {
        String n = owner.getName();
        return n.startsWith("org.apache.camel.") || READABLE_JDK.contains(n);
    }

    /** A static final String, primitive, boxed primitive or enum constant: data, never an object with behaviour. */
    static boolean isConstant(Field f) {
        int m = f.getModifiers();
        Class<?> t = f.getType();
        return Modifier.isStatic(m) && Modifier.isFinal(m) && Modifier.isPublic(m)
                && (t.isPrimitive() || t == String.class || t.isEnum() || Number.class.isAssignableFrom(t)
                        && t.getName().startsWith("java.lang.") || t == Boolean.class || t == Character.class);
    }

    private Object classLiteral(ClassLit c) {
        Class<?> type = loadable(c.type());
        return type != null ? type : new Unknown(c, "a class of the project");
    }

    /**
     * A JDK or Camel class by its name in the source, resolved with the imports; null for anything else, which is never
     * loaded.
     */
    private Class<?> loadable(String name) {
        if (name.endsWith("[]")) {
            Class<?> component = loadable(name.substring(0, name.length() - 2));
            return component != null ? Array.newInstance(component, 0).getClass() : null;
        }
        Class<?> primitive = PRIMITIVES.get(name);
        if (primitive != null) {
            return primitive;
        }
        List<String> candidates = new ArrayList<>();
        if (name.contains(".") && Character.isLowerCase(name.charAt(0))) {
            candidates.add(name);
        }
        String first = name.contains(".") ? name.substring(0, name.indexOf('.')) : name;
        String imported = source.imports().get(first);
        if (imported != null) {
            candidates.add(imported + name.substring(first.length()));
        }
        candidates.add("java.lang." + name);
        if (imported == null && !Character.isLowerCase(name.charAt(0))) {
            for (String pkg : CAMEL_PACKAGES) {
                candidates.add(pkg + name);
            }
            // a snippet without its imports: TimeUnit.SECONDS, Deflater.BEST_COMPRESSION
            for (String jdk : READABLE_JDK) {
                if (jdk.endsWith("." + first)) {
                    candidates.add(jdk + name.substring(first.length()));
                }
            }
        }
        for (String candidate : candidates) {
            String binary = candidate;
            // Outer.Inner as a binary name: try the dots from the right as $
            for (int i = 0; i < 3; i++) {
                if (binary.startsWith("java.") || binary.startsWith("org.apache.camel.")) {
                    try {
                        return Class.forName(binary, false, ChainReplayer.class.getClassLoader());
                    } catch (ClassNotFoundException | LinkageError e) {
                        // try the next form
                    }
                }
                int dot = binary.lastIndexOf('.');
                if (dot < 0) {
                    break;
                }
                binary = binary.substring(0, dot) + "$" + binary.substring(dot + 1);
            }
        }
        return null;
    }

    /**
     * {@code com.acme.Outer.Inner} as the JVM names it, {@code com.acme.Outer$Inner}: the segments after the first
     * class name (the first starting with a capital) are nested classes.
     */
    static String binaryName(String name) {
        String[] parts = name.split("\\.");
        StringBuilder sb = new StringBuilder();
        boolean inClass = false;
        for (String part : parts) {
            if (!sb.isEmpty()) {
                sb.append(inClass ? '$' : '.');
            }
            sb.append(part);
            inClass |= !part.isEmpty() && Character.isUpperCase(part.charAt(0));
        }
        return sb.toString();
    }

    /** The fully qualified name of a class of the project, from the imports or the package of the source. */
    private String qualified(String name) {
        if (name.contains(".") && Character.isLowerCase(name.charAt(0))) {
            return name;
        }
        String first = name.contains(".") ? name.substring(0, name.indexOf('.')) : name;
        String imported = source.imports().get(first);
        if (imported != null) {
            return imported + name.substring(first.length());
        }
        return source.packageName() != null ? source.packageName() + "." + name : name;
    }

    // ---- calls ----

    private Object chain(Chain c) {
        Object target;
        Class<?> staticType = null;
        if (("String".equals(c.qualifier()) || "java.lang.String".equals(c.qualifier())) && c.calls().size() == 1
                && c.calls().get(0).name().equals("format") && !c.calls().get(0).args().isEmpty()) {
            // String.format("netty:tcp://localhost:%d", PORT)
            String text = formatText(c.calls().get(0).args());
            return text != null ? text : new Unknown(c, "a format the parser cannot work out");
        }
        if (c.qualifier() != null && c.calls().size() == 1
                && COLLECTIONS.contains(c.qualifier() + "." + c.calls().get(0).name())) {
            // setHeaders(Map.of("foo", constant("ABC"))): a collection of values the parser works out
            return collection(c);
        }
        if (c.qualifier() != null && c.qualifier().equals(builderParameter)) {
            // rb.simple(...) in a builder lambda: the builder
            target = builder;
        } else if (c.qualifier() != null) {
            Class<?> type = loadable(c.qualifier());
            if (type != null && STATIC_DSL.contains(type)) {
                staticType = type;
                target = null;
            } else {
                Object q = reference(new Ref(c.qualifier(), c.line()));
                if (q instanceof Unknown || q == null) {
                    return new Unknown(c, "a call on something the parser cannot see");
                }
                target = q;
            }
        } else {
            target = builder;
        }
        for (int i = 0; i < c.calls().size(); i++) {
            Call call = formatted(c.calls().get(i));
            Object result;
            if (staticType != null) {
                result = invokeStatic(List.of(staticType), call);
                staticType = null;
            } else {
                result = invoke(target, call);
                if (target == builder && result instanceof Unknown && i == 0) {
                    if (endpointDsl != null && call.name().equals("endpoints")) {
                        // endpoints(mock("a"), direct("b")) for a routing slip: the URIs, comma separated
                        result = endpoints(call);
                    } else {
                        // kafka("orders").brokers("b:9092"): the whole chain is one endpoint; as in Java, the
                        // builder's own methods (the endpoint DSL) come before statically imported ones
                        String uri = endpointDsl != null ? endpoint(c) : null;
                        if (uri != null) {
                            return uri;
                        }
                        // a statically imported DSL method: language("groovy", "..."), and(...)
                        Object viaStatic = invokeStatic(unqualifiedStaticDsl(), call);
                        if (!(viaStatic instanceof Unknown)) {
                            result = viaStatic;
                        }
                    }
                }
            }
            if (result instanceof Unknown u) {
                // the rest of the chain has nothing to be called on
                return u;
            }
            if (result == null) {
                // a void method: the rest of the chain, if any, is on the builder again (it rarely is)
                result = builder;
            }
            target = result;
        }
        return target;
    }

    /** Factories of JDK collections a route passes values in: only data, nothing of the project runs. */
    private static final Set<String> COLLECTIONS = Set.of(
            "Map.of", "java.util.Map.of", "List.of", "java.util.List.of", "Set.of", "java.util.Set.of",
            "Arrays.asList", "java.util.Arrays.asList", "Collections.singletonList", "java.util.Collections.singletonList");

    private Object collection(Chain c) {
        Call call = c.calls().get(0);
        List<Object> values = new ArrayList<>();
        for (Node arg : call.args()) {
            Object v = evaluate(arg);
            if (v instanceof Unknown || v == null) {
                return new Unknown(c, "a collection of values the parser cannot work out");
            }
            values.add(v);
        }
        String qualifier = c.qualifier().substring(c.qualifier().lastIndexOf('.') + 1);
        if (qualifier.equals("Map")) {
            if (values.size() % 2 != 0) {
                return new Unknown(c, "a collection of values the parser cannot work out");
            }
            // in the order of the source, which Map.of does not keep but the model and a dump should
            Map<Object, Object> map = new LinkedHashMap<>();
            for (int i = 0; i < values.size(); i += 2) {
                map.put(values.get(i), values.get(i + 1));
            }
            return map;
        }
        return qualifier.equals("Set") ? new LinkedHashSet<>(values) : values;
    }

    /** Why a statement that sets up the CamelContext (components, beans, properties) is not read: it is not a route. */
    static final String CONFIGURES_THE_CONTEXT = "configures the CamelContext, not a route";

    /**
     * Whether a statement starts on the CamelContext rather than the DSL: getContext()..., context.addComponent(...).
     */
    private boolean configuresTheContext(Chain chain) {
        if (chain.qualifier() != null) {
            String q = chain.qualifier();
            return q.equals("context") || q.equals("camelContext") || q.startsWith("context.")
                    || q.startsWith("camelContext.");
        }
        String first = chain.calls().isEmpty() ? "" : chain.calls().get(0).name();
        return first.equals("getContext") || first.equals("getCamelContext");
    }

    /** The format variants of the DSL, and the call they format for. */
    private static final Map<String, String> FORMATTED = Map.of("fromF", "from", "toF", "to", "simpleF", "simple");

    private static final Pattern FORMAT_SPECIFIER = Pattern.compile("%(\\d+\\$)?[-#+ 0,(]*\\d*(\\.\\d+)?[a-zA-Z%]");

    /**
     * {@code toF("seda:%s?size=%d", QUEUE, 10)} as {@code to("seda:orders?size=10")}: formatted as the DSL does when
     * every argument is known; else each unknown one is a marked placeholder in the text (reported).
     */
    private Call formatted(Call call) {
        String plain = FORMATTED.get(call.name());
        if (plain == null || call.args().isEmpty()) {
            return call;
        }
        String text = formatText(call.args());
        return text != null ? new Call(plain, List.of(new Str(text, call.line())), call.line()) : call;
    }

    /**
     * The text of a format and its arguments, as {@code String.format} gives it when every argument is known; else with
     * each unknown one as a marked placeholder (reported). Null when the format is not a known String, or an argument
     * is a class (simpleF(format, resultType, ...)).
     */
    private String formatText(List<Node> args) {
        Object format = evaluate(args.get(0));
        if (!(format instanceof String fmt)) {
            return null;
        }
        List<Node> argNodes = args.subList(1, args.size());
        List<Object> values = new ArrayList<>();
        boolean known = true;
        for (Node arg : argNodes) {
            Object v = evaluate(arg);
            if (v instanceof Class<?>) {
                return null;
            }
            known &= !(v instanceof Unknown);
            values.add(v);
        }
        if (known) {
            try {
                return String.format(fmt, values.toArray());
            } catch (IllegalFormatException e) {
                // fill in what is known below
            }
        }
        StringBuilder sb = new StringBuilder();
        Matcher m = FORMAT_SPECIFIER.matcher(fmt);
        int next = 0;
        while (m.find()) {
            String spec = m.group();
            String replacement;
            if (spec.equals("%%")) {
                replacement = "%";
            } else if (spec.equals("%n")) {
                replacement = System.lineSeparator();
            } else {
                // %2$s names its argument, the others take the next one
                Matcher indexed = Pattern.compile("^%(\\d+)\\$").matcher(spec);
                int index = indexed.find() ? Integer.parseInt(indexed.group(1)) - 1 : next++;
                if (index >= 0 && index < values.size()) {
                    Object v = values.get(index);
                    Node node = argNodes.get(index);
                    if (v instanceof Unknown u) {
                        report(node, u.reason());
                        replacement = LwJavaParser.UNRESOLVED_PREFIX + text(node) + "}";
                    } else {
                        replacement = String.valueOf(v);
                    }
                } else {
                    replacement = spec;
                }
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** {@code endpoints(...)} of the endpoint DSL as a constant of their URIs, comma separated. */
    private Object endpoints(Call call) {
        List<String> uris = new ArrayList<>();
        for (Node arg : call.args()) {
            String uri = arg instanceof Chain chain ? endpoint(chain) : null;
            if (uri == null) {
                Object v = evaluate(arg);
                if (!(v instanceof String s)) {
                    return new Unknown(call, "an endpoint the parser cannot work out");
                }
                uri = s;
            }
            uris.add(uri);
        }
        return builder.constant(String.join(",", uris));
    }

    /**
     * The classes whose static DSL a call without a qualifier may be: Builder and PredicateBuilder always (the Java the
     * dumper writes calls language(...) so), AggregationStrategies only when the source imports it statically.
     */
    private List<Class<?>> unqualifiedStaticDsl() {
        String aggregation = AggregationStrategies.class.getName();
        boolean imported = source.staticWildcards().contains(aggregation)
                || source.staticImports().containsValue(aggregation);
        return imported ? STATIC_DSL : List.of(Builder.class, PredicateBuilder.class);
    }

    /** The URI of an endpoint DSL chain, or null when the resolver does not know its factory. */
    private String endpoint(Chain c) {
        Call factory = c.calls().get(0);
        if (factory.args().size() > 2) {
            return null;
        }
        List<Object> paths = new ArrayList<>();
        for (Node arg : factory.args()) {
            Object v = evaluate(arg);
            if (!(v instanceof String) && !(v instanceof Unknown)) {
                // a factory of the endpoint DSL takes the path as text: split(stax(Record.class)) is StAXBuilder
                return null;
            }
            paths.add(v);
        }
        List<String> args = new ArrayList<>();
        for (int i = 0; i < paths.size(); i++) {
            args.add(uriValue(factory.args().get(i), paths.get(i)));
        }
        List<EndpointDslResolver.Option> options = new ArrayList<>();
        for (int i = 1; i < c.calls().size(); i++) {
            Call call = c.calls().get(i);
            List<String> values = new ArrayList<>();
            for (Node arg : call.args()) {
                values.add(uriValue(arg));
            }
            options.add(new EndpointDslResolver.Option(call.name(), values));
        }
        EndpointDslResolver.Endpoint endpoint = endpointDsl.endpoint(factory.name(), args, options);
        if (endpoint == null) {
            return null;
        }
        for (String problem : endpoint.problems()) {
            report(c, problem);
        }
        return endpoint.uri();
    }

    /** A value in an endpoint URI as the endpoint DSL writes it; a marked placeholder when unknown (reported). */
    private String uriValue(Node node) {
        return uriValue(node, evaluate(node));
    }

    private String uriValue(Node node, Object v) {
        if (v instanceof Unknown u) {
            report(node, u.reason());
            return LwJavaParser.UNRESOLVED_PREFIX + text(node) + "}";
        }
        if (v instanceof Enum<?> e) {
            return e.name();
        }
        if (v instanceof Class<?> type) {
            return type.getName();
        }
        return String.valueOf(v);
    }

    private Object invokeStatic(List<Class<?>> types, Call call) {
        List<Method> candidates = new ArrayList<>();
        for (Class<?> type : types) {
            for (Method m : type.getMethods()) {
                if (m.getName().equals(call.name()) && Modifier.isStatic(m.getModifiers())
                        && (m.getParameterCount() == call.args().size()
                                || m.isVarArgs() && call.args().size() >= m.getParameterCount() - 1)) {
                    candidates.add(m);
                }
            }
        }
        if (candidates.isEmpty()) {
            return new Unknown(call, "not a DSL method (a helper method, or the endpoint DSL)");
        }
        return call(null, candidates, call);
    }

    private Object invoke(Object target, Call call) {
        Object value = valueCall(target, call);
        if (value != null) {
            return value;
        }
        List<Method> candidates = candidates(target.getClass(), call.name(), call.args().size());
        if (candidates.isEmpty()) {
            String reason = target == builder
                    ? "not a DSL method (a helper method, or the endpoint DSL)"
                    : "no such DSL method on " + target.getClass().getSimpleName();
            return new Unknown(call, reason);
        }
        return call(target, candidates, call);
    }

    /**
     * A call on a JDK value that only computes a value: {@code TimeUnit.MILLISECONDS.toString()},
     * {@code TimeUnit.SECONDS.toMillis(5)}; null for anything else.
     */
    private Object valueCall(Object target, Call call) {
        if (target instanceof Enum<?> e && call.args().isEmpty()
                && (call.name().equals("name") || call.name().equals("toString"))) {
            return e.name();
        }
        if (target instanceof TimeUnit unit && call.args().size() == 1 && call.name().startsWith("to")
                && evaluate(call.args().get(0)) instanceof Number n) {
            return switch (call.name()) {
                case "toNanos" -> unit.toNanos(n.longValue());
                case "toMicros" -> unit.toMicros(n.longValue());
                case "toMillis" -> unit.toMillis(n.longValue());
                case "toSeconds" -> unit.toSeconds(n.longValue());
                case "toMinutes" -> unit.toMinutes(n.longValue());
                case "toHours" -> unit.toHours(n.longValue());
                case "toDays" -> unit.toDays(n.longValue());
                default -> null;
            };
        }
        return null;
    }

    /** Calls the overload the arguments fit best; a static one when {@code target} is null. */
    private Object call(Object target, List<Method> candidates, Call call) {
        Object[] values = new Object[call.args().size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = evaluate(call.args().get(i));
        }
        Method best = null;
        Object[] bestArgs = null;
        int bestCost = Integer.MAX_VALUE;
        int bestSpecific = -1;
        List<JavaParseResult.Unresolved> bestReports = null;
        // in a stable order, as reflection gives methods in none
        List<Method> ordered = new ArrayList<>(candidates);
        ordered.sort((a, b) -> signature(a).compareTo(signature(b)));
        for (Method m : ordered) {
            List<JavaParseResult.Unresolved> reports = new ArrayList<>();
            int[] cost = { 0 };
            Object[] args = convertAll(m, values, call.args(), reports, cost);
            if (args == null) {
                continue;
            }
            // as Java does, of the overloads that fit the most specific one: method(String, String) before
            // method(Object, String) for method("waiter", "checkOrder")
            int specific = specificity(m, values);
            if (cost[0] < bestCost || cost[0] == bestCost && specific > bestSpecific) {
                best = m;
                bestArgs = args;
                bestCost = cost[0];
                bestSpecific = specific;
                bestReports = reports;
            }
        }
        if (best == null) {
            return new Unknown(call, "arguments the DSL method does not take");
        }
        unresolved.addAll(bestReports);
        try {
            if (!Modifier.isPublic(best.getModifiers())) {
                // a protected method of the route builder, as a RouteBuilder subclass calls it; this needs Camel's
                // builder package open to camel-java-io, as it is on the class path (see design/java-dsl-parser.adoc)
                best.setAccessible(true);
            }
            Object result = best.invoke(target, bestArgs);
            if (result instanceof LineNumberAware la && la.getLineNumber() < 0) {
                la.setLineNumber(call.line());
            }
            if (result instanceof RouteDefinition route && route.getInput() != null
                    && route.getInput().getLineNumber() < 0) {
                route.getInput().setLineNumber(call.line());
            }
            if (target instanceof ProcessorDefinition<?> step) {
                // to(...) returns the route, not the step it adds: the step is the one without a line
                lineOfNewSteps(step, call.line());
            }
            if (result instanceof SwitchDefinition sw) {
                // doCase(value, uri), doCase(value).to(uri) and otherwise(uri) add to the switch, not its outputs
                lineOfNewCases(sw, call.line());
            }
            return result;
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            String message = String.valueOf(cause.getMessage());
            if (standsIn(values)) {
                // the DSL checks or uses right away what stands in for a value: configuration(builder) calls
                // builder.build(), setHeaders(headerMap) needs the map; the source is not wrong
                return new Unknown(call, "needs a value the parser cannot see");
            }
            if (cause instanceof NullPointerException && message.contains("CamelContext")) {
                // the replay builder never has a context: whatever needs one is not done in a parse
                return new Unknown(call, "needs a running CamelContext, which a parse does not have");
            }
            return new Unknown(call, "the DSL refused it: " + message);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return new Unknown(call, "the DSL refused it: " + e.getMessage());
        }
    }

    /**
     * Gives the line of a call to the steps it added. A DSL call appends to the step it is called on, or to the block
     * open in it (the when of a choice, the body of a split), and an open block is always the last output; so the steps
     * without a line on the path of last outputs are the new ones.
     */
    private static void lineOfNewSteps(ProcessorDefinition<?> step, int line) {
        ProcessorDefinition<?> current = step;
        for (int depth = 0; depth < JavaChainParser.MAX_DEPTH && current != null; depth++) {
            if (current instanceof ChoiceDefinition choice) {
                // the outputs of a choice are those of its open branch; the branches themselves are apart
                for (WhenDefinition when : choice.getWhenClauses()) {
                    if (when.getLineNumber() < 0) {
                        when.setLineNumber(line);
                    }
                }
                if (choice.getOtherwise() != null && choice.getOtherwise().getLineNumber() < 0) {
                    choice.getOtherwise().setLineNumber(line);
                }
            }
            List<ProcessorDefinition<?>> outputs = current.getOutputs();
            if (outputs == null || outputs.isEmpty()) {
                return;
            }
            ProcessorDefinition<?> last = outputs.get(outputs.size() - 1);
            if (last.getLineNumber() < 0) {
                last.setLineNumber(line);
            }
            current = last;
        }
    }

    /** Gives the line of a call to the switch cases and fallback it added. */
    private static void lineOfNewCases(SwitchDefinition sw, int line) {
        for (SwitchCaseDefinition c : sw.getCases()) {
            if (c.getLineNumber() < 0) {
                c.setLineNumber(line);
            }
        }
        if (sw.getOtherwiseDefinition() != null && sw.getOtherwiseDefinition().getLineNumber() < 0) {
            sw.getOtherwiseDefinition().setLineNumber(line);
        }
    }

    /** Whether something stands in for a value the parser does not know, in the arguments of a call. */
    private static boolean standsIn(Object[] values) {
        for (Object v : values) {
            if (v instanceof Unknown) {
                return true;
            }
        }
        return false;
    }

    /** How well a method's parameter types match the values: exactly the value's type counts most, Object least. */
    private static int specificity(Method m, Object[] values) {
        Class<?>[] types = m.getParameterTypes();
        int score = 0;
        for (int i = 0; i < Math.min(types.length, values.length); i++) {
            Class<?> t = boxed(types[i]);
            Object v = values[i];
            if (v == null || v instanceof Unknown || t == Object.class) {
                continue;
            }
            score += t == v.getClass() ? 2 : 1;
        }
        return score;
    }

    /** The DSL methods of that name and arity; the builder's protected ones count, as a RouteBuilder sees them. */
    private List<Method> candidates(Class<?> type, String name, int arity) {
        List<Method> answer = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals(name) || m.isBridge() || m.isSynthetic() || !isAllowed(m)
                        || !Modifier.isPublic(m.getModifiers()) && !(Modifier.isProtected(m.getModifiers())
                                && c.isAssignableFrom(ReplayBuilder.class))) {
                    continue;
                }
                int n = m.getParameterCount();
                if ((n == arity || m.isVarArgs() && arity >= n - 1) && seen.add(signature(m))) {
                    answer.add(m);
                }
            }
        }
        // public methods of interfaces (default methods of the DSL)
        for (Method m : type.getMethods()) {
            if (m.getName().equals(name) && isAllowed(m) && !m.isBridge()
                    && (m.getParameterCount() == arity || m.isVarArgs() && arity >= m.getParameterCount() - 1)
                    && seen.add(signature(m))) {
                answer.add(m);
            }
        }
        return answer;
    }

    private static String signature(Method m) {
        StringBuilder sb = new StringBuilder(m.getName());
        for (Class<?> p : m.getParameterTypes()) {
            sb.append(',').append(p.getName());
        }
        return sb.toString();
    }

    /**
     * The methods a source can make the replay builder and the static DSL call, one signature a line: pinned by a test,
     * so a DSL method added to Camel is looked at before a parse can call it.
     */
    static SortedSet<String> builderSurface() {
        SortedSet<String> answer = new TreeSet<>();
        for (Class<?> c = ReplayBuilder.class; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                boolean visible = Modifier.isPublic(m.getModifiers()) || Modifier.isProtected(m.getModifiers());
                if (visible && !m.isBridge() && !m.isSynthetic() && isAllowed(m)) {
                    answer.add(c.getSimpleName() + "." + signature(m));
                }
            }
        }
        for (Class<?> type : STATIC_DSL) {
            for (Method m : type.getMethods()) {
                if (Modifier.isStatic(m.getModifiers())) {
                    answer.add(type.getSimpleName() + "." + signature(m));
                }
            }
        }
        return answer;
    }

    /**
     * Only Camel's DSL: methods declared by Camel's model and builder types (and the API types they return), never ones
     * that take or return a CamelContext, reach into the registry or properties, or run the builder's life cycle
     * (populate, configure, prepare, update the context).
     */
    static boolean isAllowed(Method m) {
        String owner = m.getDeclaringClass().getName();
        boolean builder = owner.startsWith("org.apache.camel.builder.") || owner.equals(ReplayBuilder.class.getName());
        boolean camel = builder || owner.startsWith("org.apache.camel.model.")
                || owner.startsWith("org.apache.camel.support.builder.");
        if (!camel || Modifier.isStatic(m.getModifiers()) || DENIED_METHODS.contains(m.getName())
                || CamelContext.class.isAssignableFrom(m.getReturnType())) {
            return false;
        }
        for (Class<?> p : m.getParameterTypes()) {
            if (CamelContext.class.isAssignableFrom(p)) {
                return false;
            }
        }
        if (builder) {
            for (String prefix : BUILDER_LIFE_CYCLE) {
                if (m.getName().startsWith(prefix)) {
                    return false;
                }
            }
        }
        return true;
    }

    // ---- arguments ----

    /** The arguments for a method, or null when they do not fit; {@code cost} counts the stand-ins used. */
    private Object[] convertAll(
            Executable m, Object[] values, List<Node> nodes, List<JavaParseResult.Unresolved> reports, int[] cost) {
        Class<?>[] types = m.getParameterTypes();
        Type[] generics = m.getGenericParameterTypes();
        Object[] args = new Object[types.length];
        int fixed = m.isVarArgs() ? types.length - 1 : types.length;
        if (!m.isVarArgs() && values.length != types.length) {
            return null;
        }
        for (int i = 0; i < fixed; i++) {
            Object v = convert(values[i], types[i], generics[i], nodes.get(i), reports, cost);
            if (v == NO) {
                return null;
            }
            args[i] = v;
        }
        if (m.isVarArgs()) {
            Class<?> component = types[fixed].getComponentType();
            Type componentGeneric = generics[fixed] instanceof GenericArrayType g ? g.getGenericComponentType() : component;
            // one array argument passed as is
            if (values.length == types.length && values[fixed] != null && types[fixed].isInstance(values[fixed])) {
                args[fixed] = values[fixed];
                return args;
            }
            Object array = Array.newInstance(component, values.length - fixed);
            for (int i = fixed; i < values.length; i++) {
                Object v = convert(values[i], component, componentGeneric, nodes.get(i), reports, cost);
                if (v == NO) {
                    return null;
                }
                Array.set(array, i - fixed, v);
            }
            args[fixed] = array;
            // a varargs call costs a little, so an exact overload wins
            cost[0]++;
        }
        return args;
    }

    private static final Object NO = new Object();

    private Object convert(
            Object value, Class<?> type, Type generic, Node node, List<JavaParseResult.Unresolved> reports, int[] cost) {
        Class<?> boxed = boxed(type);
        if (value instanceof Unknown u) {
            return standIn(u, type, generic, reports, cost);
        }
        if (value == null) {
            return type.isPrimitive() ? NO : null;
        }
        if (boxed.isInstance(value)) {
            return value;
        }
        if (value instanceof Number n) {
            if (boxed == Long.class && value instanceof Integer) {
                return n.longValue();
            }
            if (boxed == Double.class) {
                return n.doubleValue();
            }
            if (boxed == Float.class) {
                return n.floatValue();
            }
            if (boxed == Object.class) {
                return value;
            }
        }
        if (type.isEnum() && node instanceof Ref r) {
            String constant = r.name().substring(r.name().lastIndexOf('.') + 1);
            for (Object e : type.getEnumConstants()) {
                if (((Enum<?>) e).name().equals(constant)) {
                    return e;
                }
            }
        }
        return NO;
    }

    /**
     * What stands in for a value the replay cannot work out: an empty stub for a class of the project, a placeholder
     * object for an interface, a marked string; each reported. NO when the parameter takes none of those.
     */
    private Object standIn(Unknown u, Class<?> type, Type generic, List<JavaParseResult.Unresolved> reports, int[] cost) {
        Node node = u.node();
        if (type.isEnum() && node instanceof Ref r) {
            String constant = r.name().substring(r.name().lastIndexOf('.') + 1);
            for (Object e : type.getEnumConstants()) {
                if (((Enum<?>) e).name().equals(constant)) {
                    return e;
                }
            }
        }
        if (type == Class.class && node instanceof ClassLit c) {
            Class<?> stub = stubs.stub(binaryName(qualified(c.type())), bound(generic));
            if (stub != null) {
                // a class named in the route is as good as known: the model keeps its name
                return stub;
            }
            cost[0] += 10;
            reports.add(new JavaParseResult.Unresolved(node.line(), c.type() + ".class", "a class name not usable"));
            return Object.class;
        }
        String text = text(node);
        if (type == String.class || type == Object.class) {
            cost[0] += 10;
            reports.add(new JavaParseResult.Unresolved(node.line(), text, u.reason()));
            return LwJavaParser.UNRESOLVED_PREFIX + text + "}";
        }
        if (type.isInterface()) {
            // a placeholder object says less than the marked text: to(helper()) keeps the text in to(String); when
            // the class it stands for is named for the type (new CafeAggregationStrategy()), that overload fits best
            boolean named = node instanceof New n && n.type().endsWith(type.getSimpleName());
            cost[0] += named ? 15 : 20;
            reports.add(new JavaParseResult.Unresolved(node.line(), text, u.reason()));
            return Proxy.newProxyInstance(stubs, new Class<?>[] { type }, (proxy, method, args) -> switch (method.getName()) {
                case "toString" -> LwJavaParser.UNRESOLVED_PREFIX + text + "}";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            });
        }
        if (!type.isPrimitive() && !type.isEnum() && type != Class.class) {
            // an object of a class, such as xpath("/c:n", ns) with Namespaces ns built in the method: null stands
            // in, as the parser creates no objects of a route
            cost[0] += 30;
            reports.add(new JavaParseResult.Unresolved(node.line(), text, u.reason()));
            return null;
        }
        return NO;
    }

    /** The upper bound of {@code Class<? extends X>}, or null. */
    private static Class<?> bound(Type generic) {
        if (generic instanceof ParameterizedType p && p.getActualTypeArguments().length == 1) {
            Type arg = p.getActualTypeArguments()[0];
            if (arg instanceof WildcardType w && w.getUpperBounds().length == 1
                    && w.getUpperBounds()[0] instanceof Class<?> c) {
                return c;
            }
            if (arg instanceof Class<?> c) {
                return c;
            }
        }
        return null;
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return Array.get(Array.newInstance(type, 1), 0);
    }

    private static Class<?> boxed(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        return switch (type.getName()) {
            case "int" -> Integer.class;
            case "long" -> Long.class;
            case "boolean" -> Boolean.class;
            case "double" -> Double.class;
            case "float" -> Float.class;
            case "short" -> Short.class;
            case "byte" -> Byte.class;
            case "char" -> Character.class;
            default -> Void.class;
        };
    }

    private void report(Node node, String reason) {
        unresolved.add(new JavaParseResult.Unresolved(node.line(), text(node), reason));
    }

    /** The source text of a node, for the reports. */
    static String text(Node node) {
        if (node instanceof Str s) {
            return "\"" + s.value() + "\"";
        } else if (node instanceof Chr c) {
            return "'" + c.value() + "'";
        } else if (node instanceof Num n) {
            return n.text();
        } else if (node instanceof Bool b) {
            return String.valueOf(b.value());
        } else if (node instanceof Null) {
            return "null";
        } else if (node instanceof Concat c) {
            return String.join(" + ", c.parts().stream().map(ChainReplayer::text).toList());
        } else if (node instanceof Ref r) {
            return r.name();
        } else if (node instanceof ClassLit c) {
            return c.type() + ".class";
        } else if (node instanceof ClassName c) {
            return c.type() + ".class." + c.method() + "()";
        } else if (node instanceof BinOp b) {
            return text(b.left()) + " " + b.op() + " " + text(b.right());
        } else if (node instanceof Chain c) {
            return (c.qualifier() != null ? c.qualifier() + "." : "")
                   + String.join(".", c.calls().stream().map(ChainReplayer::text).toList());
        } else if (node instanceof Call c) {
            return c.name() + "(" + String.join(", ", c.args().stream().map(ChainReplayer::text).toList()) + ")";
        } else if (node instanceof New n) {
            return n.text();
        } else if (node instanceof Lambda l) {
            return l.text();
        } else if (node instanceof Opaque o) {
            return o.text();
        }
        return String.valueOf(node);
    }
}
