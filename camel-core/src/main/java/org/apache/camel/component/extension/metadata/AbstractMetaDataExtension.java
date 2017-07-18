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
package org.apache.camel.component.extension.metadata;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Component;
import org.apache.camel.ComponentAware;
import org.apache.camel.component.extension.MetaDataExtension;

public abstract class AbstractMetaDataExtension implements MetaDataExtension, ComponentAware, CamelContextAware {
    private CamelContext camelContext;
    private Component component;

    protected AbstractMetaDataExtension() {
        this(null, null);
    }

    protected AbstractMetaDataExtension(Component component) {
        this(component, component.getCamelContext());
    }

    protected AbstractMetaDataExtension(CamelContext camelContext) {
        this(null, camelContext);
    }

    protected AbstractMetaDataExtension(Component component, CamelContext camelContext) {
        this.component = component;
        this.camelContext = camelContext;
    }

    @Override
    public void setComponent(Component component) {
        this.component = component;
    }

    @Override
    public Component getComponent() {
        return component;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }
}
