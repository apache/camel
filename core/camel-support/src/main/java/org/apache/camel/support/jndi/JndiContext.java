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

import java.io.Serializable;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import javax.naming.Binding;
import javax.naming.CompositeName;
import javax.naming.Context;
import javax.naming.LinkRef;
import javax.naming.Name;
import javax.naming.NameClassPair;
import javax.naming.NameNotFoundException;
import javax.naming.NameParser;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.NotContextException;
import javax.naming.OperationNotSupportedException;
import javax.naming.Reference;
import javax.naming.spi.NamingManager;

import org.apache.camel.util.CastUtils;

/**
 * A default JNDI context
 */
public class JndiContext implements Context, Serializable {
    public static final String SEPARATOR = "/";
    protected static final NameParser NAME_PARSER = new NameParser() {
        public Name parse(String name) throws NamingException {
            return new CompositeName(name);
        }
    };
    private static final long serialVersionUID = -5754338187296859149L;

    private final Hashtable<String, Object> environment; // environment for this context
    private final Map<String, Object> bindings; // bindings at my level
    private final Map<String, Object> treeBindings; // all bindings under me
    private boolean frozen;
    private String nameInNamespace = "";

    public JndiContext() throws Exception {
        this(new Hashtable<>());
    }

    public JndiContext(Hashtable<String, Object> env) throws Exception {
        this(env, createBindingsMapFromEnvironment(env));
    }

    public JndiContext(Hashtable<String, Object> environment, Map<String, Object> bindings) {
        this.environment = environment == null ? new Hashtable<>() : new Hashtable<>(environment);
        this.bindings = bindings;
        treeBindings = new HashMap<>();
    }

    public JndiContext(Hashtable<String, Object> environment, Map<String, Object> bindings, String nameInNamespace) {
        this(environment, bindings);
        this.nameInNamespace = nameInNamespace;
    }

    protected JndiContext(JndiContext clone, Hashtable<String, Object> env) {
        this.bindings = clone.bindings;
        this.treeBindings = clone.treeBindings;
        this.environment = new Hashtable<>(env);
    }

    protected JndiContext(JndiContext clone, Hashtable<String, Object> env, String nameInNamespace) {
        this(clone, env);
        this.nameInNamespace = nameInNamespace;
    }

    /**
     * A helper method to create the JNDI bindings from the input environment properties using $foo.class to point to a
     * class name with $foo.* being properties set on the injected bean
     */
    public static Map<String, Object> createBindingsMapFromEnvironment(Hashtable<String, Object> env) throws Exception {
        return new HashMap<>(env);
    }

    public void freeze() {
        frozen = true;
    }

    boolean isFrozen() {
        return frozen;
    }

    /**
     * internalBind is intended for use only during setup or possibly by suitably synchronized superclasses. It binds
     * every possible lookup into a map in each context. To do this, each context strips off one name segment and if
     * necessary creates a new context for it. Then it asks that context to bind the remaining name. It returns a map
     * containing all the bindings from the next context, plus the context it just created (if it in fact created it).
     * (the names are suitably extended by the segment originally lopped off).
     */
    protected Map<String, Object> internalBind(String name, Object value) throws NamingException {
        org.apache.camel.util.ObjectHelper.isNotEmpty(name);
        org.apache.camel.util.ObjectHelper.notNull(frozen, "frozen");

        Map<String, Object> newBindings = new HashMap<>();
        int pos = name.indexOf('/');
        if (pos == -1) {
            if (treeBindings.put(name, value) != null) {
                throw new NamingException("Something already bound at " + name);
            }
            bindings.put(name, value);
            newBindings.put(name, value);
        } else {
            String segment = name.substring(0, pos);
            org.apache.camel.util.ObjectHelper.isNotEmpty(segment);
            Object o = treeBindings.get(segment);
            if (o == null) {
                o = newContext();
                treeBindings.put(segment, o);
                bindings.put(segment, o);
                newBindings.put(segment, o);
            } else if (!(o instanceof JndiContext)) {
                throw new NamingException("Something already bound where a subcontext should go");
            }
            JndiContext defaultContext = (JndiContext) o;
            String remainder = name.substring(pos + 1);
            Map<String, Object> subBindings = defaultContext.internalBind(remainder, value);
            for (Entry<String, Object> entry : subBindings.entrySet()) {
                String subName = segment + "/" + entry.getKey();
                Object bound = entry.getValue();
                treeBindings.put(subName, bound);
                newBindings.put(subName, bound);
            }
        }
        return newBindings;
    }

    protected JndiContext newContext() {
        try {
            return new JndiContext();
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public Object addToEnvironment(String propName, Object propVal) throws NamingException {
        return environment.put(propName, propVal);
    }

    @Override
    public Hashtable<String, Object> getEnvironment() throws NamingException {
        return CastUtils.cast((Hashtable<?, ?>) environment.clone(), String.class, Object.class);
    }

    @Override
    public Object removeFromEnvironment(String propName) throws NamingException {
        return environment.remove(propName);
    }

    @Override
    public Object lookup(String name) throws NamingException {
        if (name.length() == 0) {
            return this;
        }
        Object result = treeBindings.get(name);
        if (result == null) {
            result = bindings.get(name);
        }
        if (result == null) {
            int pos = name.indexOf(':');
            if (pos > 0) {
                String scheme = name.substring(0, pos);
                Context ctx = NamingManager.getURLContext(scheme, environment);
                if (ctx == null) {
                    throw new NamingException("scheme " + scheme + " not recognized");
                }
                return ctx.lookup(name);
            } else {
                // Split out the first name of the path
                // and look for it in the bindings map.
                CompositeName path = new CompositeName(name);

                if (path.size() == 0) {
                    return this;
                } else {
                    String first = path.get(0);
                    Object value = bindings.get(first);
                    if (value == null) {
                        throw new NameNotFoundException(name);
                    } else if (value instanceof Context && path.size() > 1) {
                        Context subContext = (Context) value;
                        value = subContext.lookup(path.getSuffix(1));
                    }
                    return value;
                }
            }
        }
        if (result instanceof LinkRef) {
            LinkRef ref = (LinkRef) result;
            result = lookup(ref.getLinkName());
        }
        if (result instanceof Reference) {
            try {
                result = NamingManager.getObjectInstance(result, null, null, this.environment);
            } catch (NamingException e) {
                throw e;
            } catch (Exception e) {
                throw (NamingException) new NamingException("could not look up : " + name).initCause(e);
            }
        }
        if (result instanceof JndiContext) {
            String prefix = getNameInNamespace();
            if (prefix.length() > 0) {
                prefix = prefix + SEPARATOR;
            }
            result = new JndiContext((JndiContext) result, environment, prefix + name);
        }
        return result;
    }

    @Override
    public Object lookup(Name name) throws NamingException {
        return lookup(name.toString());
    }

    @Override
    public Object lookupLink(String name) throws NamingException {
        return lookup(name);
    }

    @Override
    public Name composeName(Name name, Name prefix) throws NamingException {
        Name result = (Name) prefix.clone();
        result.addAll(name);
        return result;
    }

    @Override
    public String composeName(String name, String prefix) throws NamingException {
        CompositeName result = new CompositeName(prefix);
        result.addAll(new CompositeName(name));
        return result.toString();
    }

    @Override
    public NamingEnumeration<NameClassPair> list(String name) throws NamingException {
        Object o = lookup(name);
        if (o == this) {
            return CastUtils.cast(new ListEnumeration());
        } else if (o instanceof Context) {
            return ((Context) o).list("");
        } else {
            throw new NotContextException();
        }
    }

    @Override
    public NamingEnumeration<Binding> listBindings(String name) throws NamingException {
        Object o = lookup(name);
        if (o == this) {
            return CastUtils.cast(new ListBindingEnumeration());
        } else if (o instanceof Context) {
            return ((Context) o).listBindings("");
        } else {
            throw new NotContextException();
        }
    }

    @Override
    public Object lookupLink(Name name) throws NamingException {
        return lookupLink(name.toString());
    }

    @Override
    public NamingEnumeration<NameClassPair> list(Name name) throws NamingException {
        return list(name.toString());
    }

    @Override
    public NamingEnumeration<Binding> listBindings(Name name) throws NamingException {
        return listBindings(name.toString());
    }

    @Override
    public void bind(Name name, Object value) throws NamingException {
        bind(name.toString(), value);
    }

    @Override
    public void bind(String name, Object value) throws NamingException {
        if (isFrozen()) {
            throw new OperationNotSupportedException();
        } else {
            internalBind(name, value);
        }
    }

    @Override
    public void close() throws NamingException {
        // ignore
    }

    @Override
    public Context createSubcontext(Name name) throws NamingException {
        throw new OperationNotSupportedException();
    }

    @Override
    public Context createSubcontext(String name) throws NamingException {
        throw new OperationNotSupportedException();
    }

    @Override
    public void destroySubcontext(Name name) throws NamingException {
        throw new OperationNotSupportedException();
    }

    @Override
    public void destroySubcontext(String name) throws NamingException {
        throw new OperationNotSupportedException();
    }

    @Override
    public String getNameInNamespace() throws NamingException {
        return nameInNamespace;
    }

    @Override
    public NameParser getNameParser(Name name) throws NamingException {
        return NAME_PARSER;
    }

    @Override
    public NameParser getNameParser(String name) throws NamingException {
        return NAME_PARSER;
    }

    @Override
    public void rebind(Name name, Object value) throws NamingException {
        bind(name, value);
    }

    @Override
    public void rebind(String name, Object value) throws NamingException {
        bind(name, value);
    }

    @Override
    public void rename(Name oldName, Name newName) throws NamingException {
        throw new OperationNotSupportedException();
    }

    @Override
    public void rename(String oldName, String newName) throws NamingException {
        throw new OperationNotSupportedException();
    }

    @Override
    public void unbind(Name name) throws NamingException {
        throw new OperationNotSupportedException();
    }

    @Override
    public void unbind(String name) throws NamingException {
        bindings.remove(name);
        treeBindings.remove(name);
    }

    private abstract class LocalNamingEnumeration implements NamingEnumeration<Object> {
        private Iterator<Map.Entry<String, Object>> i = bindings.entrySet().iterator();

        @Override
        public boolean hasMore() throws NamingException {
            return i.hasNext();
        }

        @Override
        public boolean hasMoreElements() {
            return i.hasNext();
        }

        protected Map.Entry<String, Object> getNext() {
            return i.next();
        }

        @Override
        public void close() throws NamingException {
        }
    }

    private class ListEnumeration extends LocalNamingEnumeration {
        ListEnumeration() {
        }

        @Override
        public Object next() throws NamingException {
            return nextElement();
        }

        @Override
        public Object nextElement() {
            Map.Entry<String, Object> entry = getNext();
            return new NameClassPair(entry.getKey(), entry.getValue().getClass().getName());
        }
    }

    private class ListBindingEnumeration extends LocalNamingEnumeration {
        ListBindingEnumeration() {
        }

        @Override
        public Object next() throws NamingException {
            return nextElement();
        }

        @Override
        public Object nextElement() {
            Map.Entry<String, Object> entry = getNext();
            return new Binding(entry.getKey(), entry.getValue());
        }
    }

}
