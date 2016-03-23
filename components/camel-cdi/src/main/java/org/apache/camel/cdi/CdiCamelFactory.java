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

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import javax.enterprise.event.Event;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.InjectionException;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.Typed;
import javax.enterprise.inject.UnsatisfiedResolutionException;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.util.TypeLiteral;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.TypeConverter;
import org.apache.camel.component.mock.MockEndpoint;

final class CdiCamelFactory {

    @Produces
    private static TypeConverter typeConverter(InjectionPoint ip, @Any Instance<CamelContext> instance, CdiCamelExtension extension) {
        return selectContext(ip, instance, extension).getTypeConverter();
    }

    @Uri("")
    @Produces
    // Qualifiers are dynamically added in CdiCamelExtension
    private static ProducerTemplate producerTemplate(InjectionPoint ip, @Any Instance<CamelContext> instance, CdiCamelExtension extension) {
        Uri uri = CdiSpiHelper.getQualifierByType(ip, Uri.class);
        try {
            CamelContext context = uri.context().isEmpty() ? selectContext(ip, instance, extension) : selectContext(uri.context(), instance);
            ProducerTemplate producerTemplate = context.createProducerTemplate();
            // FIXME: avoid NPE caused by missing @Uri qualifier when injection point is @ContextName qualified
            Endpoint endpoint = context.getEndpoint(uri.value(), Endpoint.class);
            producerTemplate.setDefaultEndpoint(endpoint);
            return producerTemplate;
        } catch (Exception cause) {
            throw new InjectionException("Error injecting producer template annotated with " + uri + " into " + ip, cause);
        }
    }

    @Produces
    @Typed(MockEndpoint.class)
    // Qualifiers are dynamically added in CdiCamelExtension
    private static MockEndpoint mockEndpointFromMember(InjectionPoint ip, @Any Instance<CamelContext> instance, CdiCamelExtension extension) {
        String uri = "mock:" + ip.getMember().getName();
        try {
            return selectContext(ip, instance, extension).getEndpoint(uri, MockEndpoint.class);
        } catch (Exception cause) {
            throw new InjectionException("Error injecting mock endpoint into " + ip, cause);
        }
    }

    @Uri("")
    @Produces
    @Typed(MockEndpoint.class)
    // Qualifiers are dynamically added in CdiCamelExtension
    private static MockEndpoint mockEndpointFromUri(InjectionPoint ip, @Any Instance<CamelContext> instance, CdiCamelExtension extension) {
        Uri uri = CdiSpiHelper.getQualifierByType(ip, Uri.class);
        try {
            CamelContext context = uri.context().isEmpty() ? selectContext(ip, instance, extension) : selectContext(uri.context(), instance);
            return context.getEndpoint(uri.value(), MockEndpoint.class);
        } catch (Exception cause) {
            throw new InjectionException("Error injecting mock endpoint annotated with " + uri
                + " into " + ip, cause);
        }
    }

    // Maintained for backward compatibility reason though this is redundant with @Uri
    // see https://issues.apache.org/jira/browse/CAMEL-5553?focusedCommentId=13445936&page=com.atlassian.jira.plugin.system.issuetabpanels:comment-tabpanel#comment-13445936
    @Mock("")
    @Produces
    @Typed(MockEndpoint.class)
    // Qualifiers are dynamically added in CdiCamelExtension
    private static MockEndpoint createMockEndpoint(InjectionPoint ip, @Any Instance<CamelContext> instance, CdiCamelExtension extension) {
        Mock mock = CdiSpiHelper.getQualifierByType(ip, Mock.class);
        try {
            CamelContext context = mock.context().isEmpty() ? selectContext(ip, instance, extension) : selectContext(mock.context(), instance);
            return context.getEndpoint(mock.value(), MockEndpoint.class);
        } catch (Exception cause) {
            throw new InjectionException("Error injecting mock endpoint annotated with " + mock + " into " + ip, cause);
        }
    }

    @Uri("")
    @Produces
    // Qualifiers are dynamically added in CdiCamelExtension
    private static Endpoint endpoint(InjectionPoint ip, @Any Instance<CamelContext> instance, CdiCamelExtension extension) {
        Uri uri = CdiSpiHelper.getQualifierByType(ip, Uri.class);
        try {
            CamelContext context = uri.context().isEmpty() ? selectContext(ip, instance, extension) : selectContext(uri.context(), instance);
            return context.getEndpoint(uri.value(), Endpoint.class);
        } catch (Exception cause) {
            throw new InjectionException("Error injecting endpoint annotated with " + uri + " into " + ip, cause);
        }
    }

    @Produces
    @SuppressWarnings("unchecked")
    // Qualifiers are dynamically added in CdiCamelExtension
    private static <T> CdiEventEndpoint<T> cdiEventEndpoint(InjectionPoint ip, @Any Instance<CamelContext> instance, CdiCamelExtension extension, @Any Event<Object> event) throws Exception {
        CamelContext context = selectContext(ip, instance, extension);
        Type type = Object.class;
        if (ip.getType() instanceof ParameterizedType) {
            type = ((ParameterizedType) ip.getType()).getActualTypeArguments()[0];
        }
        String uri = eventEndpointUri(type, ip.getQualifiers());
        if (context.hasEndpoint(uri) == null) {
            // FIXME: to be replaced once event firing with dynamic parameterized type is properly supported (see https://issues.jboss.org/browse/CDI-516)
            TypeLiteral<T> literal = new TypeLiteral<T>() {
            };
            for (Field field : TypeLiteral.class.getDeclaredFields()) {
                if (field.getType().equals(Type.class)) {
                    field.setAccessible(true);
                    field.set(literal, type);
                    break;
                }
            }
            context.addEndpoint(uri,
                new CdiEventEndpoint<>(
                    event.select(literal, ip.getQualifiers().toArray(new Annotation[ip.getQualifiers().size()])), uri, context, (ForwardingObserverMethod<T>) extension.getObserverMethod(ip)));
        }
        return context.getEndpoint(uri, CdiEventEndpoint.class);
    }

    private static <T extends CamelContext> T selectContext(String name, Instance<T> instance) {
        for (T context : instance) {
            if (name.equals(context.getName())) {
                return context;
            }
        }
        throw new UnsatisfiedResolutionException("No Camel context with name [" + name + "] is deployed!");
    }

    private static <T extends CamelContext> T selectContext(InjectionPoint ip, Instance<T> instance, CdiCamelExtension extension) {
        Collection<Annotation> qualifiers = new HashSet<>(ip.getQualifiers());
        qualifiers.retainAll(extension.getContextQualifiers());
        if (qualifiers.isEmpty() && !instance.select(DefaultLiteral.INSTANCE).isUnsatisfied()) {
            return instance.select(DefaultLiteral.INSTANCE).get();
        }
        return instance.select(qualifiers.toArray(new Annotation[qualifiers.size()])).get();
    }

    private static String eventEndpointUri(Type type, Set<Annotation> qualifiers) {
        String uri = "cdi-event://" + authorityFromType(type);
        StringBuilder parameters = new StringBuilder();
        Iterator<Annotation> it = qualifiers.iterator();
        while (it.hasNext()) {
            parameters.append(it.next().annotationType().getCanonicalName());
            if (it.hasNext()) {
                parameters.append("%2C");
            }
        }
        if (parameters.length() > 0) {
            uri += "?qualifiers=" + parameters.toString();
        }
        return uri;
    }

    private static String authorityFromType(Type type) {
        if (type instanceof Class) {
            return Class.class.cast(type).getName();
        }
        if (type instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) type;
            StringBuilder builder = new StringBuilder(authorityFromType(pt.getRawType()));
            Iterator<Type> it = Arrays.asList(pt.getActualTypeArguments()).iterator();
            builder.append("%3C");
            while (it.hasNext()) {
                builder.append(authorityFromType(it.next()));
                if (it.hasNext()) {
                    builder.append("%2C");
                }
            }
            builder.append("%3E");
            return builder.toString();
        }
        if (type instanceof GenericArrayType) {
            GenericArrayType arrayType = (GenericArrayType) type;
            return authorityFromType(arrayType.getGenericComponentType()) + "%5B%5D";
        }
        throw new IllegalArgumentException("Cannot create URI authority for event type [" + type + "]");
    }
}
