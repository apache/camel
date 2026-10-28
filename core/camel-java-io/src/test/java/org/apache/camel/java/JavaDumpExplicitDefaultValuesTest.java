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
package org.apache.camel.java;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.errorhandler.DefaultErrorHandlerDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A route that sets an option to its documented default value, where "not set" means "the value of a parent level",
 * runs the same once its Java dump is compiled and loaded again.
 */
public class JavaDumpExplicitDefaultValuesTest {

    private static final String CLASS_NAME = "test.generated.DumpedRoutes";

    @Test
    public void onExceptionRedeliveryDelayUnderErrorHandler() throws Exception {
        // the onException redelivers after 1000 ms (the documented default), the error handler after 30 s: an option of
        // the onException that is not set takes the value of the error handler
        RouteBuilder routes = new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("redelivery")
                        .onException(IllegalStateException.class).maximumRedeliveries(1).redeliveryDelay(1000)
                        .handled(true).setBody(constant("handled")).end()
                        .throwException(IllegalStateException.class, "Forced");
            }
        };

        assertThat(run(routes)).isEqualTo("handled");

        String dumped;
        try (DefaultCamelContext context = createContext()) {
            context.addRoutes(routes);
            dumped = new LwModelToJavaDumper().dumpModelAsJava(context, context.getRouteDefinition("redelivery"));
        }
        assertThat(run(load(dumped))).as(dumped).isEqualTo("handled");
    }

    private static DefaultCamelContext createContext() {
        DefaultCamelContext context = new DefaultCamelContext();
        context.getCamelContextExtension().setErrorHandlerFactory(
                new DefaultErrorHandlerDefinition().maximumRedeliveries(3).redeliveryDelay(30000));
        context.getShutdownStrategy().setTimeout(1);
        return context;
    }

    private static String run(RoutesBuilder routes) throws Exception {
        try (DefaultCamelContext context = createContext()) {
            context.addRoutes(routes);
            context.start();
            return call(context);
        }
    }

    private static String call(CamelContext context) {
        try {
            return context.createProducerTemplate().asyncRequestBody("direct:start", "x", String.class)
                    .get(10, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            return "not redelivered within 10 seconds";
        } catch (Exception e) {
            return e.toString();
        }
    }

    /** Compiles the dump in a RouteBuilder and creates an instance of it. */
    private static RoutesBuilder load(String dump) throws Exception {
        String source = "package test.generated;\n\n"
                        + "import org.apache.camel.ExchangePattern;\n"
                        + "import org.apache.camel.LoggingLevel;\n"
                        + "import org.apache.camel.Predicate;\n"
                        + "import org.apache.camel.builder.RouteBuilder;\n\n"
                        + "public class DumpedRoutes extends RouteBuilder {\n"
                        + "    @Override\n"
                        + "    public void configure() throws Exception {\n"
                        + dump + "\n"
                        + "    }\n"
                        + "}\n";

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("a JDK is needed to compile the dump").isNotNull();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        Map<String, ByteArrayOutputStream> classes = new HashMap<>();
        JavaFileManager fileManager
                = new ForwardingJavaFileManager<>(compiler.getStandardFileManager(diagnostics, null, null)) {
                    @Override
                    public JavaFileObject getJavaFileForOutput(
                            Location location, String name, JavaFileObject.Kind kind, FileObject sibling) {
                        return new SimpleJavaFileObject(URI.create("mem:///" + name.replace('.', '/') + kind.extension), kind) {
                            @Override
                            public OutputStream openOutputStream() {
                                return classes.computeIfAbsent(name, n -> new ByteArrayOutputStream());
                            }
                        };
                    }
                };
        JavaFileObject file = new SimpleJavaFileObject(
                URI.create("string:///" + CLASS_NAME.replace('.', '/') + JavaFileObject.Kind.SOURCE.extension),
                JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source;
            }
        };
        boolean compiled = compiler.getTask(null, fileManager, diagnostics,
                List.of("-proc:none", "-classpath", System.getProperty("java.class.path")), null, List.of(file)).call();
        assertThat(compiled).as(diagnostics.getDiagnostics() + "\n" + source).isTrue();

        ClassLoader loader = new ClassLoader(JavaDumpExplicitDefaultValuesTest.class.getClassLoader()) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                ByteArrayOutputStream bytes = classes.get(name);
                if (bytes == null) {
                    throw new ClassNotFoundException(name);
                }
                byte[] b = bytes.toByteArray();
                return defineClass(name, b, 0, b.length);
            }
        };
        return (RoutesBuilder) loader.loadClass(CLASS_NAME).getDeclaredConstructor().newInstance();
    }
}
