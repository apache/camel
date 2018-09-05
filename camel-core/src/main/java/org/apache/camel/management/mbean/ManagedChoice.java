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

import java.util.List;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;

import org.apache.camel.CamelContext;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.CamelOpenMBeanTypes;
import org.apache.camel.api.management.mbean.ManagedChoiceMBean;
import org.apache.camel.model.ChoiceDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.WhenDefinition;
import org.apache.camel.processor.ChoiceProcessor;
import org.apache.camel.processor.FilterProcessor;
import org.apache.camel.util.ObjectHelper;

/**
 * @version 
 */
@ManagedResource(description = "Managed Choice")
public class ManagedChoice extends ManagedProcessor implements ManagedChoiceMBean {
    private final ChoiceProcessor processor;

    public ManagedChoice(CamelContext context, ChoiceProcessor processor, ProcessorDefinition<?> definition) {
        super(context, processor, definition);
        this.processor = processor;
    }

    @Override
    public ChoiceDefinition getDefinition() {
        return (ChoiceDefinition) super.getDefinition();
    }

    @Override
    public void reset() {
        processor.reset();
        super.reset();
    }

    @Override
    public Boolean getSupportExtendedInformation() {
        return true;
    }

    @Override
    public TabularData choiceStatistics() {
        try {
            TabularData answer = new TabularDataSupport(CamelOpenMBeanTypes.choiceTabularType());

            List<WhenDefinition> whens = getDefinition().getWhenClauses();
            List<FilterProcessor> filters = processor.getFilters();

            for (int i = 0; i < filters.size(); i++) {
                WhenDefinition when = whens.get(i);
                FilterProcessor filter = filters.get(i);

                CompositeType ct = CamelOpenMBeanTypes.choiceCompositeType();
                String predicate = when.getExpression().getExpression();
                String language = when.getExpression().getLanguage();
                Long matches = filter.getFilteredCount();

                CompositeData data = new CompositeDataSupport(ct,
                        new String[]{"predicate", "language", "matches"},
                        new Object[]{predicate, language, matches});
                answer.put(data);
            }
            if (getDefinition().getOtherwise() != null) {
                CompositeType ct = CamelOpenMBeanTypes.choiceCompositeType();
                String predicate = "otherwise";
                String language = "";
                Long matches = processor.getNotFilteredCount();

                CompositeData data = new CompositeDataSupport(ct,
                        new String[]{"predicate", "language", "matches"},
                        new Object[]{predicate, language, matches});
                answer.put(data);
            }

            return answer;
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
    }
}