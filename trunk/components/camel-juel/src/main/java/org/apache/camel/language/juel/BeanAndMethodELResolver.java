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
package org.apache.camel.language.juel;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import javax.el.BeanELResolver;
import javax.el.ELContext;
import javax.el.PropertyNotFoundException;

/**
 * An extension of the JUEL {@link BeanELResolver} which also supports the resolving of methods
 *
 * @version 
 */
public class BeanAndMethodELResolver extends BeanELResolver {
    public BeanAndMethodELResolver() {
        super(false);
    }

    @Override
    public Object getValue(ELContext elContext, Object base, Object property) {
        try {
            return (property instanceof Method) ? property : super.getValue(elContext, base, property);
        } catch (PropertyNotFoundException e) {
            // lets see if its a method call...
            Method method = findMethod(elContext, base, property);
            if (method != null) {
                elContext.setPropertyResolved(true);
                return method;
            } else {
                throw e;
            }
        }
    }

    protected Method findMethod(ELContext elContext, Object base, Object property) {
        if (base != null) {
            Method[] methods = base.getClass().getMethods();
            List<Method> matching = new ArrayList<Method>();
            for (Method method : methods) {
                if (method.getName().equals(property.toString()) && Modifier.isPublic(method.getModifiers())) {
                    matching.add(method);
                }
            }
            int size = matching.size();
            if (size > 0) {
                if (size > 1) {
                    // TODO there's currently no way for JUEL to tell us how many parameters there are
                    // so lets just pick the first one that has a single param by default
                    for (Method method : matching) {
                        Class<?>[] paramTypes = method.getParameterTypes();
                        if (paramTypes.length == 1) {
                            return method;
                        }
                    }
                }
                // lets default to the first one
                return matching.get(0);
            }
        }
        return null;
    }
}
