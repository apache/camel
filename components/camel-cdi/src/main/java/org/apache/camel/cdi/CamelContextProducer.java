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
package org.apache.camel.cdi;

import java.lang.annotation.Annotation;
import java.util.Set;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.InjectionException;
import javax.enterprise.inject.spi.Annotated;
import javax.enterprise.inject.spi.AnnotatedField;
import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Producer;
import javax.inject.Named;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.DefaultCamelContextNameStrategy;
import org.apache.camel.impl.engine.ExplicitCamelContextNameStrategy;
import org.apache.camel.spi.CamelContextNameStrategy;
import org.apache.camel.support.DefaultRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.beans.Introspector.decapitalize;
import static java.util.stream.Collectors.toSet;
import static org.apache.camel.RuntimeCamelException.wrapRuntimeCamelException;
import static org.apache.camel.cdi.AnyLiteral.ANY;
import static org.apache.camel.cdi.CdiSpiHelper.createCamelContextWithTCCL;
import static org.apache.camel.cdi.CdiSpiHelper.getRawType;
import static org.apache.camel.cdi.CdiSpiHelper.isAnnotationType;
import static org.apache.camel.cdi.DefaultLiteral.DEFAULT;

final class CamelContextProducer<T extends CamelContext> extends DelegateProducer<T> {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Annotated annotated;

    private final BeanManager manager;

    private final CdiCamelExtension extension;

    CamelContextProducer(Producer<T> delegate, Annotated annotated, BeanManager manager, CdiCamelExtension extension) {
        super(delegate);
        this.annotated = annotated;
        this.manager = manager;
        this.extension = extension;
    }

    @Override
    public T produce(CreationalContext<T> ctx) {
        T context = createCamelContextWithTCCL(() -> super.produce(ctx), annotated);

        // Do not override the name if it's been already set (in the bean constructor for example)
        if (context.getNameStrategy() instanceof DefaultCamelContextNameStrategy) {
            context.setNameStrategy(nameStrategy(annotated));
        }

        // Add bean registry and Camel injector
        if (context instanceof DefaultCamelContext) {
            DefaultCamelContext adapted = context.adapt(DefaultCamelContext.class);
            adapted.setRegistry(new DefaultRegistry(new CdiCamelBeanRepository(manager)));
            adapted.setInjector(new CdiCamelInjector(context.getInjector(), manager));
        } else {
            // Fail fast for the time being to avoid side effects by the time these two methods get declared on the CamelContext interface
            throw new InjectionException("Camel CDI requires Camel context [" + context.getName() + "] to be a subtype of DefaultCamelContext");
        }

        // Add event notifier if at least one observer is present
        Set<Annotation> qualifiers = annotated.getAnnotations().stream()
            .filter(isAnnotationType(Named.class).negate()
                .and(q -> manager.isQualifier(q.annotationType())))
            .collect(toSet());
        qualifiers.add(ANY);
        if (qualifiers.size() == 1) {
            qualifiers.add(DEFAULT);
        }
        qualifiers.retainAll(extension.getObserverEvents());
        if (!qualifiers.isEmpty()) {
            context.getManagementStrategy().addEventNotifier(new CdiEventNotifier(manager, qualifiers));
        }

        return context;
    }

    @Override
    public void dispose(T context) {
        super.dispose(context);

        if (!context.getStatus().isStopped()) {
            logger.info("Camel CDI is stopping Camel context [{}]", context.getName());
            try {
                context.stop();
            } catch (Exception cause) {
                throw wrapRuntimeCamelException(cause);
            }
        }
    }

    private static CamelContextNameStrategy nameStrategy(Annotated annotated) {
        if (annotated.isAnnotationPresent(Named.class)) {
            // TODO: support stereotype with empty @Named annotation
            String name = annotated.getAnnotation(Named.class).value();
            if (name.isEmpty()) {
                if (annotated instanceof AnnotatedField) {
                    name = ((AnnotatedField) annotated).getJavaMember().getName();
                } else if (annotated instanceof AnnotatedMethod) {
                    name = ((AnnotatedMethod) annotated).getJavaMember().getName();
                    if (name.startsWith("get")) {
                        name = decapitalize(name.substring(3));
                    }
                } else {
                    name = decapitalize(getRawType(annotated.getBaseType()).getSimpleName());
                }
            }
            return new ExplicitCamelContextNameStrategy(name);
        } else {
            // Use a specific naming strategy for Camel CDI as the default one increments the suffix for each CDI proxy created
            return new CdiCamelContextNameStrategy();
        }
    }
}
