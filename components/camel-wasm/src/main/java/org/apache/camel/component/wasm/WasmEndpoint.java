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
package org.apache.camel.component.wasm;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.wasm.WasiCommand;
import org.apache.camel.wasm.Wasm;
import run.endive.wasm.Parser;
import run.endive.wasm.WasmModule;

/**
 * Run Wasm programs (WASI Preview 1 command modules) with the message body as standard input.
 */
@UriEndpoint(firstVersion = "4.4.0", scheme = Wasm.SCHEME, title = "Wasm", syntax = "wasm:module",
             producerOnly = true, remote = false, category = { Category.CORE, Category.SCRIPT },
             headersClass = Wasm.Headers.class)
@BodyTypes(producerIn = @BodyType(value = { Carrier.NULL, Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE },
                                  description = "The standard input of the module: text, bytes, a stream or a file; no body is"
                                                + " an empty input. A Map, a List or a POJO fails: marshal it first"),
           producerOut = @BodyType(value = Carrier.BYTES, javaType = "byte[]",
                                   description = "The standard output of the module, with the exit code in the"
                                                 + " CamelWasmExitCode header"))
public class WasmEndpoint extends DefaultEndpoint {

    private static final String DEFAULT_PROGRAM_NAME = "module.wasm";

    @Metadata(required = true)
    @UriPath(description = "The WASI Preview 1 command module to run, as a resource: by default from the classpath, or"
                           + " with a prefix such as classpath:, file: or http:")
    private final String module;
    @UriParam
    private WasmConfiguration configuration;

    private final Lock lock = new ReentrantLock();
    private volatile WasiCommand command;

    public WasmEndpoint(String endpointUri, Component component, String module,
                        WasmConfiguration configuration) {
        super(endpointUri, component);
        this.module = module;
        this.configuration = configuration;
    }

    @Override
    public boolean isRemote() {
        return false;
    }

    public String getModule() {
        return module;
    }

    public WasmConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * The module of this endpoint as a command. The module is loaded, checked and, with compile=true, compiled on the
     * first call, by the first producer that is initialized; all producers of the endpoint then share the result, which
     * is immutable, for as long as the endpoint exists. So a route that is stopped and started again gets a new
     * producer, but neither loads nor compiles the module again, and the classes generated for a compiled module can
     * only be unloaded when the endpoint is no longer reachable (removed from the context, or the context stopped), not
     * when a route stops.
     * <p/>
     * A failed call leaves nothing behind, so the next call (of the next producer that is initialized) tries again.
     */
    WasiCommand command() throws Exception {
        WasiCommand answer = command;
        if (answer == null) {
            lock.lock();
            try {
                answer = command;
                if (answer == null) {
                    List<String> arguments
                            = arguments(programName(module), configuration.getArgs());
                    for (String argument : arguments) {
                        checkNoNul("argument " + argument, argument);
                    }
                    WasmModule parsed;
                    try (InputStream is
                            = ResourceHelper.resolveMandatoryResourceAsInputStream(getCamelContext(), module)) {
                        parsed = Parser.parse(is);
                    }
                    answer = new WasiCommand(
                            parsed, arguments, configuration.getMaxMemoryPages(), configuration.isCompile());
                    command = answer;
                }
            } finally {
                lock.unlock();
            }
        }
        return answer;
    }

    static String checkNoNul(String what, String value) {
        // WASI passes arguments and environment variables as NUL terminated strings
        if (value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("The " + what + " contains a NUL character, which a module cannot receive");
        }
        return value;
    }

    /**
     * The program name the module receives as argument zero: the file name of the module location, without scheme,
     * directories, user info or query. The location itself is not passed on: it can carry credentials (an http location
     * with user info or a token in the query) and shows host paths to a module that has no file access.
     */
    static String programName(String location) {
        String name = location;
        int query = name.indexOf('?');
        if (query >= 0) {
            name = name.substring(0, query);
        }
        int scheme = name.indexOf(':');
        if (scheme > 0) {
            String prefix = name.substring(0, scheme);
            if ("base64".equals(prefix) || "gzip".equals(prefix)) {
                // the rest is the module itself, not a name
                return DEFAULT_PROGRAM_NAME;
            }
            name = name.substring(scheme + 1);
        }
        if (name.startsWith("//")) {
            // host, port and user info are not part of the name
            int path = name.indexOf('/', 2);
            name = path < 0 ? "" : name.substring(path);
        }
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        return name.isEmpty() ? DEFAULT_PROGRAM_NAME : name;
    }

    /**
     * Splits the args option into the argument list of the module, with the program name first. Arguments are separated
     * by whitespace; double quotes group an argument that contains whitespace.
     */
    static List<String> arguments(String program, String args) {
        List<String> answer = new ArrayList<>();
        answer.add(program);
        if (args == null) {
            return answer;
        }
        StringBuilder current = null;
        boolean quoted = false;
        for (int i = 0; i < args.length(); i++) {
            char c = args.charAt(i);
            if (c == '"') {
                quoted = !quoted;
                if (current == null) {
                    current = new StringBuilder();
                }
            } else if (Character.isWhitespace(c) && !quoted) {
                if (current != null) {
                    answer.add(current.toString());
                    current = null;
                }
            } else {
                if (current == null) {
                    current = new StringBuilder();
                }
                current.append(c);
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("Unbalanced double quote in args: " + args);
        }
        if (current != null) {
            answer.add(current.toString());
        }
        return answer;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new WasmProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot consume from a wasm endpoint");
    }
}
