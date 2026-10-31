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
package org.apache.camel.cli.connector;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

import org.apache.camel.CamelContext;
import org.apache.camel.util.json.JsonObject;

/**
 * The {@code chaos} action: adds, changes and removes the rules of camel-chaos in the running integration, as
 * {@code camel cmd chaos-add}, {@code chaos-change}, {@code chaos-remove} and {@code chaos-clear} send it, for example
 * {@code {"action":"chaos","command":"add","rule":"endpoint=kafka:orders* error rate=0.3 until=1m"}}.
 * <p/>
 * It works only when chaos is switched on in the integration (profile {@code dev} or {@code camel.chaos.enabled=true},
 * never profile {@code prod}): only then has the context a {@code ChaosManager}, and the action never creates one. A
 * rule goes through the same parser as {@code camel.chaos.rules}; a rule that is not valid is reported as an error with
 * the reason, and an id that does not exist as not found. The chaos dev console only reads, so rules cannot be added
 * over HTTP.
 * <p/>
 * camel-cli-connector does not depend on camel-chaos: with {@code camel run}, camel-chaos comes with {@code --chaos} or
 * {@code --dep camel:chaos} in the class loader of the integration, which the connector cannot link against. So the
 * manager is looked up by its class name through the class resolver of the context, and its public methods are called
 * by reflection.
 */
final class ChaosAction {

    static final String CHAOS_MANAGER = "org.apache.camel.component.chaos.ChaosManager";

    private ChaosAction() {
    }

    static void dispatch(CamelContext camelContext, JsonObject root, CliActionOutput output) throws Exception {
        Class<?> type = camelContext.getClassResolver().resolveClass(CHAOS_MANAGER);
        if (type == null) {
            output.error("camel-chaos is not on the classpath of this integration: run it with --chaos, or add the"
                         + " camel-chaos dependency (camel run --dep camel:chaos)");
            return;
        }
        Object chaos = camelContext.getCamelContextExtension().getContextPlugin(type);
        if (chaos == null) {
            output.error("Chaos is off in this integration: it is switched on with profile dev (unless"
                         + " camel.chaos.enabled=false) or with camel.chaos.enabled=true, and never with profile prod");
            return;
        }
        String command = root.getString("command");
        String id = root.getString("id");
        String rule = root.getString("rule");
        JsonObject answer = new JsonObject();
        try {
            if ("add".equals(command)) {
                if (isBlank(rule)) {
                    output.error("A rule is required");
                    return;
                }
                answer.put("id", call(type, chaos, "addRule", rule));
                answer.put("version", 1);
            } else if ("change".equals(command)) {
                if (isBlank(id) || isBlank(rule)) {
                    output.error("The id of the rule and the new rule are required");
                    return;
                }
                if (!Boolean.TRUE.equals(call(type, chaos, "hasRule", id))) {
                    output.notFound("There is no chaos rule " + id);
                    return;
                }
                answer.put("id", id);
                answer.put("version", call(type, chaos, "changeRule", id, rule));
            } else if ("remove".equals(command)) {
                if (isBlank(id)) {
                    output.error("The id of the rule is required");
                    return;
                }
                if (!Boolean.TRUE.equals(call(type, chaos, "removeRule", id))) {
                    output.notFound("There is no chaos rule " + id);
                    return;
                }
                answer.put("id", id);
                answer.put("removed", 1);
            } else if ("clear".equals(command)) {
                answer.put("removed", call(type, chaos, "clearRules"));
            } else {
                output.error("Unknown chaos command: " + command + " (add, change, remove or clear)");
                return;
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            // a rule that is not valid, an id that does not exist, or too many rules: the reason goes to the tool
            output.error(e.getMessage());
            return;
        }
        output.write(answer);
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    /**
     * Calls a public method of the manager, and throws what it threw (such as the IllegalArgumentException of a rule
     * that is not valid).
     */
    private static Object call(Class<?> type, Object chaos, String name, String... args) throws Exception {
        Class<?>[] types = new Class<?>[args.length];
        Arrays.fill(types, String.class);
        Method method = type.getMethod(name, types);
        try {
            return method.invoke(chaos, (Object[]) args);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception cause) {
                throw cause;
            }
            if (e.getCause() instanceof LinkageError cause) {
                // such as a NoClassDefFoundError of an exception= class: the reason goes to the tool
                throw new IllegalStateException(cause.toString(), cause);
            }
            throw e;
        }
    }
}
