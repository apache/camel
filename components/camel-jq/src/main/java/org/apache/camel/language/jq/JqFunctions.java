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
package org.apache.camel.language.jq;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import net.thisptr.jackson.jq.BuiltinFunctionLoader;
import net.thisptr.jackson.jq.Expression;
import net.thisptr.jackson.jq.Function;
import net.thisptr.jackson.jq.PathOutput;
import net.thisptr.jackson.jq.Scope;
import net.thisptr.jackson.jq.Version;
import net.thisptr.jackson.jq.Versions;
import net.thisptr.jackson.jq.exception.JsonQueryException;
import net.thisptr.jackson.jq.internal.tree.FunctionCall;
import net.thisptr.jackson.jq.path.Path;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JqFunctions {

    public static final ThreadLocal<Exchange> EXCHANGE_LOCAL = new ThreadLocal<>();

    private static final Logger LOGGER = LoggerFactory.getLogger(JqFunctions.class);

    private JqFunctions() {
    }

    public static void load(CamelContext camelContext, Scope scope) {
        Map<String, Function> fromServiceLoader = BuiltinFunctionLoader.getInstance()
                .loadFunctionsFromServiceLoader(
                        camelContext.getApplicationContextClassLoader() != null
                                ? camelContext.getApplicationContextClassLoader()
                                : BuiltinFunctionLoader.class.getClassLoader(),
                        Versions.JQ_1_6);

        Map<String, Function> fromJq = BuiltinFunctionLoader.getInstance()
                .loadFunctionsFromJsonJq(
                        camelContext.getApplicationContextClassLoader() != null
                                ? camelContext.getApplicationContextClassLoader()
                                : BuiltinFunctionLoader.class.getClassLoader(),
                        Versions.JQ_1_6,
                        scope);

        Map<String, Function> fromRegistry = camelContext.getRegistry().findByTypeWithName(Function.class);

        if (fromServiceLoader != null) {
            LOGGER.debug("Loading {} jq functions from ServiceLoader", fromServiceLoader.size());
            fromServiceLoader.forEach(scope::addFunction);
        }

        if (fromJq != null) {
            LOGGER.debug("Loading {} jq functions from Json JQ", fromJq.size());
            fromJq.forEach(scope::addFunction);
        }

        if (fromRegistry != null) {
            LOGGER.debug("Loading {} jq functions from Registry", fromRegistry.size());
            fromRegistry.forEach(scope::addFunction);
        }
    }

    public static void loadLocal(Scope scope) {
        scope.addFunction(Header.NAME, 1, new Header());
        scope.addFunction(Header.NAME, 2, new Header());
        scope.addFunction(Property.NAME, 1, new Property());
        scope.addFunction(Property.NAME, 2, new Property());
        scope.addFunction(Constant.NAME, 1, new Constant());
        scope.addFunction(Constant.NAME, 2, new Constant());
    }

    public abstract static class ExchangeAwareFunction implements Function {

        @Override
        public void apply(Scope scope, List<Expression> args, JsonNode in, Path path, PathOutput output, Version version)
                throws JsonQueryException {

            Exchange exchange = EXCHANGE_LOCAL.get();

            if (exchange != null) {
                doApply(scope, args, in, path, output, version, exchange);
            }
        }

        protected abstract void doApply(
                Scope scope,
                List<Expression> args,
                JsonNode in,
                Path path,
                PathOutput output,
                Version version,
                Exchange exchange)
                throws JsonQueryException;
    }

    /**
     * A function that allow to retrieve an {@link org.apache.camel.Message} header value as part of JQ expression
     * evaluation.
     *
     * As example, the following JQ expression sets the {@code .name} property to the value of the header named
     * {@code CommitterName}.
     *
     * <pre>
     * {@code
     * .name = header(\"CommitterName\")"
     * }
     * </pre>
     *
     */
    public static class Header extends ExchangeAwareFunction {
        public static final String NAME = "header";

        @Override
        protected void doApply(
                Scope scope,
                List<Expression> args,
                JsonNode in,
                Path path,
                PathOutput output,
                Version version,
                Exchange exchange)
                throws JsonQueryException {

            args.get(0).apply(scope, in, name -> {
                if (args.size() == 2) {
                    args.get(1).apply(scope, in, defval -> {
                        extract(
                                exchange,
                                name.asText(),
                                defval.asText(),
                                output);
                    });
                } else {
                    extract(
                            exchange,
                            name.asText(),
                            null,
                            output);
                }
            });
        }

        private void extract(Exchange exchange, String headerName, String headerValue, PathOutput output)
                throws JsonQueryException {
            String header = exchange.getMessage().getHeader(headerName, headerValue, String.class);

            if (header == null) {
                output.emit(NullNode.getInstance(), null);
            } else {
                output.emit(new TextNode(header), null);
            }
        }
    }

    /**
     * A function that allow to retrieve an {@link org.apache.camel.Message} property value as part of JQ expression
     * evaluation.
     *
     * As example, the following JQ expression sets the {@code .name} property to the value of the header named
     * {@code CommitterName}.
     *
     * <pre>
     * {@code
     * .name = property(\"CommitterName\")"
     * }
     * </pre>
     *
     */
    public static class Property extends ExchangeAwareFunction {
        public static final String NAME = "property";

        @Override
        protected void doApply(
                Scope scope,
                List<Expression> args,
                JsonNode in,
                Path path,
                PathOutput output,
                Version version,
                Exchange exchange)
                throws JsonQueryException {

            args.get(0).apply(scope, in, name -> {
                if (args.size() == 2) {
                    args.get(1).apply(scope, in, defval -> {
                        extract(
                                exchange,
                                name.asText(),
                                defval.asText(),
                                output);
                    });
                } else {
                    extract(
                            exchange,
                            name.asText(),
                            null,
                            output);
                }
            });
        }

        private void extract(Exchange exchange, String propertyName, String propertyValue, PathOutput output)
                throws JsonQueryException {
            String header = exchange.getProperty(propertyName, propertyValue, String.class);

            if (header == null) {
                output.emit(NullNode.getInstance(), null);
            } else {
                output.emit(new TextNode(header), null);
            }
        }
    }

    /**
     * A function that returns a constant value as part of JQ expression evaluation.
     *
     * As example, the following JQ expression sets the {@code .name} property to the constant value Donald.
     *
     * <pre>
     * {@code
     * .name = constant(\"Donald\")"
     * }
     * </pre>
     *
     */
    public static class Constant implements Function {
        public static final String NAME = "constant";

        @Override
        public void apply(Scope scope, List<Expression> args, JsonNode in, Path path, PathOutput output, Version version)
                throws JsonQueryException {
            FunctionCall fc = (FunctionCall) args.get(0);
            String t = fc.toString();
            output.emit(new TextNode(t), null);
        }
    }
}
