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
package org.apache.camel.component.cdi;

import java.util.Map;

import org.apache.camel.component.cdi.util.BeanProvider;
import org.apache.camel.spi.Registry;
import org.apache.camel.util.ObjectHelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * CdiBeanRegistry used by Camel to perform lookup into the
 * Cdi BeanManager. The BeanManager must be passed as argument
 * to the CdiRegistry constructor.
 */
public class CdiBeanRegistry implements Registry {
    private final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * @see org.apache.camel.spi.Registry#lookup(java.lang.String)
     */
    @Override
    public Object lookup(final String name) {
        ObjectHelper.notEmpty(name, "name");
        log.trace("Looking up bean using name = [{}] in CDI registry ...", name);

        return BeanProvider.getContextualReference(name, true);
    }

    @Override
    public <T> T lookup(final String name, final Class<T> type) {
        ObjectHelper.notEmpty(name, "name");
        ObjectHelper.notNull(type, "type");
        return type.cast(lookup(name));
    }

    @Override
    public <T> Map<String, T> lookupByType(final Class<T> type) {
        ObjectHelper.notNull(type, "type");
        return BeanProvider.getContextualNamesReferences(type, true, true);
    }

    @Override
    public String toString() {
        return "CdiRegistry[" + System.identityHashCode(this) + "]";
    }
}
