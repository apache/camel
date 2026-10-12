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
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
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
public class WasmEndpoint extends DefaultEndpoint {

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
                            = WasmProducer.arguments(WasmProducer.programName(module), configuration.getArgs());
                    for (String argument : arguments) {
                        WasmProducer.checkNoNul("argument " + argument, argument);
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

    @Override
    public Producer createProducer() throws Exception {
        return new WasmProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot consume from a wasm endpoint");
    }
}
