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

import java.lang.reflect.Type;

import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.inject.Inject;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.component.cdi.Mock;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * Injects endpoints into beans
 */
public class EndpointInjector {
    @Inject
    private CamelContext camelContext;


    @Produces
    @Mock
    protected MockEndpoint createMockEndpoint(InjectionPoint point) {
        String url = "";
        String name = "";
        EndpointInject annotation = point.getAnnotated().getAnnotation(EndpointInject.class);
        if (annotation != null) {
            url = annotation.uri();
            name = annotation.ref();
        }
        if (ObjectHelper.isEmpty(name)) {
            name = point.getMember().getName();
        }
        if (ObjectHelper.isEmpty(url)) {
            url = "mock:" + name;
        }
        return camelContext.getEndpoint(url, MockEndpoint.class);
    }

    @Produces
    public Endpoint createEndpoint(InjectionPoint point) {
        Class<? extends Endpoint> endpointType = Endpoint.class;
        Type pointType = point.getType();
        if (pointType instanceof Class<?>) {
            endpointType = (Class<? extends Endpoint>) pointType;
        }
        EndpointInject annotation = point.getAnnotated().getAnnotation(EndpointInject.class);
        if (annotation != null) {
            String uri = annotation.uri();
            if (ObjectHelper.isNotEmpty(uri)) {
                return camelContext.getEndpoint(uri, endpointType);
            }
            String ref = annotation.ref();
            if (ObjectHelper.isNotEmpty(ref)) {
                return camelContext.getEndpoint("ref:" + ref, endpointType);
            }
        }
        throw new IllegalArgumentException("Could not create instance of Endpoint for the given injection point " + point);
    }
}
