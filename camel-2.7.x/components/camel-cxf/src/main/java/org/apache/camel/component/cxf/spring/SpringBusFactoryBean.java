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
package org.apache.camel.component.cxf.spring;

import org.apache.cxf.Bus;
import org.apache.cxf.bus.spring.SpringBusFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.SmartFactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * This factoryBean which can help user to choice CXF components that he wants bus to load
 * without needing to import bunch of CXF packages in OSGi bundle, as the SpringBusFactory
 * will try to load the bus extensions with the CXF bundle classloader.
 * You can set the CXF extensions files with ; as the separator to create a bus.
 */
public class SpringBusFactoryBean implements SmartFactoryBean {
    private String[] cfgFiles;
    private boolean includeDefaultBus;
    private SpringBusFactory bf;

    public Object getObject() throws Exception {
        bf = new SpringBusFactory();
        if (cfgFiles != null) {
            return bf.createBus(cfgFiles, includeDefaultBus);
        } else {
            return bf.createBus();
        }
    }

    public Class getObjectType() {
        return Bus.class;
    }

    public void setCfgFiles(String cfgFiles) {
        this.cfgFiles = cfgFiles.split(";");
    }

    public void setIncludeDefaultBus(boolean includeDefaultBus) {
        this.includeDefaultBus = includeDefaultBus;
    }

    public boolean isSingleton() {
        return true;
    }

    public boolean isEagerInit() {
        return true;
    }

    public boolean isPrototype() {
        return false;
    }

}
