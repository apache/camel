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
package org.apache.camel.component.cdi.internal;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.util.ObjectHelper;

import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.inject.Inject;
import java.lang.reflect.Type;

/**
 * Injects endpoints into beans
 */
public class EndpointInjector {
    @Inject
    private CamelContext camelContext;


/*
    @Produces
    public Endpoint createEndpoint(InjectionPoint point) {
        return createEndpoint(point, Endpoint.class);
    }

    // Note that there does not appear to be a way in CDI to say we can inject
    // all types from Endpoint onwards so lets make it easy to also inject mock endpoints too
    @Produces
    protected MockEndpoint createMockEndpoint(InjectionPoint point) {
        return createEndpoint(point, MockEndpoint.class);
    }
*/

    @Produces
    public Endpoint createEndpoint(InjectionPoint point) {
        Class<? extends Endpoint> endpointType = Endpoint.class;
        Type pointType = point.getType();
        if (pointType instanceof Class<?>) {
            endpointType = (Class<? extends Endpoint>) pointType;
        }

        EndpointInject annotation = point.getAnnotated().getAnnotation(EndpointInject.class);
        if (annotation != null) {
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
        }
        return null;
    }
}
