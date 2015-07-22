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

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Injector;
import org.apache.camel.spi.Registry;
import org.apache.camel.util.ObjectHelper;

/**
 * CDI {@link org.apache.camel.CamelContext} class.
 */
public class CdiCamelContext extends DefaultCamelContext {

    private BeanManager beanManager;

    public CdiCamelContext() {
    }

    @Inject
    public void setBeanManager(Instance<BeanManager> beanManager) {
        this.beanManager = beanManager.get();
    }

    @Inject
    public void setRegistry(Instance<Registry> instance) {
        if (isSingular(instance)) {
            setRegistry(instance.get());
        }
    }

    @Inject
    public void setInjector(Instance<Injector> instance) {
        if (isSingular(instance)) {
            setInjector(instance.get());
        }
    }

    private <T> boolean isSingular(Instance<T> instance) {
        return !instance.isUnsatisfied() && !instance.isAmbiguous();
    }

    @PostConstruct
    @Override
    public void start() {
        // make sure to use cdi capable bean registry and injector
        if (!(getRegistry() instanceof CdiBeanRegistry)) {
            setRegistry(new CdiBeanRegistry(beanManager));
        }

        if (!(getInjector() instanceof CdiInjector)) {
            setInjector(new CdiInjector(getInjector()));
        }

        try {
            super.start();
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
    }

    @PreDestroy
    @Override
    public void stop() {
        try {
            super.stop();
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
    }

}
