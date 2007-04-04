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
package org.apache.camel.spring;

import org.apache.camel.spi.Injector;
import org.apache.camel.impl.ReflectionInjector;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.ApplicationContext;

import java.util.Arrays;

/**
 * A Spring implementation of {@link Injector} allowing Spring to be used to inject newly constructed type converters
 *
 * @version $Revision$
 */
public class SpringInjector extends ReflectionInjector {
    private static final transient Log log = LogFactory.getLog(SpringInjector.class);
    private final ApplicationContext applicationContext;

    public SpringInjector(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public Object newInstance(Class type) {
        String[] names = applicationContext.getBeanNamesForType(type, true, true);
        if (names != null) {
            if (names.length == 1) {
                // lets instantiate the single bean
                return applicationContext.getBean(names[0]);
            }
            else if (names.length > 1) {
                log.warn("Too many beans of type: " + type.getName() + " available: " + Arrays.asList(names) + " so ignoring Spring configuration");
            }
        }

        // lets instantiate the bean
        Object answer = super.newInstance(type);

        // TODO now lets inject spring...
        return answer;
    }
}
