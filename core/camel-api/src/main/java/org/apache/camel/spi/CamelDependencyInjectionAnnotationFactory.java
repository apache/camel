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
 * Factory that creates the task carrying out a Camel dependency-injection annotation, such as binding a
 * {@link org.apache.camel.BindToRegistry} bean into the {@link Registry}.
 * <p/>
 * During {@link CamelBeanPostProcessor} bean post-processing, when a {@link org.apache.camel.BindToRegistry} annotation
 * is found this factory produces a {@link Runnable} that performs the actual binding, including any optional init and
 * destroy methods. Decoupling the binding into a task lets runtimes (such as Spring or Quarkus) control exactly when
 * and how beans are registered, rather than binding eagerly during the scan.
 * <p/>
 * See <a href="https://camel.apache.org/manual/bean-integration.html">Bean Integration</a> in the Camel user manual.
 *
 * @see   CamelBeanPostProcessor
 * @see   org.apache.camel.BindToRegistry
 * @since 3.16
 */
public interface CamelDependencyInjectionAnnotationFactory {

    /**
     * The task for binding the bean to the registry (eg {@link org.apache.camel.BindToRegistry annotation}
     *
     * @param  id              the bean id
     * @param  bean            the bean instance
     * @param  beanName        the bean name
     * @param  beanType        the bean type (optional)
     * @param  beanPostProcess whether bean post processor should be performed
     * @param  initMethod      optional init method (invoked at bind)
     * @param  destroyMethod   optional destroy method (invoked at unbind or stopping Camel)
     * @return                 the created task to use for binding the bean
     */
    Runnable createBindToRegistryFactory(
            String id, Object bean, Class<?> beanType,
            String beanName, boolean beanPostProcess,
            String initMethod, String destroyMethod);

}
