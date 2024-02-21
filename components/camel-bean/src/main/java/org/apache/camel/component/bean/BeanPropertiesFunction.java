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
package org.apache.camel.component.bean;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.PropertiesFunction;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.util.StringHelper;

@org.apache.camel.spi.annotations.PropertiesFunction("bean")
public class BeanPropertiesFunction implements PropertiesFunction, CamelContextAware {
    private CamelContext camelContext;

    @Override
    public String getName() {
        return "bean";
    }

    @Override
    public String apply(String remainder) {
        if (StringHelper.countChar(remainder, '.') != 1 || remainder.startsWith(".") || remainder.endsWith(".")) {
            throw new IllegalArgumentException("BeanName and methodName should be separated by a dot.");
        }
        String[] beanNameAndMethodName = remainder.split("\\.");
        String beanName = beanNameAndMethodName[0];
        String methodName = beanNameAndMethodName[1];

        Object bean = CamelContextHelper.mandatoryLookup(camelContext, beanName);

        String answer = "";
        try {
            answer += camelContext.getTypeConverter().convertTo(String.class, ObjectHelper.invokeMethodSafe(methodName, bean));
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
        return answer;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

}
