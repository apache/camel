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
package org.apache.camel.main.download;

import org.apache.camel.Component;
import org.apache.camel.component.stub.StubComponent;
import org.apache.camel.spi.Injector;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.util.ObjectHelper;

public class KameletMainInjector implements Injector {

    private static final String ACCEPTED_STUB_NAMES
            = "StubComponent,BeanComponent,ClassComponent,DirectComponent,KameletComponent,LogComponent,RestComponent"
              + ",RestApiComponent,PlatformHttpComponent,SedaComponent,VertxHttpComponent";

    private final Injector delegate;
    private final String stubPattern;
    private final boolean silent;

    public KameletMainInjector(Injector delegate, String stubPattern, boolean silent) {
        this.delegate = delegate;
        this.stubPattern = stubPattern;
        this.silent = silent;
    }

    @Override
    public <T> T newInstance(Class<T> type) {
        boolean accept = acceptComponent(type);
        if (!accept) {
            return (T) delegate.newInstance(StubComponent.class);
        }
        return delegate.newInstance(type);
    }

    @Override
    public <T> T newInstance(Class<T> type, String factoryMethod) {
        boolean accept = acceptComponent(type);
        if (!accept) {
            return (T) delegate.newInstance(StubComponent.class);
        }
        return delegate.newInstance(type, factoryMethod);
    }

    @Override
    public <T> T newInstance(Class<T> type, boolean postProcessBean) {
        boolean accept = acceptComponent(type);
        if (!accept) {
            return (T) delegate.newInstance(StubComponent.class);
        }
        return delegate.newInstance(type, postProcessBean);
    }

    @Override
    public boolean supportsAutoWiring() {
        return delegate.supportsAutoWiring();
    }

    private boolean acceptComponent(Class<?> type) {
        boolean accept = true;
        if (stubPattern != null && Component.class.isAssignableFrom(type)) {
            accept = accept(type);
            if (!accept && !"*".equals(stubPattern)) {
                // grab component name via annotation trick!
                org.apache.camel.spi.annotations.Component ann
                        = ObjectHelper.getAnnotation(this, org.apache.camel.spi.annotations.Component.class);
                if (ann != null) {
                    boolean found = false;
                    String name = ann.value();
                    for (String n : name.split(",")) {
                        if (PatternHelper.matchPattern(n, stubPattern)) {
                            found = true;
                            break;
                        }
                    }
                    accept = !found;
                }
            }
        }
        return accept;
    }

    private boolean accept(Class<?> type) {
        String shortName = type.getSimpleName();
        // we are stubbing but need to accept the following
        return ACCEPTED_STUB_NAMES.contains(shortName);
    }
}
