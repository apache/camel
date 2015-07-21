/**
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
package org.apache.camel.management.mbean;

import org.apache.camel.CamelContext;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.ManagedLoopMBean;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.processor.LoopProcessor;

/**
 * @version 
 */
@ManagedResource(description = "Managed Loop")
public class ManagedLoop extends ManagedProcessor implements ManagedLoopMBean {
    private final LoopProcessor processor;
    private final String uri;

    public ManagedLoop(CamelContext context, LoopProcessor processor, ProcessorDefinition<?> definition) {
        super(context, processor, definition);
        this.processor = processor;
        this.uri = processor.getExpression().toString();
    }

    @Override
    public String getExpression() {
        return uri;
    }

    @Override
    public Boolean isCopy() {
        return processor.isCopy();
    }
}
