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

import org.apache.camel.Component;
import org.apache.camel.EndpointResolver;
import org.apache.camel.Exchange;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.converter.Injector;
import org.apache.camel.util.FactoryFinder;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.NoFactoryAvailableException;

/**
 * An implementation of {@link org.apache.camel.EndpointResolver} that delegates to
 * other {@link EndpointResolver} which are selected based on the uri prefix.
 * <p/>
 * The delegate {@link EndpointResolver} are associated with uri prefixes by
 * adding a property file with the same uri prefix in the
 * META-INF/services/org/apache/camel/EndpointResolver/
 * directory on the classpath.
 *
 * @version $Revision$
 */
public class DefaultComponentResolver<E extends Exchange> {
    static final private FactoryFinder componentFactory = new FactoryFinder("META-INF/services/org/apache/camel/component/");

    public Component<E> resolveComponent(String uri, CamelContext context) {
        String splitURI[] = ObjectHelper.splitOnCharacter(uri, ":", 2);
        if (splitURI[1] == null) {
            throw new IllegalArgumentException("Invalid URI, it did not contain a scheme: " + uri);
        }
        String scheme = splitURI[0];
        Class type;
        try {
            type = componentFactory.findClass(scheme);
        }
        catch (NoFactoryAvailableException e) {
            return null;
        }
        catch (Throwable e) {
            throw new IllegalArgumentException("Invalid URI, no EndpointResolver registered for scheme : " + scheme, e);
        }
        if (type == null) {
            return null;
        }
        if (Component.class.isAssignableFrom(type)) {
            Component<E> answer = (Component<E>) context.getInjector().newInstance(type);
            // lets add the component using the prefix
            context.addComponent(scheme, answer);
            // TODO should we start it?
            return answer;
        }
        else {
            throw new IllegalArgumentException("Type is not a Component implementation. Found: " + type.getName());
        }
    }
}
