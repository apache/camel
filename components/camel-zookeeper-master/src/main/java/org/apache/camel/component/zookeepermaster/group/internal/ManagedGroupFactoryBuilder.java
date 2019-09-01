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
package org.apache.camel.component.zookeepermaster.group.internal;

import java.util.concurrent.Callable;

import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.spi.BeanIntrospection;
import org.apache.camel.support.ObjectHelper;
import org.apache.curator.framework.CuratorFramework;

public final class ManagedGroupFactoryBuilder {

    private ManagedGroupFactoryBuilder() {
    }

    public static ManagedGroupFactory create(CuratorFramework curator,
                                             ClassLoader loader,
                                             CamelContext camelContext,
                                             Callable<CuratorFramework> factory) throws Exception {
        if (curator != null) {
            return new StaticManagedGroupFactory(curator, false);
        }
        try {
            Class<?> clazz = camelContext.getClassResolver().resolveClass("org.apache.camel.component.zookeepermaster.group.internal.osgi.OsgiManagedGroupFactory");
            if (clazz != null) {
                Object instance = ObjectHelper.newInstance(clazz);
                BeanIntrospection beanIntrospection = camelContext.adapt(ExtendedCamelContext.class).getBeanIntrospection();
                beanIntrospection.setProperty(camelContext, instance, "classLoader", loader);
                return (ManagedGroupFactory) instance;
            }
        } catch (Throwable e) {
            // Ignore if we'e not in OSGi
        }
        return new StaticManagedGroupFactory(factory.call(), true);
    }

}
