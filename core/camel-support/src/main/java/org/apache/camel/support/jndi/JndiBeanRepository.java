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
package org.apache.camel.support.jndi;

import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameClassPair;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;

import org.apache.camel.NoSuchBeanException;
import org.apache.camel.spi.BeanRepository;

/**
 * A {@link BeanRepository} implementation which looks up the objects in JNDI
 */
public class JndiBeanRepository implements BeanRepository {

    private Context context;
    private Map environment;
    private final boolean standalone;

    public JndiBeanRepository() {
        this.standalone = false;
    }

    public JndiBeanRepository(Map environment) {
        this.environment = environment;
        this.standalone = false;
    }

    public JndiBeanRepository(Context context) {
        this.context = context;
        this.standalone = false;
    }

    /**
     * Whether to use standalone mode, where the JNDI initial context factory is using
     * {@link CamelInitialContextFactory}.
     */
    public JndiBeanRepository(boolean standalone) {
        this.standalone = true;
    }

    @Override
    public <T> T lookupByNameAndType(String name, Class<T> type) {
        Object answer = lookupByName(name);

        // just to be safe
        if (answer == null) {
            return null;
        }

        try {
            answer = unwrap(answer);
            return type.cast(answer);
        } catch (Throwable e) {
            String msg = "Found bean: " + name + " in JNDI Context: " + context
                    + " of type: " + answer.getClass().getName() + " expected type was: " + type;
            throw new NoSuchBeanException(name, msg, e);
        }
    }

    @Override
    public Object lookupByName(String name) {
        try {
            return unwrap(getContext().lookup(name));
        } catch (NameNotFoundException e) {
            return null;
        } catch (NamingException e) {
            return null;
        }
    }

    @Override
    public <T> Map<String, T> findByTypeWithName(Class<T> type) {
        Map<String, T> answer = new LinkedHashMap<>();
        try {
            NamingEnumeration<NameClassPair> list = getContext().list("");
            while (list.hasMore()) {
                NameClassPair pair = list.next();
                Object instance = context.lookup(pair.getName());
                instance = unwrap(instance);
                if (type.isInstance(instance)) {
                    answer.put(pair.getName(), type.cast(instance));
                }
            }
        } catch (NamingException e) {
            // ignore
        }

        return answer;
    }

    @Override
    public <T> Set<T> findByType(Class<T> type) {
        Set<T> answer = new LinkedHashSet<>();
        try {
            NamingEnumeration<NameClassPair> list = getContext().list("");
            while (list.hasMore()) {
                NameClassPair pair = list.next();
                Object instance = context.lookup(pair.getName());
                instance = unwrap(instance);
                if (type.isInstance(instance)) {
                    answer.add(type.cast(instance));
                }
            }
        } catch (NamingException e) {
            // ignore
        }
        return answer;
    }

    public void close() throws NamingException {
        if (context != null) {
            context.close();
        }
    }

    public Context getContext() throws NamingException {
        if (context == null) {
            context = createContext();
        }
        return context;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    protected Context createContext() throws NamingException {
        Hashtable<Object, Object> properties = new Hashtable<>(System.getProperties());
        if (environment != null) {
            properties.putAll(environment);
        }
        // must include a factory if none provided in standalone mode
        if (standalone && !properties.containsKey("java.naming.factory.initial")) {
            properties.put("java.naming.factory.initial", "org.apache.camel.support.jndi.CamelInitialContextFactory");
        }
        return new InitialContext(properties);
    }
}
