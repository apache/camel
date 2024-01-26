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

import com.dylibso.chicory.runtime.Module;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.ResourceLoader;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.wasm.WasmFunction;
import org.apache.camel.wasm.WasmSupport;

public class WasmProducer extends DefaultProducer {

    private final String functionModule;
    private final String functionName;

    private Module module;
    private WasmFunction function;

    public WasmProducer(Endpoint endpoint, String functionModule, String functionName) throws Exception {
        super(endpoint);

        this.functionModule = functionModule;
        this.functionName = functionName;
    }

    @Override
    public void doInit() throws Exception {
        final ResourceLoader rl = PluginHelper.getResourceLoader(getEndpoint().getCamelContext());
        final Resource res = rl.resolveResource(this.functionModule);

        try (InputStream is = res.getInputStream()) {
            this.module = Module.builder(is).build();
        }
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();

        if (this.module != null && this.function == null) {
            this.function = new WasmFunction(this.module, this.functionName);
        }
    }

    @Override
    public void doStop() throws Exception {
        super.doStop();

        this.function = null;
    }

    @Override
    public void doShutdown() throws Exception {
        super.doShutdown();

        this.function = null;
        this.module = null;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        byte[] in = WasmSupport.serialize(exchange);
        byte[] result = function.run(in);

        WasmSupport.deserialize(result, exchange);
    }
}
