/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.impl;

import org.apache.camel.spi.Registry;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.util.Hashtable;

/**
 * A {@link Registry} implementation which looks up the objects in JNDI
 *
 * @version $Revision: 1.1 $
 */
public class JndiRegistry implements Registry {
    private Context context;

    public <T> T lookup(String name, Class<T> type) {
        Object value = lookup(name);
        return type.cast(value);
    }

    public Object lookup(String name) {
        try {
            return getContext().lookup(name);
        }
        catch (NamingException e) {
            return null;
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
        Hashtable properties = new Hashtable(System.getProperties());
        return new InitialContext(properties);
    }
}
