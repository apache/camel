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
package org.apache.camel.cdi;

import java.util.HashMap;
import java.util.Map;
import javax.enterprise.inject.UnsatisfiedResolutionException;
import javax.enterprise.inject.spi.BeanManager;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.engine.CamelPostProcessorHelper;
import org.apache.camel.impl.engine.DefaultCamelBeanPostProcessor;

import static org.apache.camel.cdi.BeanManagerHelper.getReferenceByType;
import static org.apache.camel.cdi.DefaultLiteral.DEFAULT;

@Vetoed
final class CdiCamelBeanPostProcessor extends DefaultCamelBeanPostProcessor {

    private final BeanManager manager;

    private final Map<String, CamelPostProcessorHelper> postProcessorHelpers = new HashMap<>();

    CdiCamelBeanPostProcessor(BeanManager manager) {
        this.manager = manager;
    }

    private CamelPostProcessorHelper getPostProcessorHelper(String contextName) {
        return postProcessorHelpers.computeIfAbsent(contextName, k -> new CamelPostProcessorHelper(getOrLookupCamelContext(k)));
    }

    private CamelContext getOrLookupCamelContext(String contextName) {
        // TODO: proper support for custom context qualifiers
        return getReferenceByType(manager, CamelContext.class,
            contextName.isEmpty() ? DEFAULT : ContextName.Literal.of(contextName))
            .orElseThrow(() -> new UnsatisfiedResolutionException("No Camel context with name [" + contextName + "] is deployed!"));
    }

    @Override
    public CamelContext getOrLookupCamelContext() {
        return getReferenceByType(manager, CamelContext.class).orElse(null);
    }
}
