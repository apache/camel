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
package org.apache.camel.impl;

import java.util.Collections;
import java.util.Hashtable;
import java.util.Map;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;

import org.apache.camel.NoSuchBeanException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Registry;

/**
 * A {@link Registry} implementation which looks up the objects in JNDI
 * 
 * @version 
 */
public class JndiRegistry implements Registry {
    private Context context;

    public JndiRegistry() {
    }

    public JndiRegistry(Context context) {
        this.context = context;
    }

    public <T> T lookup(String name, Class<T> type) {
        Object answer = lookup(name);

        // just to be safe
        if (answer == null) {
            return null;
        }

        try {
            return type.cast(answer);
        } catch (Throwable e) {
            String msg = "Found bean: " + name + " in JNDI Context: " + context
                    + " of type: " + answer.getClass().getName() + " expected type was: " + type;
            throw new NoSuchBeanException(name, msg, e);
        }
    }

    public Object lookup(String name) {
        try {
            return getContext().lookup(name);
        } catch (NameNotFoundException e) {
            return null;
        } catch (NamingException e) {
            return null;
        }
    }

    public <T> Map<String, T> lookupByType(Class<T> type) {
        // not implemented so we return an empty map
        return Collections.emptyMap();
    }

    public void bind(String s, Object o) {
        try {
            getContext().bind(s, o);
        } catch (NamingException e) {
            throw new RuntimeCamelException(e);
        }
    }

    public void close() throws NamingException {
        getContext().close();
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
        Hashtable<?, ?> properties = new Hashtable<Object, Object>(System.getProperties());
        return new InitialContext(properties);
    }
}
