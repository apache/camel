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
package org.apache.camel.impl.engine;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.spi.ProcessorFactory;

public class TypedProcessorFactory<T extends NamedNode> implements ProcessorFactory {
    private final Class<T> type;

    protected TypedProcessorFactory(Class<T> type) {
        this.type = type;
    }

    @Override
    public Processor createChildProcessor(Route route, NamedNode definition, boolean mandatory) throws Exception {
        if (type.isInstance(definition)) {
            return doCreateChildProcessor(route, type.cast(definition), mandatory);
        }

        return null;
    }

    @Override
    public Processor createProcessor(Route route, NamedNode definition) throws Exception {
        if (type.isInstance(definition)) {
            return doCreateProcessor(route, type.cast(definition));
        }

        return null;
    }

    @Override
    public Processor createProcessor(CamelContext camelContext, String definitionName, Map<String, Object> args) throws Exception {
        return null;
    }

    protected Processor doCreateChildProcessor(Route route, T definition, boolean mandatory) throws Exception {
        return null;
    }

    public Processor doCreateProcessor(Route route, T definition) throws Exception {
        return null;
    }
}
