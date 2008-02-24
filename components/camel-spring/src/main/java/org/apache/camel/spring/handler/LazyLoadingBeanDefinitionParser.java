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
package org.apache.camel.spring.handler;

import org.apache.camel.util.ObjectHelper;

/**
 * A {@link BeanDefinitionParser} which lazy loads the type on which it creates to allow the schema to be loosly coupled
 * with the camel jars.
 *
 * @version $Revision$
 */
public class LazyLoadingBeanDefinitionParser extends BeanDefinitionParser {
    private String className;
    private String moduleName;

    public LazyLoadingBeanDefinitionParser(String className, String moduleName) {
        this.className = className;
        this.moduleName = moduleName;
    }

    @Override
    protected Class loadType() {
        Class<?> answer = ObjectHelper.loadClass(className, getClass().getClassLoader());
        if (answer == null) {
            throw new IllegalArgumentException("Class: " + className + " could not be found. You need to add Camel module: " + moduleName + " to your classpath");
        }
        return answer;
    }
}
