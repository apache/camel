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

import java.util.Collections;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Ownership;
import net.bytebuddy.description.modifier.SyntheticState;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import org.apache.camel.CamelContext;
import org.apache.camel.Handler;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
public class BeanInfoTest {

    public static class MyClass {
        @Handler
        public void myMethod() {
        }
    }

    public static class MyDerivedClass extends MyClass {
        @Override
        public void myMethod() {
        }
    }

    @Mock
    private CamelContext context;

    @Mock
    private Registry registry;

    private BeanComponent beanComponent;

    @BeforeEach
    public void setup() {
        beanComponent = new BeanComponent();
        lenient().when(context.getComponent("bean", BeanComponent.class)).thenReturn(beanComponent);
        lenient().when(context.getRegistry()).thenReturn(registry);
        lenient()
                .when(registry.findByType(ParameterMappingStrategy.class))
                .thenReturn(Collections.EMPTY_SET);
    }

    @Test
    public void testHandlerClass() throws Exception {
        BeanInfo info = new BeanInfo(
                context, MyClass.class.getMethod("myMethod"),
                ParameterMappingStrategyHelper.createParameterMappingStrategy(context),
                context.getComponent("bean", BeanComponent.class));
        assertTrue(info.hasAnyMethodHandlerAnnotation());
    }

    @Test
    public void testHandlerOnSyntheticProxy() throws Exception {
        Object proxy = new ByteBuddy()
                .subclass(MyClass.class)
                .modifiers(SyntheticState.SYNTHETIC, Visibility.PUBLIC, Ownership.STATIC)
                .method(named("myMethod"))
                .intercept(MethodDelegation.to(
                        new Object() {
                            @RuntimeType
                            public void intercept() throws Exception {
                            }
                        }))
                .make()
                .load(getClass().getClassLoader())
                .getLoaded()
                .getDeclaredConstructor()
                .newInstance();
        BeanInfo info = new BeanInfo(
                context, proxy.getClass().getMethod("myMethod"),
                ParameterMappingStrategyHelper.createParameterMappingStrategy(context),
                context.getComponent("bean", BeanComponent.class));
        assertTrue(info.hasAnyMethodHandlerAnnotation());
    }

    @Test
    public void testHandlerOnDerived() throws Exception {
        BeanInfo info = new BeanInfo(
                context, MyDerivedClass.class.getMethod("myMethod"),
                ParameterMappingStrategyHelper.createParameterMappingStrategy(context),
                context.getComponent("bean", BeanComponent.class));
        assertFalse(info.hasAnyMethodHandlerAnnotation());
    }

}
