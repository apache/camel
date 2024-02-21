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

import java.util.ArrayList;
import java.util.List;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;

import org.apache.camel.CamelContext;
import org.apache.camel.Channel;
import org.apache.camel.Navigate;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.CamelOpenMBeanTypes;
import org.apache.camel.api.management.mbean.ManagedDoTryMBean;
import org.apache.camel.model.CatchDefinition;
import org.apache.camel.model.TryDefinition;
import org.apache.camel.processor.CatchProcessor;
import org.apache.camel.processor.TryProcessor;

@ManagedResource(description = "Managed DoTry")
public class ManagedDoTry extends ManagedProcessor implements ManagedDoTryMBean {

    private final TryProcessor processor;
    private final List<CatchProcessor> catchProcessors;

    public ManagedDoTry(CamelContext context, TryProcessor processor, TryDefinition definition) {
        super(context, processor, definition);
        this.processor = processor;

        if (processor.getCatchClauses() != null) {
            catchProcessors = new ArrayList<>();
            for (Processor p : processor.getCatchClauses()) {
                Channel c = (Channel) p;
                CatchProcessor caught = asCatchProcessor(c);
                catchProcessors.add(caught);
            }
        } else {
            catchProcessors = null;
        }
    }

    @Override
    public TryDefinition getDefinition() {
        return (TryDefinition) super.getDefinition();
    }

    @Override
    public Boolean getSupportExtendedInformation() {
        return true;
    }

    @Override
    public TabularData extendedInformation() {
        try {
            TabularData answer = new TabularDataSupport(CamelOpenMBeanTypes.doTryTabularType());

            if (catchProcessors != null) {
                List<CatchDefinition> exceptions = getDefinition().getCatchClauses();
                for (int i = 0; i < catchProcessors.size(); i++) {
                    CatchDefinition when = exceptions.get(i);
                    CatchProcessor caught = catchProcessors.get(i);
                    if (caught != null) {
                        for (String fqn : caught.getCaughtExceptionClassNames()) {
                            CompositeType ct = CamelOpenMBeanTypes.doTryCompositeType();
                            String predicate = null;
                            String language = null;
                            if (when.getOnWhen() != null) {
                                predicate = when.getOnWhen().getExpression().getExpression();
                                language = when.getOnWhen().getExpression().getLanguage();
                            }
                            long matches = caught.getCaughtCount(fqn);

                            CompositeData data = new CompositeDataSupport(
                                    ct,
                                    new String[] { "exception", "predicate", "language", "matches" },
                                    new Object[] { fqn, predicate, language, matches });
                            answer.put(data);
                        }
                    }
                }
            }
            return answer;
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }

    private CatchProcessor asCatchProcessor(Navigate<Processor> nav) {
        // drill down and find
        while (nav.hasNext()) {
            for (Processor p : nav.next()) {
                if (p instanceof CatchProcessor) {
                    return (CatchProcessor) p;
                }
                if (p instanceof Navigate<?>) {
                    Navigate<Processor> child = (Navigate<Processor>) p;
                    return asCatchProcessor(child);
                }
            }
        }
        return null;
    }

}
