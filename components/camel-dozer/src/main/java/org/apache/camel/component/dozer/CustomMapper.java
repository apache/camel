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
package org.apache.camel.component.dozer;

import java.lang.reflect.Method;

import org.apache.camel.spi.ClassResolver;

/**
 * Allows a user to customize a field mapping using a POJO that is not
 * required to extend/implement Dozer-specific classes.
 */
public class CustomMapper extends BaseConverter {
    
    private ClassResolver resolver;
    
    public CustomMapper(ClassResolver resolver) {
        this.resolver = resolver;
    }
    
    @Override
    public Object convert(Object existingDestinationFieldValue, 
            Object sourceFieldValue, 
            Class<?> destinationClass,
            Class<?> sourceClass) {
        try {
            return mapCustom(sourceFieldValue);
        } finally {
            done();
        }
    }
    
    Method selectMethod(Class<?> customClass, Object fromType) {
        Method method = null;
        for (Method m : customClass.getDeclaredMethods()) {
            if (m.getReturnType() != null 
                    && m.getParameterTypes().length == 1
                    && m.getParameterTypes()[0].isAssignableFrom(fromType.getClass())) {
                method = m;
                break;
            }
        }
        return method;
    }

    Object mapCustom(Object source) {
        Object customMapObj;
        Method mapMethod = null;
        
        // The converter parameter is stored in a thread local variable, so 
        // we need to parse the parameter on each invocation
        String[] params = getParameter().split(",");
        String className = params[0];
        String operation = params.length > 1 ? params[1] : null;
        
        try {
            Class<?> customClass = resolver.resolveClass(className);
            customMapObj = customClass.newInstance();
            // If a specific mapping operation has been supplied use that
            if (operation != null) {
                mapMethod = customClass.getMethod(operation, source.getClass());
            } else {
                mapMethod = selectMethod(customClass, source);
            }
        } catch (Exception cnfEx) {
            throw new RuntimeException("Failed to load custom mapping", cnfEx);
        }
        
        // Verify that we found a matching method
        if (mapMethod == null) {
            throw new RuntimeException("No eligible custom mapping methods in " + className);
        }
        
        // Invoke the custom mapping method
        try {
            return mapMethod.invoke(customMapObj, source);
        } catch (Exception ex) {
            throw new RuntimeException("Error while invoking custom mapping", ex);
        }
    }
}
