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
package org.apache.camel.main.support;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.GeneratedPropertyConfigurer;
import org.apache.camel.support.component.PropertyConfigurerSupport;

public class MyDummyComponentConfigurer extends PropertyConfigurerSupport implements GeneratedPropertyConfigurer {
    @Override
    public boolean configure(CamelContext camelContext, Object component, String name, Object value, boolean ignoreCase) {
        if (ignoreCase) {
            return doConfigureIgnoreCase(camelContext, component, name, value);
        } else {
            return doConfigure(camelContext, component, name, value);
        }
    }

    private static boolean doConfigure(CamelContext camelContext, Object component, String name, Object value) {
        switch (name) {
            case "configuration":
                ((MyDummyComponent) component).setConfiguration(property(camelContext, MyDummyConfiguration.class, value));
                return true;
            default:
                return false;
        }
    }

    private static boolean doConfigureIgnoreCase(CamelContext camelContext, Object component, String name, Object value) {
        switch (name.toLowerCase()) {
            case "configuration":
                ((MyDummyComponent) component).setConfiguration(property(camelContext, MyDummyConfiguration.class, value));
                return true;
            default:
                return false;
        }
    }
}
