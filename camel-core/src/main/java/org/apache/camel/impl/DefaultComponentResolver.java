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

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Exchange;
import org.apache.camel.spi.ComponentResolver;
import org.apache.camel.util.FactoryFinder;
import org.apache.camel.util.NoFactoryAvailableException;

/**
 * The default implementation of {@link ComponentResolver}
 * which tries to find components by using the URI scheme prefix and searching for a file of the URI
 * scheme name in the <b>META-INF/services/org/apache/camel/component/</b>
 * directory on the classpath.
 *
 * @version $Revision$
 */
public class DefaultComponentResolver<E extends Exchange> implements ComponentResolver<E> {
    protected static final FactoryFinder componentFactory = new FactoryFinder("META-INF/services/org/apache/camel/component/");

    public Component<E> resolveComponent(String name, CamelContext context) {
        Class type;
        try {
            type = componentFactory.findClass(name);
        }
        catch (NoFactoryAvailableException e) {
            return null;
        }
        catch (Throwable e) {
            throw new IllegalArgumentException("Invalid URI, no EndpointResolver registered for scheme : " + name, e);
        }
        if (type == null) {
            return null;
        }
        if (Component.class.isAssignableFrom(type)) {
            return (Component<E>)context.getInjector().newInstance(type);
        }
        else {
            throw new IllegalArgumentException("Type is not a Component implementation. Found: " + type.getName());
        }
    }
}
