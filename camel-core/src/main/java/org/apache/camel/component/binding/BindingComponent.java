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
package org.apache.camel.component.binding;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.spi.Binding;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.util.CamelContextHelper.getMandatoryEndpoint;

/**
 * A composite {@link org.apache.camel.Component} which creates a {@link BindingEndpoint} from a
 * configured {@link Binding} instance and using the optional {@link #setUriPrefix(String)}
 * and {@link #setUriPostfix(String)} to create the underlying endpoint from the remaining URI
 *
 * @deprecated use {@link org.apache.camel.component.binding.BindingNameComponent}
 */
@Deprecated
public class BindingComponent extends DefaultComponent {
    private Binding binding;
    private String uriPrefix;
    private String uriPostfix;

    public BindingComponent() {
    }

    public BindingComponent(Binding binding) {
        this.binding = binding;
    }

    public BindingComponent(Binding binding, String uriPrefix) {
        this(binding);
        this.uriPrefix = uriPrefix;
    }

    public BindingComponent(Binding binding, String uriPrefix, String uriPostfix) {
        this(binding, uriPrefix);
        this.uriPostfix = uriPostfix;
    }

    public Binding getBinding() {
        return binding;
    }

    public void setBinding(Binding binding) {
        this.binding = binding;
    }

    public String getUriPostfix() {
        return uriPostfix;
    }

    public void setUriPostfix(String uriPostfix) {
        this.uriPostfix = uriPostfix;
    }

    public String getUriPrefix() {
        return uriPrefix;
    }

    public void setUriPrefix(String uriPrefix) {
        this.uriPrefix = uriPrefix;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        Binding bindingValue = getBinding();
        ObjectHelper.notNull(bindingValue, "binding");

        CamelContext camelContext = getCamelContext();
        String delegateURI = createDelegateURI(remaining, parameters);
        Endpoint delegate = getMandatoryEndpoint(camelContext, delegateURI);
        return new BindingEndpoint(uri, this, bindingValue, delegate);
    }

    protected String createDelegateURI(String remaining, Map<String, Object> parameters) {
        return getOrEmpty(uriPrefix) + remaining + getOrEmpty(uriPostfix);
    }

    protected static String getOrEmpty(String text) {
        return text != null ? text : "";
    }
}
