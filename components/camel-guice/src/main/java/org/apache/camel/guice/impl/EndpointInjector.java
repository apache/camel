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

import com.google.inject.Inject;
import com.google.inject.TypeLiteral;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.impl.CamelPostProcessorHelper;
import org.apache.camel.util.ObjectHelper;
import org.guiceyfruit.support.AnnotationMemberProvider;

/**
 * Injects values into the {@link EndpointInject} injection point
 *
 * @version $Revision$
 */
public class EndpointInjector extends CamelPostProcessorHelper
    implements AnnotationMemberProvider<EndpointInject> {

  @Inject
  public EndpointInjector(CamelContext camelContext) {
    super(camelContext);
  }

  public Object provide(EndpointInject inject, TypeLiteral<?> typeLiteral, Field field) {
    Class<?> type = field.getType();
    String injectionPointName = field.getName();
    String uri = inject.uri();
    String endpointRef = inject.name();
    return getInjectionValue(type, uri, endpointRef, injectionPointName);
  }

  public Object provide(EndpointInject inject, TypeLiteral<?> typeLiteral, Method method,
      Class<?> aClass, int index) {

    Class<?>[] parameterTypes = method.getParameterTypes();
    Class<?> type = parameterTypes[index];
    String injectionPointName = ObjectHelper.getPropertyName(method);
    String endpointRef = inject.name();
    String uri = inject.uri();

    return getInjectionValue(type, uri, endpointRef, injectionPointName);
  }

  public boolean isNullParameterAllowed(EndpointInject endpointInject, Method method,
      Class<?> aClass, int i) {
    return false;
  }
}
