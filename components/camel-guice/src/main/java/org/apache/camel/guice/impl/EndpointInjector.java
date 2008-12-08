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
package org.apache.camel.guice.impl;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import com.google.common.base.Objects;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.spi.AnnotationProviderFactory;
import com.google.inject.spi.InjectionAnnotation;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.impl.CamelPostProcessorHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * Injects values into the {@link EndpointInject} injection point
 *
 * @version $Revision$
 */
@InjectionAnnotation(EndpointInject.class)
public class EndpointInjector extends CamelPostProcessorHelper implements AnnotationProviderFactory {

    @Inject
    public EndpointInjector(CamelContext camelContext) {
        super(camelContext);
    }

    public Provider createProvider(final AnnotatedElement member) {
        final EndpointInject inject = member.getAnnotation(EndpointInject.class);
        Objects.nonNull(inject, "@EndpointInject is not present!");


        final Class<?> type;
        final String injectionPointName;
        final String endpointRef = inject.name();
        final String uri = inject.uri();

        if (member instanceof Field) {
            Field field = (Field) member;
            type = field.getType();
            injectionPointName = field.getName();
        } else if (member instanceof Method) {
            Method method = (Method) member;
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length == 1) {
                type = parameterTypes[0];
                injectionPointName = ObjectHelper.getPropertyName(method);
            } else {
                throw new UnsupportedOperationException("Only a single method parameter value supported for @EndpointInject on " + method);
            }
        } else {
            throw new UnsupportedOperationException("Annotated element " + member + " not supported");
        }

        return new Provider() {
            public Object get() {
                return getInjectionValue(type, uri, endpointRef, injectionPointName);
            }
        };
    }
}
