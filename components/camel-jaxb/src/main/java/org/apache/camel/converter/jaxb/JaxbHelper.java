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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JaxbHelper {

    private static final Logger LOG = LoggerFactory.getLogger(JaxbHelper.class);

    private JaxbHelper() {
    }

    public static <T> Method getJaxbElementFactoryMethod(CamelContext camelContext, Class<T> type) {
        Method factoryMethod = null;
        try {
            for (Method m : getObjectFactory(camelContext, type).getMethods()) {
                final XmlElementDecl a = m.getAnnotation(XmlElementDecl.class);
                if (a == null) {
                    continue;
                }
                final Class<?>[] parameters = m.getParameterTypes();
                if (parameters.length == 1 && parameters[0].isAssignableFrom(type)) {
                    if (factoryMethod != null) {
                        throw new IllegalStateException("There are several possible XML schema mappings for class " + type.getName());
                    } else {
                        factoryMethod = m;
                    }
                }
            }
        } catch (ClassNotFoundException e) {
            LOG.debug(e.getMessage(), e);
        }
        return factoryMethod;
    }

    public static <T> Class getObjectFactory(CamelContext camelContext, Class<T> type) throws ClassNotFoundException {
        Class<?> c = null;
        if (type.getPackage() != null) {
            String objectFactoryClassName = type.getPackage().getName() + ".ObjectFactory";
            c = camelContext.getClassResolver().resolveClass(objectFactoryClassName);
        }
        if (c == null) {
            throw new ClassNotFoundException(String.format("ObjectFactory for type %s was not found", type.getName()));
        } else {
            return c;
        }
    }

}
