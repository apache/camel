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
package org.apache.camel.guice.jsr250;

import java.lang.annotation.Annotation;
import java.lang.reflect.Member;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import com.google.inject.Binding;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.ProvisionException;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;

import org.apache.camel.guice.inject.Injectors;
import org.apache.camel.guice.support.AnnotationMemberProviderSupport;


/**
 * A useful base class for any provider which looks up values by name
 * annotations or named entries in OSGi
 * 
 * @version
 */
public abstract class NamedProviderSupport<A extends Annotation> extends
        AnnotationMemberProviderSupport<A> {
    @Inject
    private Injector injector;
    private Context context;

    public Context getContext() {
        return context;
    }

    @Inject(optional = true)
    public void setContext(Context context) {
        this.context = context;
    }

    protected Object provideObjectFromNamedBindingOrJndi(
            TypeLiteral<?> requiredType, String name) {
        Binding<?> binding = Injectors.getBinding(injector,
                Key.get(requiredType, Names.named(name)));
        if (binding != null) {
            return binding.getProvider().get();
        }

        // TODO we may want to try avoid the dependency on JNDI classes
        // for better operation in GAE?
        try {
            if (context == null) {
                context = new InitialContext();
            }
            return context.lookup(name);
        } catch (NamingException e) {
            throw new ProvisionException("Failed to find name '" + name
                    + "' in JNDI. Cause: " + e, e);
        }
    }

    /**
     * if no valid name is present on the annotation then use the member name
     */
    protected String getValueName(String nameFromAnnotation, Member member) {
        if (nameFromAnnotation == null || nameFromAnnotation.length() == 0) {
            nameFromAnnotation = member.getName();
        }
        if (nameFromAnnotation == null || nameFromAnnotation.length() == 0) {
            throw new IllegalArgumentException("No name defined");
        }
        return nameFromAnnotation;
    }
}
