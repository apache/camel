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

import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;

import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.CamelOpenMBeanTypes;
import org.apache.camel.api.management.mbean.ManagedSwitchMBean;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.SwitchCaseDefinition;
import org.apache.camel.model.SwitchDefinition;
import org.apache.camel.processor.SwitchProcessor;

@ManagedResource(description = "Managed Switch")
public class ManagedSwitch extends ManagedProcessor implements ManagedSwitchMBean {
    public ManagedSwitch(CamelContext context, SwitchProcessor processor, ProcessorDefinition<?> definition) {
        super(context, processor, definition);
    }

    @Override
    public SwitchProcessor getProcessor() {
        return (SwitchProcessor) super.getProcessor();
    }

    @Override
    public SwitchDefinition getDefinition() {
        return (SwitchDefinition) super.getDefinition();
    }

    @Override
    public Boolean getSupportExtendedInformation() {
        return true;
    }

    @Override
    public long getUnmatchedCount() {
        return getProcessor().getUnmatchedCount();
    }

    @Override
    public void reset() {
        getProcessor().reset();
        super.reset();
    }

    @Override
    public TabularData extendedInformation() {
        try {
            TabularDataSupport table = new TabularDataSupport(CamelOpenMBeanTypes.switchTabularType());
            for (int i = 0; i < getDefinition().getCases().size(); i++) {
                SwitchCaseDefinition c = getDefinition().getCases().get(i);
                addRow(table, c.getId(), c.getValue() != null ? c.getValue() : c.getValues().toString(),
                        c.getUri(), getProcessor().getMatchedCount(i));
            }
            if (getDefinition().getOtherwise() != null) {
                addRow(table, getDefinition().getId() + "-otherwise", "otherwise", getDefinition().getOtherwise(),
                        getUnmatchedCount());
            }
            return table;
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }

    private void addRow(TabularDataSupport table, String id, String value, String uri, long matches) throws Exception {
        table.put(new CompositeDataSupport(
                CamelOpenMBeanTypes.switchCompositeType(),
                new String[] { "index", "id", "value", "uri", "matches" },
                new Object[] { table.size(), id, value, uri, matches }));
    }
}
