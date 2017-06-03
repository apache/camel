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
package org.apache.camel.rx.support;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.CamelPostProcessorHelper;
import org.apache.camel.impl.DefaultCamelBeanPostProcessor;

public class ReactiveBeanPostProcessor extends DefaultCamelBeanPostProcessor {

    private ReactivePostProcessorHelper helper;

    public ReactiveBeanPostProcessor(CamelContext camelContext) {
        super(camelContext);
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws Exception {
        return super.postProcessBeforeInitialization(bean, beanName);
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws Exception {
        return super.postProcessAfterInitialization(bean, beanName);
    }

    @Override
    public CamelPostProcessorHelper getPostProcessorHelper() {
        if (helper == null) {
            helper = new ReactivePostProcessorHelper(getOrLookupCamelContext());
        }
        return helper;
    }
}
