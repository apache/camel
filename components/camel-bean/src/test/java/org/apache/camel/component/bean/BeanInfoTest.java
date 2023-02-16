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
import org.apache.aries.proxy.UnableToProxyException;
import org.apache.aries.proxy.impl.AsmProxyManager;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Handler;
import org.apache.camel.Message;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class BeanInfoTest {

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
    public void testHandlerClass() {
        BeanInfo info = new BeanInfo(context, MyClass.class);
        assertTrue(info.hasAnyMethodHandlerAnnotation());
    }

    @Test
    public void testHandlerOnSyntheticProxy() {
        Object proxy = buildProxyObject();

        BeanInfo info = new BeanInfo(context, proxy.getClass());
        assertTrue(info.hasAnyMethodHandlerAnnotation());
    }

    @Test
    public void testHandlerOnSyntheticProxyWithInterface() {
        Object proxy = buildProxyObject(MyHandlerInterface.class);

        BeanInfo info = new BeanInfo(context, proxy.getClass());
        assertTrue(info.hasAnyMethodHandlerAnnotation());
    }

    @Test
    public void testInvocationOnSyntheticProxy() {
        Object proxy = buildProxyObject(MyDerivedClass.class);

        BeanInfo info = new BeanInfo(context, proxy.getClass());
        info.createInvocation(info, createMockExchange());
    }

    @Test
    public void testInvocationOnSyntheticProxyWithInterface() throws UnableToProxyException {
        Object proxy = new AsmProxyManager().createNewProxy(null, Collections.singleton(MyInterface.class), () -> null, null);

        BeanInfo info = new BeanInfo(context, proxy.getClass());
        info.createInvocation(info, createMockExchange());
    }

    @Test
    public void testHandlerOnDerived() {
        BeanInfo info = new BeanInfo(context, MyDerivedClass.class);
        assertFalse(info.hasAnyMethodHandlerAnnotation());
    }

    @Test
    public void testHandlerInFunctionalInterfaceWithLambda() {
        MyHandlerInterface mhi = (MyHandlerInterface) () -> null;

        BeanInfo info = new BeanInfo(context, mhi.getClass());
        assertTrue(info.hasAnyMethodHandlerAnnotation());
    }

    @Test
    public void testHandlerInFunctionalInterfaceWithMethodReference() {
        MyClass myClass = new MyClass();
        MyHandlerInterface mhi = (MyHandlerInterface) myClass::myOtherMethod;
        BeanInfo info = new BeanInfo(context, mhi.getClass());
        assertTrue(info.hasAnyMethodHandlerAnnotation());
    }

    @Test
    public void testHandlerInFunctionalInterfaceWithAnonymousInnerClass() {
        MyHandlerInterface mhi = new MyHandlerInterface() {
            @Override
            public String myMethod() {
                return "";
            }
        };

        BeanInfo info = new BeanInfo(context, mhi.getClass());
        assertTrue(info.hasAnyMethodHandlerAnnotation());
    }

    private Exchange createMockExchange() {
        Exchange exchange = mock(Exchange.class);
        Message message = mock(Message.class);
        when(exchange.getIn()).thenReturn(message);
        when(message.getHeader(eq(Exchange.BEAN_METHOD_NAME), eq(String.class))).thenReturn("myMethod");
        return exchange;
    }

    private Object buildProxyObject() {
        return buildProxyObject(MyClass.class);
    }

    private Object buildProxyObject(Class<?> clazz) {
        try {
            return new ByteBuddy()
                    .subclass(clazz)
                    .modifiers(SyntheticState.SYNTHETIC, Visibility.PUBLIC, Ownership.STATIC)
                    .method(named("myMethod"))
                    .intercept(MethodDelegation.to(
                            new Object() {
                                @RuntimeType
                                public void intercept() {
                                }
                            }))
                    .make()
                    .load(getClass().getClassLoader())
                    .getLoaded()
                    .getDeclaredConstructor()
                    .newInstance();

        } catch (Exception ignored) {
            return new Object();
        }
    }

    public static class MyClass {
        @Handler
        public void myMethod() {
        }

        public String myOtherMethod() {
            return "";
        }
    }

    public static class MyDerivedClass extends MyClass {
        @Override
        public void myMethod() {
        }
    }

    @FunctionalInterface
    public interface MyHandlerInterface {
        @Handler
        String myMethod();
    }

    public interface MyInterface {

        void myMethod();

    }

}
