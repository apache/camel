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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Ownership;
import net.bytebuddy.description.modifier.SyntheticState;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Handler;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;

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

    @Test
    public void testVoidMethod() throws NoSuchMethodException, SecurityException {
        Method method = MyClass.class.getMethod("myMethod");
        MethodInfo info = new MethodInfo(context, MyClass.class, method, new ArrayList<>(), new ArrayList<>(), false, false);
        assertTrue(info.isReturnTypeVoid());
    }

    @Test
    public void testExchangeClass() {
        BeanInfo info = new BeanInfo(context, DefaultExchange.class);
        assertFalse(info.hasAnyMethodHandlerAnnotation());

        BeanInfo info2 = new BeanInfo(context, DefaultExchange.class);
        assertFalse(info2.hasAnyMethodHandlerAnnotation());
    }

    @Test
    public void testChooseExchangeMethod() {
        DefaultExchange exchange = new DefaultExchange(context);
        BeanInfo info = new BeanInfo(context, MyClassTwo.class);
        MethodInfo mi = info.chooseMethod(null, exchange, null);
        assertNotNull(mi);
        assertTrue(mi.hasCustomAnnotation()); // not really correct; but backwards compatible
        assertFalse(mi.hasHandlerAnnotation());
        assertTrue(mi.hasParameters());
        assertFalse(mi.hasBodyParameter());
        assertEquals("myExchangeMethod", mi.getMethod().getName());
        assertEquals(Exchange.class, mi.getMethod().getParameterTypes()[0]);
    }

    private Object buildProxyObject() {
        try {
            return new ByteBuddy()
                    .subclass(MyClass.class)
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

    public static class MyClassTwo {

        public void myMethod() {
        }

        public String myOtherMethod() {
            return "";
        }

        public String myExchangeMethod(Exchange exchange) {
            return exchange.getExchangeId();
        }

        public void myBooleanMethod(boolean fool) {
            // noop
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

}
