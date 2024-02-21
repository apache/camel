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
import org.apache.camel.wasm.Wasm;

/**
 * Invoke Wasm functions.
 */
@UriEndpoint(
             firstVersion = "4.4.0",
             scheme = Wasm.SCHEME,
             title = "Wasm",
             syntax = "wasm:functionName",
             producerOnly = true,
             remote = false,
             category = {
                     Category.CORE,
                     Category.SCRIPT
             },
             headersClass = Wasm.Headers.class)
public class WasmEndpoint extends DefaultEndpoint {

    @Metadata(required = true)
    @UriPath(description = "The Function Name")
    private final String functionName;

    @UriParam
    private WasmConfiguration configuration;

    public WasmEndpoint(
                        String endpointUri,
                        Component component,
                        String functionName,
                        WasmConfiguration configuration) {

        super(endpointUri, component);

        this.functionName = functionName;
        this.configuration = configuration;
    }

    public WasmConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new WasmProducer(this, configuration.getModule(), functionName);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot consume from a wasm endpoint");
    }
}
