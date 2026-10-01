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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.model.ChoiceDefinition;
import org.apache.camel.model.CircuitBreakerDefinition;
import org.apache.camel.model.OutputNode;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.TryDefinition;
import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.EipModel;
import org.apache.camel.tooling.model.LanguageModel;

/**
 * The Tab completions of the Java DSL route chain in the Source editor (CAMEL-25241): after a dot, the methods that
 * compile there. The chain is walked from its start (from, onException...) over the model classes with reflection, with
 * a stack of the open blocks as the DSL has at runtime: a block EIP (split, filter, choice, doTry...) opens one, end()
 * closes it, endChoice() and endDoTry() go back to the choice or try, a builder clause (.split().simple(..),
 * .marshal().json()) returns to its EIP. An overload the text cannot tell apart (recipientList(header("x")) or
 * recipientList(",")) is kept both ways until a following call rules one out. The options of the EIP the chain is on
 * come first, then the EIPs; the documentation comes from the catalog. This is light assistance for hand-written edits,
 * not a Java compiler: a chain it cannot follow gets no completion.
 */
final class JavaDslCompletions {

    /** Names that are no DSL, although public on the model classes. */
    private static final Set<String> NOT_DSL = Set.of(
            "copyDefinition", "toString", "equals", "hashCode", "getClass", "clone", "wait", "notify", "notifyAll",
            "createChildProcessor", "preCreateProcessor", "configureChild", "addOutput", "clearOutput");

    /** The methods that close blocks, with what they do: they have no EIP in the catalog. */
    private static final Map<String, String> END_DOCS = Map.of(
            "end", "Ends the current block (split, filter, choice, doTry...), going back to the one around it",
            "endParent", "Ends the current block and goes back to the parent of the block",
            "endChoice", "Ends the current when or otherwise, going back to the choice to add another when or otherwise",
            "endDoTry", "Ends the current doCatch or doFinally, going back to the doTry",
            "endDoCatch", "Ends the current block, going back to the doCatch",
            "endCircuitBreaker", "Ends the current onFallback, going back to the circuitBreaker");

    /** The most states kept for overloads the text cannot tell apart. */
    private static final int MAX_STATES = 8;

    // the DSL methods of a class by name, without and with the deprecated ones
    private static final Map<Class<?>, Map<String, List<Method>>> METHODS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<String, List<Method>>> ALL_METHODS = new ConcurrentHashMap<>();
    // whether a model class is a configuration ended by end(), as a clause
    private static final Map<Class<?>, Boolean> CLAUSES = new ConcurrentHashMap<>();
    // the EIP name of a definition class, by the java type the catalog has for it
    private static final Map<String, String> EIP_BY_TYPE = new ConcurrentHashMap<>();

    private JavaDslCompletions() {
    }

    /**
     * Where the chain is after its calls.
     *
     * @param blocks  the open blocks, the outermost (the route) first
     * @param current the definition the next call is on: the innermost block, or the EIP just added that has options
     * @param clause  the builder clause the chain is on (.split().|), whose methods return to {@code after}; else null
     * @param after   the definition a clause returns to
     */
    record State(List<Class<?>> blocks, Class<?> current, Class<?> clause, Class<?> after) {

        Class<?> top() {
            return blocks.get(blocks.size() - 1);
        }

        State on(Class<?> definition) {
            return new State(blocks, definition, null, null);
        }

        State push(Class<?> block) {
            List<Class<?>> b = new ArrayList<>(blocks);
            b.add(block);
            return new State(b, block, null, null);
        }

        State popTo(Class<?> block) {
            int at = blocks.lastIndexOf(block);
            if (at < 0) {
                return null;
            }
            List<Class<?>> b = new ArrayList<>(blocks.subList(0, at + 1));
            return new State(b, block, null, null);
        }

        State pop() {
            if (blocks.size() == 1) {
                return on(top());
            }
            List<Class<?>> b = new ArrayList<>(blocks.subList(0, blocks.size() - 1));
            return new State(b, b.get(b.size() - 1), null, null);
        }
    }

    /** The state after the calls of the chain, or null when the chain is not one this can follow. */
    static State resolve(List<JavaChainContext.Call> calls) {
        List<State> states = states(calls);
        return states.isEmpty() ? null : states.get(0);
    }

    /** The possible states after the calls of the chain (one unless overloads leave it open); empty when it is lost. */
    static List<State> states(List<JavaChainContext.Call> calls) {
        if (calls.isEmpty()) {
            return List.of();
        }
        List<State> states = new ArrayList<>();
        for (Method m : candidates(RouteBuilder.class, calls.get(0))) {
            Class<?> start = raw(m.getGenericReturnType());
            if (start != null && ProcessorDefinition.class.isAssignableFrom(start)) {
                states.add(new State(List.of(start), start, null, null));
            }
        }
        for (int i = 1; i < calls.size() && !states.isEmpty(); i++) {
            List<State> next = new ArrayList<>();
            for (State s : states) {
                for (State n : step(s, calls.get(i))) {
                    if (next.size() < MAX_STATES && !next.contains(n)) {
                        next.add(n);
                    }
                }
            }
            states = next;
        }
        return states;
    }

    /** The states one call leads to from a state: none when the call does not compile there. */
    private static List<State> step(State s, JavaChainContext.Call call) {
        String name = call.name();
        if (s.clause() != null) {
            List<State> found = new ArrayList<>();
            for (Method m : candidates(s.clause(), call)) {
                // an option of the clause (marshal().variableSend(..)) stays on it, the others return to the EIP
                State n = raw(m.getGenericReturnType()) == s.clause() ? s : s.on(s.after());
                if (!found.contains(n)) {
                    found.add(n);
                }
            }
            return found;
        }
        if (END_DOCS.containsKey(name)) {
            State n = switch (name) {
                case "endChoice" -> s.popTo(ChoiceDefinition.class);
                case "endDoTry", "endDoCatch" -> s.popTo(TryDefinition.class);
                case "endCircuitBreaker" -> s.popTo(CircuitBreakerDefinition.class);
                // after an EIP that is no block (recipientList...), end() ends that EIP
                default -> s.current() != s.top() ? s.on(s.top()) : s.pop();
            };
            return n != null ? List.of(n) : List.of();
        }
        List<State> found = new ArrayList<>();
        List<Method> methods = candidates(s.current(), call);
        State from = s;
        if (methods.isEmpty() && s.current() != s.top()) {
            // an EIP after one whose options were being set: it goes into the innermost block
            from = s.on(s.top());
            methods = candidates(from.current(), call);
        }
        for (Method m : methods) {
            State base = isOption(m) ? from : from.on(from.top());
            Type ret = m.getGenericReturnType();
            Class<?> raw = raw(ret);
            if (raw == null) {
                continue;
            }
            if (isClause(raw)) {
                Type arg = argument(ret);
                Class<?> of = raw(arg);
                State target = creates(m, arg, of, base) ? (isBlock(of) ? base.push(of) : base.on(of)) : base;
                found.add(new State(target.blocks(), target.current(), raw, target.current()));
            } else if (ProcessorDefinition.class.isAssignableFrom(raw)) {
                if (!creates(m, ret, raw, base)) {
                    // Type, or an option returning its definition: the chain stays where it is
                    found.add(base);
                } else if (isBlock(raw)) {
                    found.add(base.push(raw));
                } else {
                    found.add(base.on(raw));
                }
            }
        }
        return found;
    }

    /**
     * Whether the call adds a new definition of the returned class: an EIP returning a definition class (also of the
     * class the chain is on: a choice in a choice) does, an option returning its own definition and a method returning
     * Type (to, log...) do not.
     */
    private static boolean creates(Method m, Type returned, Class<?> raw, State base) {
        if (raw == null || returned instanceof TypeVariable<?> || raw == ProcessorDefinition.class
                || !ProcessorDefinition.class.isAssignableFrom(raw)) {
            return false;
        }
        return !isOption(m) || !raw.isAssignableFrom(base.current());
    }

    /** The completions after the calls of the chain; empty when the chain is not one this can follow. */
    static List<AutocompletePopup.CompletionItem> provide(CamelCatalog catalog, JavaChainContext context) {
        Map<String, AutocompletePopup.CompletionItem> own = new TreeMap<>();
        Map<String, AutocompletePopup.CompletionItem> ends = new TreeMap<>();
        Map<String, AutocompletePopup.CompletionItem> eips = new TreeMap<>();
        for (State state : states(context.calls())) {
            boolean clause = state.clause() != null;
            Map<String, BaseOptionModel> options = clause ? Map.of() : optionDocs(catalog, state.current());
            for (Map.Entry<String, List<Method>> e : methods(clause ? state.clause() : state.current(), false)
                    .entrySet()) {
                String name = e.getKey();
                List<Method> overloads = e.getValue();
                if (END_DOCS.containsKey(name)) {
                    if (clause) {
                        // the end() of a configuration (resilience4jConfiguration()...)
                        ends.putIfAbsent(name, item(name, "Ends the configuration, going back to the "
                                                          + state.after().getSimpleName(),
                                "end", overloads));
                    } else if (closes(state, name)) {
                        ends.putIfAbsent(name, item(name, END_DOCS.get(name), "end", overloads));
                    }
                } else if (clause) {
                    LanguageModel language = catalog != null ? catalog.languageModel(name) : null;
                    own.putIfAbsent(name, item(name, language != null ? language.getDescription() : null,
                            state.clause().getSimpleName(), overloads));
                } else if (isOption(overloads.get(0))) {
                    BaseOptionModel option = options.get(name);
                    own.putIfAbsent(name, item(name, option != null ? option.getDescription() : null, "option", overloads));
                } else {
                    EipModel eip = catalog != null ? catalog.eipModel(name) : null;
                    eips.putIfAbsent(name, item(name, eip != null ? eip.getDescription() : null, "EIP", overloads));
                }
            }
        }
        List<AutocompletePopup.CompletionItem> items = new ArrayList<>(own.values());
        items.addAll(ends.values());
        eips.keySet().removeAll(own.keySet());
        items.addAll(eips.values());
        return items;
    }

    /** Whether the end method closes something: end() always compiles, endChoice() only inside a choice... */
    private static boolean closes(State state, String name) {
        return switch (name) {
            case "endChoice" -> state.blocks().contains(ChoiceDefinition.class);
            case "endDoTry", "endDoCatch" -> state.blocks().contains(TryDefinition.class);
            case "endCircuitBreaker" -> state.blocks().contains(CircuitBreakerDefinition.class);
            default -> true;
        };
    }

    private static AutocompletePopup.CompletionItem item(String name, String doc, String kind, List<Method> overloads) {
        StringBuilder sb = new StringBuilder(doc != null ? doc : "");
        sb.append(sb.isEmpty() ? "" : "\n\n");
        overloads.stream().limit(6).forEach(m -> sb.append(signature(m)).append('\n'));
        if (overloads.size() > 6) {
            sb.append("... ").append(overloads.size() - 6).append(" more\n");
        }
        boolean args = overloads.stream().anyMatch(m -> m.getParameterCount() > 0);
        // the cursor goes inside the parentheses when the method can take something (split(|)), else after them
        String insert = name + (args ? "(" + XmlCompletions.CARET + ")" : "()" + XmlCompletions.CARET);
        return new AutocompletePopup.CompletionItem(
                name, sb.toString().strip(), kind, null, false, null, null, false, insert);
    }

    private static String signature(Method m) {
        StringBuilder sb = new StringBuilder(m.getName()).append('(');
        Class<?>[] types = m.getParameterTypes();
        for (int i = 0; i < types.length; i++) {
            sb.append(i > 0 ? ", " : "").append(types[i].getSimpleName());
        }
        return sb.append(')').toString();
    }

    /** The catalog options of the EIP of a definition class, by name, for the documentation of its methods. */
    private static Map<String, BaseOptionModel> optionDocs(CamelCatalog catalog, Class<?> definition) {
        Map<String, BaseOptionModel> found = new HashMap<>();
        if (catalog == null) {
            return found;
        }
        if (EIP_BY_TYPE.isEmpty()) {
            for (String name : catalog.findModelNames()) {
                EipModel m = catalog.eipModel(name);
                if (m != null && m.getJavaType() != null) {
                    EIP_BY_TYPE.putIfAbsent(m.getJavaType(), name);
                }
            }
        }
        String name = EIP_BY_TYPE.get(definition.getName());
        EipModel eip = name != null ? catalog.eipModel(name) : null;
        if (eip != null) {
            for (BaseOptionModel o : eip.getOptions()) {
                found.put(o.getName(), o);
            }
        }
        return found;
    }

    /** Whether the definition holds the EIPs that follow it, until its end(). */
    static boolean isBlock(Class<?> definition) {
        return OutputNode.class.isAssignableFrom(definition) || definition == ChoiceDefinition.class;
    }

    /**
     * Whether the type is a clause that returns to its EIP: a builder clause (ExpressionClause, DataFormatClause...),
     * or a configuration of the model ended by end() (circuitBreaker().resilience4jConfiguration()...end()).
     */
    private static boolean isClause(Class<?> type) {
        if (ProcessorDefinition.class.isAssignableFrom(type)) {
            return false;
        }
        if (type.getName().startsWith("org.apache.camel.builder.")) {
            return type.getTypeParameters().length == 1;
        }
        return type.getName().startsWith("org.apache.camel.model.") && CLAUSES.computeIfAbsent(type, t -> {
            for (Method m : t.getMethods()) {
                if ("end".equals(m.getName()) && m.getParameterCount() == 0
                        && ProcessorDefinition.class.isAssignableFrom(m.getReturnType())) {
                    return true;
                }
            }
            return false;
        });
    }

    /** Whether the method is an option of its definition (declared below ProcessorDefinition) rather than an EIP. */
    static boolean isOption(Method m) {
        Class<?> declaring = m.getDeclaringClass();
        if (ProcessorDefinition.class.isAssignableFrom(declaring)) {
            return declaring != ProcessorDefinition.class;
        }
        // id, description... of the identified definitions
        return declaring.getName().startsWith("org.apache.camel.model.");
    }

    /** The methods of the class the call can be: by name and number of arguments, deprecated ones included. */
    private static List<Method> candidates(Class<?> type, JavaChainContext.Call call) {
        List<Method> overloads = methods(type, true).get(call.name());
        if (overloads == null) {
            return List.of();
        }
        List<Method> found = new ArrayList<>();
        for (Method m : overloads) {
            if (m.getParameterCount() == call.arguments()
                    || m.isVarArgs() && call.arguments() >= m.getParameterCount() - 1) {
                found.add(m);
            }
        }
        return found;
    }

    /**
     * The DSL methods of a class by name: public, of the Camel model or builders, returning where the chain goes on.
     * The deprecated ones are followed in a chain but not offered.
     */
    static Map<String, List<Method>> methods(Class<?> type, boolean deprecated) {
        return (deprecated ? ALL_METHODS : METHODS).computeIfAbsent(type, t -> {
            Map<String, List<Method>> found = new LinkedHashMap<>();
            for (Method m : t.getMethods()) {
                if (isDsl(t, m) && (deprecated || !m.isAnnotationPresent(Deprecated.class))) {
                    found.computeIfAbsent(m.getName(), k -> new ArrayList<>()).add(m);
                }
            }
            return found;
        });
    }

    private static boolean isDsl(Class<?> type, Method m) {
        if (Modifier.isStatic(m.getModifiers()) || m.isBridge() || m.isSynthetic() || NOT_DSL.contains(m.getName())) {
            return false;
        }
        String pkg = m.getDeclaringClass().getName();
        if (!pkg.startsWith("org.apache.camel.model.") && !pkg.startsWith("org.apache.camel.builder.")) {
            return false;
        }
        if (m.getParameterCount() == 0 && (m.getName().startsWith("get") || m.getName().startsWith("is"))) {
            return false;
        }
        for (Class<?> p : m.getParameterTypes()) {
            if (CamelContext.class.isAssignableFrom(p)) {
                return false;
            }
        }
        Type ret = m.getGenericReturnType();
        if (ret instanceof TypeVariable<?>) {
            // Type of the definitions, or T of a clause: where the chain goes on
            return true;
        }
        Class<?> raw = raw(ret);
        if (isClause(type)) {
            // a clause ends with the methods that return to its EIP (T, or end() of a configuration); its options
            // return the clause itself
            return raw == type || raw != null && ProcessorDefinition.class.isAssignableFrom(raw);
        }
        return raw != null && (ProcessorDefinition.class.isAssignableFrom(raw) || isClause(raw));
    }

    private static Class<?> raw(Type type) {
        if (type instanceof Class<?> c) {
            return c;
        }
        if (type instanceof ParameterizedType p) {
            return raw(p.getRawType());
        }
        if (type instanceof TypeVariable<?> v) {
            Type[] bounds = v.getBounds();
            return bounds.length > 0 ? raw(bounds[0]) : Object.class;
        }
        return null;
    }

    private static Type argument(Type type) {
        if (type instanceof ParameterizedType p && p.getActualTypeArguments().length > 0) {
            return p.getActualTypeArguments()[0];
        }
        return null;
    }
}
