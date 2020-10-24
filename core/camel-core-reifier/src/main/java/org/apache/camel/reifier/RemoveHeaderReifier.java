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

import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RemoveHeaderDefinition;
import org.apache.camel.processor.RemoveHeaderProcessor;
import org.apache.camel.util.ObjectHelper;

public class RemoveHeaderReifier extends ProcessorReifier<RemoveHeaderDefinition> {

    public RemoveHeaderReifier(Route route, ProcessorDefinition<?> definition) {
        super(route, (RemoveHeaderDefinition) definition);
    }

    @Override
    public Processor createProcessor() throws Exception {
        ObjectHelper.notNull(definition.getHeaderName(), "headerName", this);
        return new RemoveHeaderProcessor(parseString(definition.getHeaderName()));
    }
}
