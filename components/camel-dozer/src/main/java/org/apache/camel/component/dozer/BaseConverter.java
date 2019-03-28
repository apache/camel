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
package org.apache.camel.component.dozer;

import com.github.dozermapper.core.ConfigurableCustomConverter;

/**
 * Configurable converters in Dozer are not thread-safe if a single converter 
 * instance is used.  One thread could step on the parameter being used by 
 * another thread since setParameter() is called first and convert() is called 
 * separately.  This implementation holds a copy of the parameter in 
 * thread-local storage which eliminates the possibility of collision between
 * threads on a single converter instance.
 * 
 * Any converter which is referenced by ID with the Dozer component should
 * extend this class.  It is recommended to call done() in a finally block 
 * in the implementation of convert() to clean up the value stored in the 
 * thread local.
 */
public abstract class BaseConverter implements ConfigurableCustomConverter {
    
    private ThreadLocal<String> localParameter = new ThreadLocal<>();
    
    @Override
    public void setParameter(String parameter) {
        localParameter.set(parameter);
    }
    
    public void done() {
        localParameter.set(null);
    }
    
    public String getParameter() {
        return localParameter.get();
    }
}
