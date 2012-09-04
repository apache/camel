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
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.inject.Inject;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.cdi.Mock;
import org.apache.camel.cdi.Uri;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * produces {@link Endpoint} and {@link org.apache.camel.ProducerTemplate} instances for injection into beans
 */
public class CamelFactory {
    @Inject
    private CamelContext camelContext;

    @Produces
    @Mock
    public MockEndpoint createMockEndpoint(InjectionPoint point) {
        Mock annotation = point.getAnnotated().getAnnotation(Mock.class);
        ObjectHelper.notNull(annotation, "Should be annotated with @Mock");
        String uri = annotation.value();
        if (ObjectHelper.isEmpty(uri)) {
            uri = "mock:" + point.getMember().getName();
        }
        return CamelContextHelper.getMandatoryEndpoint(camelContext, uri, MockEndpoint.class);
    }

    @Produces
    @Uri("")
    public Endpoint createEndpoint(InjectionPoint point, BeanManager beanManager) {
        Annotated annotated = point.getAnnotated();
        Uri uri = annotated.getAnnotation(Uri.class);
        ObjectHelper.notNull(uri, "Should be annotated with @Uri");
        return CamelContextHelper.getMandatoryEndpoint(camelContext, uri.value());
    }

    @Produces
    @Uri("")
    public ProducerTemplate createProducerTemplate(InjectionPoint point) {
        ProducerTemplate producerTemplate = camelContext.createProducerTemplate();
        Annotated annotated = point.getAnnotated();
        Uri uri = annotated.getAnnotation(Uri.class);
        ObjectHelper.notNull(uri, "Should be annotated with @Uri");
        Endpoint endpoint = CamelContextHelper.getMandatoryEndpoint(camelContext, uri.value());
        producerTemplate.setDefaultEndpoint(endpoint);
        return producerTemplate;
    }
}
