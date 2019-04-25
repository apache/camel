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
package org.apache.camel.component.bean;

import java.lang.reflect.Method;

import org.apache.camel.CamelContext;
import org.apache.camel.Processor;
import org.apache.camel.spi.BeanProcessorFactory;

public final class DefaultBeanProcessorFactory implements BeanProcessorFactory {

    public DefaultBeanProcessorFactory() {
    }

    @Override
    public Processor createBeanProcessor(CamelContext camelContext, Object pojo, Method method) throws Exception {
        BeanInfo info = new BeanInfo(camelContext, method);
        return new BeanProcessor(pojo, info);
    }
}
