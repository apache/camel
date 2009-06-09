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
package org.apache.camel.spring.spi;

import org.apache.camel.spi.Injector;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * A Spring implementation of {@link Injector} allowing Spring to be used to dependency inject newly created POJOs
 *
 * @version $Revision$
 */
public class SpringInjector implements Injector {
    private final ConfigurableApplicationContext applicationContext;

    public SpringInjector(ConfigurableApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public <T> T newInstance(Class<T> type) {
        // use the createBean method with 3 arguments as it exist in Spring 2.0.x as well.
        // this allows us to be compatible with Spring 2.0 also, and not only 2.5.
        Object value = applicationContext.getBeanFactory().createBean(type, AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR, false);
        return type.cast(value);
    }

}
