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
package org.apache.camel.cdi;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.event.Event;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.InjectionTarget;
import javax.enterprise.util.TypeLiteral;
import javax.inject.Inject;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;

/**
 * A Camel {@link Endpoint} that bridges the CDI events facility with Camel routes so that CDI events
 * can be seamlessly observed / consumed (respectively produced / fired) from Camel consumers (respectively by Camel producers).<p>
 *
 * The {@code CdiEventEndpoint<T>} bean can be used to observe / consume CDI events whose event type is {@code T}, for example:
 * <pre><code>
 * {@literal @}Inject
 *  CdiEventEndpoint{@literal <}String{@literal >} cdiEventEndpoint;
 *
 *  from(cdiEventEndpoint).log("CDI event received: ${body}");
 * </code></pre>
 *
 * Conversely, the {@code CdiEventEndpoint<T>} bean can be used to produce / fire CDI events whose event type is {@code T}, for example:
 * <pre><code>
 * {@literal @}Inject
 *  CdiEventEndpoint{@literal <}String{@literal >} cdiEventEndpoint;
 *
 *  from("direct:event").to(cdiEventEndpoint).log("CDI event sent: ${body}");
 * </code></pre>
 *
 * The type variable {@code T}, respectively the qualifiers, of a particular {@code CdiEventEndpoint<T>} injection point
 * are automatically translated into the parameterized <i>event type</i>, respectively into the <i>event qualifiers</i>, e.g.:
 * <pre><code>
 * {@literal @}Inject
 * {@literal @}FooQualifier
 *  CdiEventEndpoint{@literal <}List{@literal <}String{@literal >}{@literal >} cdiEventEndpoint;
 *
 *  from("direct:event").to(cdiEventEndpoint);
 *
 *  void observeCdiEvents({@literal @}Observes {@literal @}FooQualifier List{@literal <}String{@literal >} event) {
 *      logger.info("CDI event: {}", event);
 *  }
 * </code></pre>
 *
 * When multiple Camel contexts exist in the CDI container, the {@code @ContextName} qualifier can be used
 * to qualify the {@code CdiEventEndpoint<T>} injection points, e.g.:
 * <pre><code>
 * {@literal @}Inject
 * {@literal @}ContextName("foo")
 *  CdiEventEndpoint{@literal <}List{@literal <}String{@literal >}{@literal >} cdiEventEndpoint;
 *
 *  // Only observe / consume events having the {@literal @}ContextName("foo") qualifier
 *  from(cdiEventEndpoint).log("Camel context 'foo'{@literal >} CDI event received: ${body}");
 *
 *  // Produce / fire events with the {@literal @}ContextName("foo") qualifier
 *  from("...").to(cdiEventEndpoint);
 *
 *  void observeCdiEvents({@literal @}Observes {@literal @}ContextName("foo") List{@literal <}String{@literal >} event) {
 *      logger.info("Camel context 'foo'{@literal >} CDI event: {}", event);
 *  }
 * </code></pre>
 */
public final class CdiEventEndpoint<T> extends DefaultEndpoint {

    private final List<CdiEventConsumer<T>> consumers = new ArrayList<>();

    private final Type type;

    private final Set<Annotation> qualifiers;

    private final BeanManager manager;

    CdiEventEndpoint(String endpointUri, Type type, Set<Annotation> qualifiers, BeanManager manager) {
        super(endpointUri);
        this.type = type;
        this.qualifiers = qualifiers;
        this.manager = manager;
    }

    static String eventEndpointUri(Type type, Set<Annotation> qualifiers) {
        return "cdi-event://" + authorityFromType(type) + qualifiers.stream()
            .map(CdiSpiHelper::createAnnotationId)
            .collect(joining("%2C", qualifiers.size() > 0 ? "?qualifiers=" : "", ""));
    }

    private static String authorityFromType(Type type) {
        if (type instanceof Class) {
            return Class.class.cast(type).getName();
        }
        if (type instanceof ParameterizedType) {
            return Stream.of(((ParameterizedType) type).getActualTypeArguments())
                .map(CdiEventEndpoint::authorityFromType)
                .collect(joining("%2C", authorityFromType(((ParameterizedType) type).getRawType()) + "%3C", "%3E"));
        }
        if (type instanceof GenericArrayType) {
            return authorityFromType(((GenericArrayType) type).getGenericComponentType()) + "%5B%5D";
        }

        throw new IllegalArgumentException("Cannot create URI authority for event type [" + type + "]");
    }

    Set<Annotation> getQualifiers() {
        return qualifiers;
    }

    Type getType() {
        return type;
    }

    @Override
    public Consumer createConsumer(Processor processor) {
        return new CdiEventConsumer<>(this, processor);
    }

    @Override
    public Producer createProducer() throws IllegalAccessException {
        // FIXME: to be replaced once event firing with dynamic parameterized type
        // is properly supported (see https://issues.jboss.org/browse/CDI-516)
        TypeLiteral<T> literal = new TypeLiteral<T>() {
        };
        for (Field field : TypeLiteral.class.getDeclaredFields()) {
            if (field.getType().equals(Type.class)) {
                field.setAccessible(true);
                field.set(literal, type);
                break;
            }
        }

        InjectionTarget<AnyEvent> target = manager.createInjectionTarget(manager.createAnnotatedType(AnyEvent.class));
        CreationalContext<AnyEvent> ctx = manager.createCreationalContext(null);
        AnyEvent instance = target.produce(ctx);
        target.inject(instance, ctx);
        return new CdiEventProducer<>(this, instance.event
            .select(literal, qualifiers.toArray(new Annotation[0])));
    }

    @Vetoed
    private static class AnyEvent {

        @Any
        @Inject
        private Event<Object> event;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    void addConsumer(CdiEventConsumer<T> consumer) {
        synchronized (consumers) {
            consumers.add(consumer);
        }
    }

    void removeConsumer(CdiEventConsumer<T> consumer) {
        synchronized (consumers) {
            consumers.remove(consumer);
        }
    }

    void notify(T t) {
        synchronized (consumers) {
            consumers.forEach(consumer -> consumer.notify(t));
        }
    }
}