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
package org.apache.camel.component.rmi;

import java.rmi.AccessException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.Registry;

import org.apache.camel.CamelContext;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.bean.ParameterMappingStrategy;
import org.apache.camel.component.bean.RegistryBean;

/**
 * @version 
 */
public class RmiRegistryBean extends RegistryBean {

    private final Registry registry;

    public RmiRegistryBean(CamelContext context, String name, Registry registry) {
        super(context, name);
        this.registry = registry;
    }

    public RmiRegistryBean(CamelContext context, String name, ParameterMappingStrategy parameterMappingStrategy, Registry registry) {
        super(context, name);
        this.registry = registry;
        setParameterMappingStrategy(parameterMappingStrategy);
    }

    @Override
    protected Object lookupBean() throws NoSuchBeanException {
        try {
            return registry.lookup(getName());
        } catch (NotBoundException e) {
            throw new NoSuchBeanException(getName(), e);
        } catch (AccessException e) {
            throw new RuntimeCamelException(e);
        } catch (RemoteException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
