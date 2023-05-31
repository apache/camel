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
import org.apache.camel.api.management.mbean.ManagedDoCatchMBean;
import org.apache.camel.model.CatchDefinition;
import org.apache.camel.model.WhenDefinition;
import org.apache.camel.processor.CatchProcessor;

@ManagedResource(description = "Managed DoCatch")
public class ManagedDoCatch extends ManagedProcessor implements ManagedDoCatchMBean {

    private final CatchProcessor processor;

    public ManagedDoCatch(CamelContext context, CatchProcessor processor, CatchDefinition definition) {
        super(context, processor, definition);
        this.processor = processor;
    }

    @Override
    public CatchDefinition getDefinition() {
        return (CatchDefinition) super.getDefinition();
    }

    @Override
    public String getOnWhen() {
        WhenDefinition when = getDefinition().getOnWhen();
        if (when != null) {
            return when.getExpression().getExpression();
        }
        return null;
    }

    @Override
    public String getOnWhenLanguage() {
        WhenDefinition when = getDefinition().getOnWhen();
        if (when != null) {
            return when.getExpression().getLanguage();
        }
        return null;
    }

    @Override
    public Long getCaughtCount() {
        return processor.getCaughtCount();
    }

    @Override
    public Long getCaughtCount(String className) {
        return processor.getCaughtCount(className);
    }

    @Override
    public String[] getExceptionTypes() {
        return processor.getCaughtExceptionClassNames().toArray(new String[0]);
    }

}
