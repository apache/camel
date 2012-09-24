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

/**
 * A factory of the Camel {@link javax.naming.InitialContext} which allows a {@link java.util.Map} to be used to create a
 * JNDI context.
 * <p/>
 * This implementation is singleton based, by creating a <b>new</b> context once, and reusing it on each call to
 * {@link #getInitialContext(java.util.Hashtable)}.
 *
 * @version
 */
public class CamelSingletonInitialContextFactory extends CamelInitialContextFactory {

    private static volatile Context context;

    /**
     * Gets or creates the context with the given environment.
     * <p/>
     * This implementation will create the context once, and then return the same instance
     * on multiple calls.
     *
     * @param  environment  the environment, must not be <tt>null</tt>
     * @return the created context.
     * @throws javax.naming.NamingException is thrown if creation failed.
     */
    public synchronized Context getInitialContext(Hashtable<?, ?> environment) throws NamingException {
        if (context == null) {
            context = super.getInitialContext(environment);
        }
        return context;
    }
}
