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
package org.apache.camel.dsl.yaml.deserializers;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.model.Block;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.OutputNode;
import org.apache.camel.model.ProcessorDefinition;

public class OutputAwareFromDefinition implements OutputNode, Block {

    private final List<ProcessorDefinition<?>> outputs;
    private FromDefinition delegate;

    public OutputAwareFromDefinition() {
        this((FromDefinition) null);
    }

    public OutputAwareFromDefinition(String url) {
        this(new FromDefinition(url));
    }

    public OutputAwareFromDefinition(FromDefinition definition) {
        this.delegate = definition;
        this.outputs = new ArrayList<>();
    }

    public FromDefinition getDelegate() {
        return delegate;
    }

    public void setDelegate(FromDefinition delegate) {
        this.delegate = delegate;
    }

    @Override
    public void addOutput(ProcessorDefinition<?> processorDefinition) {
        outputs.add(processorDefinition);
    }

    public List<ProcessorDefinition<?>> getOutputs() {
        return outputs;
    }
}
