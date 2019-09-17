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
package org.apache.camel.management.mbean;

import org.apache.camel.CamelContext;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.ManagedThrowExceptionMBean;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.processor.ThrowExceptionProcessor;
import org.apache.camel.util.ObjectHelper;

@ManagedResource(description = "Managed ThrowException")
public class ManagedThrowException extends ManagedProcessor implements ManagedThrowExceptionMBean {
    private final ThrowExceptionProcessor processor;

    public ManagedThrowException(CamelContext context, ThrowExceptionProcessor processor, ProcessorDefinition<?> definition) {
        super(context, processor, definition);
        this.processor = processor;
    }

    @Override
    public String getMessage() {
        return processor.getMessage();
    }

    @Override
    public String getExceptionType() {
        if (processor.getType() != null) {
            return ObjectHelper.name(processor.getType());
        } else if (processor.getException() != null) {
            return ObjectHelper.className(processor.getException());
        } else {
            return null;
        }
    }
}
