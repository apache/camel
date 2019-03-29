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
import org.apache.camel.DelegateProcessor;
import org.apache.camel.Processor;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.ManagedProcessMBean;
import org.apache.camel.model.ProcessDefinition;
import org.apache.camel.util.ObjectHelper;

@ManagedResource(description = "Managed Process")
public class ManagedProcess extends ManagedProcessor implements ManagedProcessMBean {
    private final Processor processor;
    private String processorClassName;

    public ManagedProcess(CamelContext context, Processor processor, ProcessDefinition definition) {
        super(context, processor, definition);
        this.processor = processor;
    }

    @Override
    public ProcessDefinition getDefinition() {
        return (ProcessDefinition) super.getDefinition();
    }

    @Override
    public String getRef() {
        return getDefinition().getRef();
    }

    @Override
    public String getProcessorClassName() {
        if (processorClassName != null) {
            return processorClassName;
        }
        Processor target = processor;
        if (target instanceof DelegateProcessor) {
            target = ((DelegateProcessor) target).getProcessor();
        }
        processorClassName = ObjectHelper.className(target);
        return processorClassName;
    }
}
