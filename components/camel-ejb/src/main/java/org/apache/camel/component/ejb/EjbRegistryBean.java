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
package org.apache.camel.component.ejb;

import org.apache.camel.CamelContext;
import org.apache.camel.component.bean.RegistryBean;
import org.apache.camel.spi.Registry;

/**
 * An implementation of a {@link org.apache.camel.component.bean.BeanHolder} which will look up
 * an EJB bean from the JNDI {@link javax.naming.Context}
 *
 * @version 
 */
public class EjbRegistryBean extends RegistryBean {

    private Registry registry;

    public EjbRegistryBean(Registry registry, CamelContext context, String name) {
        super(context, name);
        this.registry = registry;
    }

    @Override
    public String toString() {
        return "ejb: " + getName();
    }

    protected Object lookupBean() {
        return registry.lookupByName(getName());
    }

}
