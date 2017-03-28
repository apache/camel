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
package org.apache.camel.converter.jaxb;

import java.lang.reflect.Method;
import javax.xml.bind.annotation.XmlElementDecl;

import org.apache.camel.CamelContext;

public final class JaxbHelper {

    private JaxbHelper() {
    }

    public static <T> Method getJaxbElementFactoryMethod(CamelContext camelContext, Class<T> type) {
        if (camelContext == null) {
            return null;
        }

        // find the first method that has @XmlElementDecl with one parameter that matches the type
        Class factory = getObjectFactory(camelContext, type);
        if (factory != null) {
            for (Method m : factory.getMethods()) {
                final XmlElementDecl a = m.getAnnotation(XmlElementDecl.class);
                if (a == null) {
                    continue;
                }
                final Class<?>[] parameters = m.getParameterTypes();
                if (parameters.length == 1 && parameters[0].isAssignableFrom(type)) {
                    return m;
                }
            }
        }

        return null;
    }

    public static <T> Class getObjectFactory(CamelContext camelContext, Class<T> type) {
        if (camelContext == null) {
            return null;
        }

        if (type.getPackage() != null) {
            String objectFactoryClassName = type.getPackage().getName() + ".ObjectFactory";
            return camelContext.getClassResolver().resolveClass(objectFactoryClassName);
        }
        return null;
    }

}
