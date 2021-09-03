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

import org.apache.camel.RuntimeCamelException;

public class DefaultBeanInfoTypeResolver implements BeanInfoTypeResolver {

    public static final DefaultBeanInfoTypeResolver INSTANCE = new DefaultBeanInfoTypeResolver();

    @Override
    public Object[] resolve(Class<?> type, Method explicitMethod) {
        boolean changed = false;
        while (type.isSynthetic()) {
            changed = true;
            type = type.getSuperclass();
            if (explicitMethod != null) {
                try {
                    explicitMethod = type.getDeclaredMethod(explicitMethod.getName(), explicitMethod.getParameterTypes());
                } catch (NoSuchMethodException e) {
                    throw new RuntimeCamelException("Unable to find a method " + explicitMethod + " on " + type, e);
                }
            }
        }
        if (changed) {
            return new Object[] { type, explicitMethod };
        } else {
            return null;
        }
    }

}
