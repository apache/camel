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
package org.apache.camel.reifier;

import java.util.stream.Stream;

import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RemovePropertiesDefinition;
import org.apache.camel.processor.RemovePropertiesProcessor;
import org.apache.camel.util.ObjectHelper;

public class RemovePropertiesReifier extends ProcessorReifier<RemovePropertiesDefinition> {

    public RemovePropertiesReifier(Route route, ProcessorDefinition<?> definition) {
        super(route, (RemovePropertiesDefinition) definition);
    }

    @Override
    public Processor createProcessor() throws Exception {
        ObjectHelper.notNull(definition.getPattern(), "patterns", this);
        if (definition.getExcludePatterns() != null) {
            return new RemovePropertiesProcessor(parseString(definition.getPattern()), parseStrings(definition.getExcludePatterns()));
        } else if (definition.getExcludePattern() != null) {
            return new RemovePropertiesProcessor(parseString(definition.getPattern()), parseStrings(new String[] {definition.getExcludePattern()}));
        } else {
            return new RemovePropertiesProcessor(parseString(definition.getPattern()), null);
        }
    }

    private String[] parseStrings(String[] array) {
        return Stream.of(array).map(this::parseString).toArray(String[]::new);
    }
}
