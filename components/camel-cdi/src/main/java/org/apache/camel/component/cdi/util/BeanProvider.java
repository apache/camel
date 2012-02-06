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
package org.apache.camel.component.cdi.util;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.enterprise.context.Dependent;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.Typed;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;

/**
 * This class contains utility methods to resolve contextual references
 * in situations where no injection is available.
 *
 * @see BeanManagerProvider
 */
@Typed
public final class BeanProvider {

    private BeanProvider() {
        // this is a utility class which doesn't get instantiated.
    }

    /**
     * <p></p>Get a Contextual Reference by it's type and annotation (qualifier).
     * You can use this method to get contextual references of a given type.
     * A 'Contextual Reference' is a proxy which will automatically resolve
     * the correct contextual instance when you access any method.</p>
     * <p/>
     * <p><b>Attention:</b> You shall not use this method to manually resolve a
     * &#064;Dependent bean! The reason is that this contextual instances do usually
     * live in the well defined lifecycle of their injection point (the bean they got
     * injected into). But if we manually resolve a &#064;Dependent bean, then it does <b>not</b>
     * belong to such a well defined lifecycle (because &#064;Dependent it is not
     * &#064;NormalScoped) and thus will not automatically be
     * destroyed at the end of the lifecycle. You need to manually destroy this contextual instance via
     * {@link javax.enterprise.context.spi.Contextual#destroy(Object, javax.enterprise.context.spi.CreationalContext)}.
     * Thus you also need to manually store the CreationalContext and the Bean you
     * used to create the contextual instance which this method will not provide.</p>
     *
     * @param type       the type of the bean in question
     * @param optional   if <code>true</code> it will return <code>null</code> if no bean could be found or created.
     *                   Otherwise it will throw an {@code IllegalStateException}
     * @param qualifiers additional qualifiers which further distinct the resolved bean
     * @param <T>        target type
     * @return the resolved Contextual Reference
     */
    public static <T> T getContextualReference(Class<T> type, boolean optional, Annotation... qualifiers) {
        BeanManager beanManager = getBeanManager();
        Set<Bean<?>> beans = beanManager.getBeans(type, qualifiers);

        if (beans == null || beans.isEmpty()) {
            if (optional) {
                return null;
            }

            throw new IllegalStateException("Could not find beans for Type='" + type
                    + "' and qualifiers: " + Arrays.toString(qualifiers));
        }
        return getContextualReference(type, beanManager, beans);
    }

    /**
     * <p>Get a Contextual Reference by it's EL Name.
     * This only works for beans with the &#064;Named annotation.</p>
     * <p/>
     * <p><b>Attention:</b> please see the notes on manually resolving &#064;Dependent bean
     * in {@link #getContextualReference(Class, boolean, java.lang.annotation.Annotation...)}!</p>
     *
     * @param name     the EL name of the bean
     * @param optional if <code>true</code> it will return <code>null</code> if no bean could be found or created.
     *                 Otherwise it will throw an {@code IllegalStateException}
     * @return the resolved Contextual Reference
     */
    public static Object getContextualReference(String name, boolean optional) {
        return getContextualReference(name, optional, Object.class);
    }

    /**
     * <p>Get a Contextual Reference by it's EL Name.
     * This only works for beans with the &#064;Named annotation.</p>
     * <p/>
     * <p><b>Attention:</b> please see the notes on manually resolving &#064;Dependent bean
     * in {@link #getContextualReference(Class, boolean, java.lang.annotation.Annotation...)}!</p>
     *
     * @param name     the EL name of the bean
     * @param optional if <code>true</code> it will return <code>null</code> if no bean could be found or created.
     *                 Otherwise it will throw an {@code IllegalStateException}
     * @param type     the type of the bean in question - use {@link #getContextualReference(String, boolean)}
     *                 if the type is unknown e.g. in dyn. use-cases
     * @param <T>      target type
     * @return the resolved Contextual Reference
     */
    public static <T> T getContextualReference(String name, boolean optional, Class<T> type) {
        BeanManager beanManager = getBeanManager();
        Set<Bean<?>> beans = beanManager.getBeans(name);

        if (beans == null || beans.isEmpty()) {
            if (optional) {
                return null;
            }
            throw new IllegalStateException("Could not find beans for Type=" + type
                    + " and name:" + name);
        }
        return getContextualReference(type, beanManager, beans);
    }

    /**
     * <p>Get a list of Contextual References by it's type independent of the qualifier
     * (including dependent scoped beans).
     * <p/>
     * You can use this method to get all contextual references of a given type.
     * A 'Contextual Reference' is a proxy which will automatically resolve
     * the correct contextual instance when you access any method.</p>
     * <p/>
     * <p><b>Attention:</b> please see the notes on manually resolving &#064;Dependent bean
     * in {@link #getContextualReference(Class, boolean, java.lang.annotation.Annotation...)}!</p>
     *
     * @param type     the type of the bean in question
     * @param optional if <code>true</code> it will return an empty list if no bean could be found or created.
     *                 Otherwise it will throw an {@code IllegalStateException}
     * @param <T>      target type
     * @return the resolved list of Contextual Reference or an empty-list if optional is true
     */
    public static <T> List<T> getContextualReferences(Class<T> type, boolean optional) {
        return getContextualReferences(type, optional, true);
    }

    /**
     * <p>Get a list of Contextual References by it's type independent of the qualifier.
     * <p/>
     * Further details are available at {@link #getContextualReferences(Class, boolean)}
     *
     * @param type                      the type of the bean in question
     * @param optional                  if <code>true</code> it will return an empty list if no bean could be found or created.
     *                                  Otherwise it will throw an {@code IllegalStateException}
     * @param includeDefaultScopedBeans specifies if dependent scoped beans should be included in the in the result
     * @param <T>                       target type
     * @return the resolved list of Contextual Reference or an empty-list if optional is true
     */
    public static <T> List<T> getContextualReferences(
            Class<T> type, boolean optional, boolean includeDefaultScopedBeans) {

        BeanManager beanManager = getBeanManager();
        Set<Bean<?>> beans = beanManager.getBeans(type, new AnyLiteral());

        if (beans == null || beans.isEmpty()) {
            if (optional) {
                return Collections.emptyList();
            }

            throw new IllegalStateException("Could not find beans for Type=" + type);
        }

        if (!includeDefaultScopedBeans) {
            beans = filterDefaultScopedBeans(beans);
        }

        List<T> result = new ArrayList<T>(beans.size());

        for (Bean<?> bean : beans) {
            result.add(getContextualReference(type, beanManager,
                new HashSet<Bean<?>>(Arrays.asList(new Bean<?>[]{bean}))));
        }
        return result;
    }

    public static <T> Map<String, T> getContextualNamesReferences(Class<T> type,
                                                                  boolean optional,
                                                                  boolean includeDefaultScopedBeans) {
        BeanManager beanManager = getBeanManager();
        Set<Bean<?>> beans = beanManager.getBeans(type, new AnyLiteral());

        if (beans == null || beans.isEmpty()) {
            if (optional) {
                return Collections.emptyMap();
            }

            throw new IllegalStateException("Could not find beans for Type=" + type);
        }

        if (!includeDefaultScopedBeans) {
            beans = filterDefaultScopedBeans(beans);
        }

        Map<String, T> result = new HashMap<String, T>(beans.size());

        for (Bean<?> bean : beans) {
            result.put(bean.getName(), getContextualReference(type, beanManager,
                    new HashSet<Bean<?>>(Arrays.asList(new Bean<?>[]{bean}))));
        }
        return result;
    }

    private static Set<Bean<?>> filterDefaultScopedBeans(Set<Bean<?>> beans) {
        Set<Bean<?>> result = new HashSet<Bean<?>>(beans.size());

        Iterator<Bean<?>> beanIterator = beans.iterator();

        Bean<?> currentBean;
        while (beanIterator.hasNext()) {
            currentBean = beanIterator.next();

            if (!Dependent.class.isAssignableFrom(currentBean.getScope())) {
                result.add(currentBean);
            }
        }
        return result;
    }

    /**
     * Internal helper method to resolve the right bean and resolve the contextual reference.
     *
     * @param type        the type of the bean in question
     * @param beanManager current bean-manager
     * @param beans       beans in question
     * @param <T>         target type
     * @return the contextual reference
     */
    @SuppressWarnings("unchecked")
    private static <T> T getContextualReference(Class<T> type, BeanManager beanManager, Set<Bean<?>> beans) {
        Bean<?> bean = beanManager.resolve(beans);
        CreationalContext<?> creationalContext = beanManager.createCreationalContext(bean);
        return (T)beanManager.getReference(bean, type, creationalContext);
    }

    /**
     * Internal method to resolve the BeanManager via the {@link BeanManagerProvider}
     */
    private static BeanManager getBeanManager() {
        return BeanManagerProvider.getInstance().getBeanManager();
    }
}
