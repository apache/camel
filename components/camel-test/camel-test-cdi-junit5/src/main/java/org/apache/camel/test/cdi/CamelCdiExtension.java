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
package org.apache.camel.test.cdi;

import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;

import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestInstanceFactory;
import org.junit.jupiter.api.extension.TestInstanceFactoryContext;

import static org.junit.jupiter.api.extension.ExtensionContext.Namespace.create;

/**
 * The JUnit 5 extension of Camel CDI, this extension allows to inject any existing beans in the test class but also the
 * Camel specific beans directly as parameter of the test methods. The non Camel specific beans cannot be injected as
 * parameter to avoid conflicts with the parameters of JUnit 5 or other extensions.
 * 
 * <pre>
 * {@code
 *   &#64;ExtendWith(CamelCdiExtension.class)
 *   public class CamelTest {
 *    ...
 *   }
 * }
 * </pre>
 *
 * <p>
 * Additional alternatives, bean types or packages to scan that are specific to the test class can be provided thanks to
 * the annotation {@link Beans}.
 * 
 * <pre>
 * {@code
 *   &#64;ExtendWith(CamelCdiExtension.class)
 *   &#64;Beans(alternatives = AlternativeBean.class, classes = TestRoute.class)
 *   public class CamelTest {
 *     ...
 *   }
 * }
 * </pre>
 * <p>
 * The extension is also able to automatically detect beans of type {@code AdviceWithRouteBuilder} annotated with
 * {@link AdviceRoute}, instantiate them and <i>advice</i> the referred routes while taking care of delaying the
 * start-up of the Camel context to ensure that it works as expected.
 *
 * <pre>
 * <code>
 *
 * &#64;AdviceRoute("route")
 * public class ExternalTestBuilder extends AdviceWithRouteBuilder {
 *
 *     &#64;Override
 *     public void configure() throws Exception {
 *         weaveByToUri("direct:out").replace().to("mock:test");
 *     }
 * }
 * </code>
 * </pre>
 *
 * @see AdviceRoute
 * @see Beans
 */
public final class CamelCdiExtension implements Extension, TestInstanceFactory, ParameterResolver {

    /**
     * The namespace used to store the state of the test.
     */
    private static final ExtensionContext.Namespace NAMESPACE = create(CamelCdiExtension.class);
    /**
     * The name of key used to store the Camel CDI deployment.
     */
    private static final String CAMEL_CDI_DEPLOYMENT = "camel-cdi-deployment";

    @Override
    public Object createTestInstance(TestInstanceFactoryContext factoryContext, ExtensionContext context) {
        final BeanManager manager = getCamelCdiDeployment(context).getOrComputeIfAbsent(
                CAMEL_CDI_DEPLOYMENT, k -> new CamelCdiDeployment(context.getRequiredTestClass()), CamelCdiDeployment.class)
                .beanManager();
        final Bean<?> bean = manager.getBeans(factoryContext.getTestClass(), AnyLiteral.INSTANCE).iterator().next();
        // TODO: manage lifecycle of @Dependent beans
        return manager.getReference(bean, bean.getBeanClass(), manager.createCreationalContext(bean));
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        // Support only parameter whose type is in the camel package to be able to support other type of parameters
        // needed for JUnit 5 itself or other extensions.
        return parameterContext.getParameter().getType().getName().startsWith("org.apache.camel.");
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext context) {
        final BeanManager manager = getCamelCdiDeployment(context).get(CAMEL_CDI_DEPLOYMENT, CamelCdiDeployment.class)
                .beanManager();
        // TODO: use a proper CreationalContext...
        return manager.getInjectableReference(
                new FrameworkMethodInjectionPoint(
                        context.getRequiredTestMethod(), parameterContext.getIndex(), manager),
                manager.createCreationalContext(null));
    }

    /**
     * @return the store in which the Camel CDI deployment of the current test class is stored
     */
    private ExtensionContext.Store getCamelCdiDeployment(ExtensionContext context) {
        return context.getStore(NAMESPACE);
    }
}
