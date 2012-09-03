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
package org.apache.camel.component.cdi.internal;

import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.Annotated;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.inject.Inject;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.cdi.Mock;
import org.apache.camel.cdi.Uri;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.CamelPostProcessorHelper;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.deltaspike.core.api.provider.BeanProvider;

/**
 * produces {@link Endpoint} and {@link org.apache.camel.ProducerTemplate} instances for injection into beans
 */
public class CamelFactory {
    @Inject
    private CamelContext camelContext;

    @Produces
    @Mock
    public MockEndpoint createMockEndpoint(InjectionPoint point) {
        String uri = "";
        String ref = "";
        EndpointInject annotation = point.getAnnotated().getAnnotation(EndpointInject.class);
        if (annotation != null) {
            uri = annotation.uri();
            ref = annotation.ref();
        }
        if (ObjectHelper.isEmpty(ref)) {
            ref = point.getMember().getName();
        }
        if (ObjectHelper.isEmpty(uri)) {
            uri = "mock:" + ref;
        }
        return CamelContextHelper.getMandatoryEndpoint(camelContext, uri, MockEndpoint.class);
    }

    @SuppressWarnings("unchecked")
    @Produces
    public Endpoint createEndpoint(InjectionPoint point, BeanManager beanManager) {
        Annotated annotated = point.getAnnotated();
        Uri uri = annotated.getAnnotation(Uri.class);
        if (uri != null) {
            return CamelContextHelper.getMandatoryEndpoint(camelContext, uri.value());
        }
        EndpointInject annotation = annotated.getAnnotation(EndpointInject.class);
        ObjectHelper.notNull(annotation, "Must be annotated with @EndpointInject");
        return getEndpoint(point, annotation.uri(), annotation.ref(), annotation.property());
    }

    @Produces
    public ProducerTemplate createProducerTemplate(InjectionPoint point) {
        ProducerTemplate producerTemplate = camelContext.createProducerTemplate();
        Annotated annotated = point.getAnnotated();
        Uri uri = annotated.getAnnotation(Uri.class);
        Endpoint endpoint = null;
        if (uri != null) {
            endpoint = CamelContextHelper.getMandatoryEndpoint(camelContext, uri.value());
        } else {
            Produce annotation = annotated.getAnnotation(Produce.class);
            if (annotation != null) {
                endpoint = getEndpoint(point, annotation.uri(), annotation.ref(), annotation.property());
            }
        }
        if (endpoint != null) {
            producerTemplate.setDefaultEndpoint(endpoint);
        }
        return producerTemplate;
    }

    protected Endpoint getEndpoint(InjectionPoint point, String uri, String ref, String property) {
        String injectName = getInjectionPointName(point);
        if (ObjectHelper.isEmpty(property)) {
            return resolveEndpoint(uri, ref, injectName);
        } else {
            throw new UnsupportedOperationException();
/*
            TODO this code won't work in CDI as we've not yet created the bean being injected yet
            so cannot evaluate the property yet!

            CamelPostProcessorHelper helper = new CamelPostProcessorHelper(camelContext);
            Bean<?> bean = point.getBean();
            Class<?> beanClass = bean.getBeanClass();
            Object instance = BeanProvider.getContextualReference((Class)beanClass, bean);
            return helper.getEndpointInjection(instance, uri, ref, property, injectName, true);
*/
        }
    }

    private Endpoint resolveEndpoint(String uri, String ref, String injectionName) {
        return CamelContextHelper.getEndpointInjection(camelContext, uri, ref, injectionName, true);
    }

    private String getInjectionPointName(InjectionPoint point) {
        // TODO is there a better name?
        return point.toString();
    }
}
