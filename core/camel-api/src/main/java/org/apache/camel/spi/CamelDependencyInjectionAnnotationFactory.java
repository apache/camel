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
package org.apache.camel.spi;

/**
 * A factory which performs the task from Camel dependency injection annotations on a field, property or method
 * parameter of a specified type.
 */
public interface CamelDependencyInjectionAnnotationFactory {

    /**
     * The task for binding the bean to the registry (eg {@link org.apache.camel.BindToRegistry annotation}
     *
     * @param  id              the bean id
     * @param  bean            the bean instance
     * @param  beanName        the bean name
     * @param  beanPostProcess whether bean post processor should be performed
     * @return                 the created task to use for binding the bean
     */
    Runnable createBindToRegistryFactory(String id, Object bean, String beanName, boolean beanPostProcess);

}
