/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.spring.spi;

import org.apache.camel.spi.Injector;
import org.apache.camel.impl.ReflectionInjector;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.support.AbstractRefreshableApplicationContext;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;

/**
 * A Spring implementation of {@link Injector} allowing Spring to be used to dependency inject newly created POJOs
 *
 * @version $Revision$
 */
public class SpringInjector extends ReflectionInjector {
    private static final transient Log log = LogFactory.getLog(SpringInjector.class);

    private final AbstractRefreshableApplicationContext applicationContext;
    private int autowireMode = AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR;
    private boolean dependencyCheck = false;

    public SpringInjector(AbstractRefreshableApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public Object newInstance(Class type) {
        // TODO support annotations for mandatory injection points?
        return applicationContext.getBeanFactory().createBean(type, autowireMode, dependencyCheck);
    }

    public int getAutowireMode() {
        return autowireMode;
    }

    public void setAutowireMode(int autowireMode) {
        this.autowireMode = autowireMode;
    }

    public boolean isDependencyCheck() {
        return dependencyCheck;
    }

    public void setDependencyCheck(boolean dependencyCheck) {
        this.dependencyCheck = dependencyCheck;
    }
}
