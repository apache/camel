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
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.impl.CamelPostProcessorHelper;

public class ReactivePostProcessorHelper extends CamelPostProcessorHelper {

    public ReactivePostProcessorHelper(CamelContext camelContext) {
        super(camelContext);
    }

    @Override
    public Object getInjectionValue(Class<?> type, String endpointUri, String endpointRef, String endpointProperty,
                                    String injectionPointName, Object bean, String beanName) {
        return super.getInjectionValue(type, endpointUri, endpointRef, endpointProperty, injectionPointName, bean, beanName);
    }

    @Override
    public Object getInjectionBeanValue(Class<?> type, String name) {
        try {
            return super.getInjectionBeanValue(type, name);
        } catch (NoSuchBeanException e) {
            // ignore
        }

        // lets build a proxy
        return "";
    }

}
