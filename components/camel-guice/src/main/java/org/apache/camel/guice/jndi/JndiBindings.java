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
package org.apache.camel.guice.jndi;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import javax.naming.Context;
import javax.naming.NamingException;

import com.google.inject.Binding;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.name.Named;

import org.apache.camel.guice.jndi.internal.Classes;

/**
 * @version
 */
public final class JndiBindings {
    
    private JndiBindings() {
        //Helper class
    }
    /**
     * Binds the given injector and its binding providers to the given JNDI
     * context using <a
     * href="http://code.google.com/p/camel-extra/wiki/GuiceJndi">this mapping
     * mechanism</a>.
     * <p/>
     * This will expose all of the bindings providers to JNDI along with any
     * bindings which are annotated with {@link JndiBind} or {@link @Named} to
     * the given JNDI context.
     * 
     * @param context
     *            the context to export objects to
     * @param injector
     *            the injector used to find the bindings
     */
    public static void bindInjectorAndBindings(Context context,
            Injector injector, Properties jndiNames) throws NamingException {
        // lets find all the exported bindings
        Set<Entry<Key<?>, Binding<?>>> entries = injector.getBindings()
                .entrySet();
        for (Entry<Key<?>, Binding<?>> entry : entries) {
            Key<?> key = entry.getKey();
            Binding<?> binding = entry.getValue();
            Annotation annotation = key.getAnnotation();
            Type type = key.getTypeLiteral().getType();
            JndiBind jndiBind = null;
            if (type instanceof Class) {
                Class<?> aClass = (Class<?>) type;
                jndiBind = aClass.getAnnotation(JndiBind.class);
            }

            if (annotation instanceof JndiBind) {
                jndiBind = (JndiBind) annotation;
            }
            String jndiName = null;
            if (jndiBind != null) {
                jndiName = jndiBind.value();
            }
            if (jndiName == null) {
                if (annotation instanceof Named) {
                    Named named = (Named) annotation;
                    String name = named.value();
                    jndiName = type.toString() + "/" + name;
                } else if (type instanceof Class<?>) {
                    Class<?> aClass = (Class<?>) type;
                    if (annotation == null) {
                        jndiName = aClass.getName();
                    } else {
                        jndiName = aClass.getName() + annotation;
                    }
                }
            }
            if (jndiName != null) {
                Object value = binding.getProvider();
                if (value != null) {
                    context.bind(jndiName, value);
                }
            }
        }

        for (Entry<Object, Object> entry : jndiNames.entrySet()) {
            String jndiName = entry.getKey().toString();
            String expression = entry.getValue().toString();

            Provider<?> provider = getProviderForExpression(injector,
                    expression);
            if (provider != null) {
                context.bind(jndiName, provider);
            }
        }
    }

    static Provider<?> getProviderForExpression(Injector injector,
            String expression) {
        // TODO we could support more complex expressions
        // like 'className/name' to map to @Named annotations
        // or even 'className@annotationType(values) etc
        try {
            Class<?> type = Classes.loadClass(expression,
                    JndiBindings.class.getClassLoader());
            return injector.getProvider(type);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}
