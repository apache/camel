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
package org.apache.camel.cdi;

import javax.enterprise.inject.spi.Annotated;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.InjectionTarget;
import javax.enterprise.inject.spi.Producer;

import org.apache.camel.CamelContext;
import org.apache.camel.core.osgi.utils.BundleContextUtils;

@Vetoed
final class CdiCamelEnvironment {

    private final boolean hasBundleContext;

    CdiCamelEnvironment() {
        hasBundleContext = isCamelCoreOsgiPresent() && hasBundleContext(CdiCamelExtension.class);
    }

    <T extends CamelContext> Producer<T> camelContextProducer(Producer<T> delegate, Annotated annotated, BeanManager manager, CdiCamelExtension extension) {
        CamelContextProducer<T> producer = new CamelContextProducer<>(delegate, annotated, manager, extension);
        return hasBundleContext ? new CamelContextOsgiProducer<>(producer) : producer;
    }

    <T extends CamelContext> InjectionTarget<T> camelContextInjectionTarget(InjectionTarget<T> delegate, Annotated annotated, BeanManager manager, CdiCamelExtension extension) {
        CamelContextProducer<T> producer = new CamelContextProducer<>(delegate, annotated, manager, extension);
        return new CamelContextInjectionTarget<>(delegate, hasBundleContext ? new CamelContextOsgiProducer<>(producer) : producer);
    }

    private static boolean isCamelCoreOsgiPresent() {
        try {
            getClassLoader(CdiCamelExtension.class).loadClass("org.apache.camel.core.osgi.OsgiCamelContextHelper");
            return true;
        } catch (ClassNotFoundException | NoClassDefFoundError cause) {
            return false;
        }
    }

    private static boolean hasBundleContext(Class clazz) {
        return BundleContextUtils.getBundleContext(clazz) != null;
    }

    private static ClassLoader getClassLoader(Class<?> clazz) {
        if (Thread.currentThread().getContextClassLoader() != null) {
            return Thread.currentThread().getContextClassLoader();
        } else {
            return clazz.getClassLoader();
        }
    }
}
