/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.cdi.internal;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import javax.enterprise.inject.spi.Bean;

/**
 * Contains the bean and the consume methods
 */
public class BeanAdapter {
    private final Bean<?> bean;
    private final List<Method> consumeMethods = new ArrayList<Method>();

    public BeanAdapter(Bean<?> bean) {
        this.bean = bean;
    }

    public Bean<?> getBean() {
        return bean;
    }

    public List<Method> getConsumeMethods() {
        return consumeMethods;
    }

    public void addConsumeMethod(Method method) {
        consumeMethods.add(method);
    }
}
