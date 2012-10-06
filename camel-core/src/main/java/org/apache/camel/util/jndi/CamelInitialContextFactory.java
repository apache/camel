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
package org.apache.camel.util.jndi;

import java.util.Hashtable;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.spi.InitialContextFactory;

import org.apache.camel.util.CastUtils;

/**
 * A factory of the Camel {@link javax.naming.InitialContext} which allows a {@link java.util.Map} to be used to create a
 * JNDI context.
 * <p/>
 * This implementation is prototype based, by creating a <b>new</b> context on each call to
 * {@link #getInitialContext(java.util.Hashtable)}.
 *
 * @version 
 */
public class CamelInitialContextFactory implements InitialContextFactory {

    /**
     * Creates a new context with the given environment.
     *
     * @param  environment  the environment, must not be <tt>null</tt>
     * @return the created context.
     * @throws NamingException is thrown if creation failed.
     */
    public Context getInitialContext(Hashtable<?, ?> environment) throws NamingException {
        try {
            return new JndiContext(CastUtils.cast(environment, String.class, Object.class));
        } catch (Exception e) {
            if (e instanceof NamingException) {
                throw (NamingException) e;
            }
            NamingException exception = new NamingException(e.getMessage());
            exception.initCause(e);
            throw exception;
        }
    }
}
