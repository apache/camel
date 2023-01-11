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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.annotation.Priority;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.spi.AfterDeploymentValidation;
import jakarta.enterprise.inject.spi.AnnotatedMethod;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanAttributes;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.enterprise.inject.spi.ProcessBeanAttributes;
import jakarta.enterprise.inject.spi.WithAnnotations;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.cdi.CdiCamelConfiguration;
import org.apache.camel.model.ModelCamelContext;

import static jakarta.interceptor.Interceptor.Priority.APPLICATION;

final class CamelCdiTestExtension implements Extension {

    private final Beans beans;
    /**
     * All the beans of type {@link AdviceWithRouteBuilder} that has been annotated with {@code @AdviceRoute}
     * corresponding to the builders that advice specific rules.
     */
    private final List<Bean<? extends AdviceWithRouteBuilder>> builderBeans = new CopyOnWriteArrayList<>();

    CamelCdiTestExtension(Beans beans) {
        this.beans = beans;
    }

    /**
     * Activates the alternatives declared with {@code @Beans} globally for the application.
     * <p/>
     * For every types and every methods of every types declared with {@link Beans#alternatives()}, the {@code Priority}
     * annotation is added so that the corresponding alternatives are selected globally for the entire application.
     *
     * @see Beans
     */
    <T> void alternatives(@Observes @WithAnnotations(Alternative.class) ProcessAnnotatedType<T> pat) {
        if (beans == null) {
            return;
        }
        AnnotatedType<T> type = pat.getAnnotatedType();

        if (!Arrays.asList(beans.alternatives()).contains(type.getJavaClass())) {
            // Only select globally the alternatives that are declared with @Beans
            return;
        }

        Set<AnnotatedMethod<? super T>> methods = new HashSet<>();
        for (AnnotatedMethod<? super T> method : type.getMethods()) {
            if (method.isAnnotationPresent(Alternative.class) && !method.isAnnotationPresent(Priority.class)) {
                methods.add(new AnnotatedMethodDecorator<>(method, PriorityLiteral.of(APPLICATION)));
            }
        }

        if (type.isAnnotationPresent(Alternative.class) && !type.isAnnotationPresent(Priority.class)) {
            pat.setAnnotatedType(new AnnotatedTypeDecorator<>(type, PriorityLiteral.of(APPLICATION), methods));
        } else if (!methods.isEmpty()) {
            pat.setAnnotatedType(new AnnotatedTypeDecorator<>(type, methods));
        }
    }

    /**
     * Collect all the beans of type {@link AdviceWithRouteBuilder} that has been annotated with {@code @AdviceRoute}.
     *
     * @param event the event to check in order to know if it corresponds to a bean of type
     *              {@link AdviceWithRouteBuilder} that should be added to the list of beans to manage.
     */
    @SuppressWarnings("unchecked")
    void collectAdviceWithRouteBuilder(@Observes ProcessBeanAttributes<? extends AdviceWithRouteBuilder> event) {
        if (event.getAnnotated().isAnnotationPresent(AdviceRoute.class)) {
            final BeanAttributes<? extends AdviceWithRouteBuilder> beanAttributes = event.getBeanAttributes();
            if (beanAttributes instanceof Bean) {
                builderBeans.add((Bean<? extends AdviceWithRouteBuilder>) beanAttributes);
                event.veto();
            }
        }
    }

    /**
     * Disables the auto start of the Camel context if at least one bean of type {@link AdviceWithRouteBuilder} that has
     * been annotated with {@code @AdviceRoute} could be found.
     *
     * @param configuration the configuration giving the opportunity to disable the auto start of the Camel context if
     *                      needed.
     */
    void disableAutoStartContext(@Observes @Priority(Integer.MAX_VALUE) CdiCamelConfiguration configuration) {
        if (builderBeans.isEmpty()) {
            return;
        }
        configuration.autoStartContexts(false);
    }

    /**
     * Advises all the target routes with all the beans of type {@link AdviceWithRouteBuilder} that have been selected
     * and starts the Camel context.
     *
     * @param  event     the event allowing to trigger the advices after the original routes are created and ready to be
     *                   started.
     * @param  manager   the bean manager used to instantiate the selected beans of type {@link AdviceWithRouteBuilder}.
     * @throws Exception if an error occurs while advising the target routes.
     */
    void advice(@Observes @Priority(Integer.MAX_VALUE) AfterDeploymentValidation event, BeanManager manager)
            throws Exception {
        if (builderBeans.isEmpty()) {
            return;
        }

        for (Bean<?> beanContext : manager.getBeans(CamelContext.class, Any.Literal.INSTANCE)) {
            final ModelCamelContext context = (ModelCamelContext) manager.getReference(beanContext, ModelCamelContext.class,
                    manager.createCreationalContext(beanContext));
            for (Bean<?> beanBuilder : builderBeans) {
                AdviceWith.adviceWith(
                        context.getRouteDefinition(beanBuilder.getBeanClass().getAnnotation(AdviceRoute.class).value()),
                        context,
                        (AdviceWithRouteBuilder) manager.getReference(beanBuilder,
                                AdviceWithRouteBuilder.class, manager.createCreationalContext(beanBuilder)));
            }
            context.start();
        }
    }
}
