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
package org.apache.camel.main.support;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.PropertyConfigurer;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

@Component("dummy")
public class MyDummyComponent extends DefaultComponent {
    private MyDummyConfiguration configuration;
    private boolean configurer;
    private String componentValue;

    public MyDummyComponent(boolean configurer) {
        this.configurer = configurer;
    }

    public MyDummyConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(MyDummyConfiguration configuration) {
        this.configuration = configuration;
    }

    // this method makes camel no able to find a suitable setter
    public void setConfiguration(Object configuration) {
        this.configuration = (MyDummyConfiguration)configuration;
    }

    public String getComponentValue() {
        return componentValue;
    }

    public MyDummyComponent setComponentValue(String componentValue) {
        this.componentValue = componentValue;
        return this;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public PropertyConfigurer getComponentPropertyConfigurer() {
        return configurer ? new MyDummyComponentConfigurer() : null;
    }
}
