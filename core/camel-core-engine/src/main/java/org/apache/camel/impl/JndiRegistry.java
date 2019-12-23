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
package org.apache.camel.impl;

import java.util.Map;

import javax.naming.Context;
import javax.naming.NamingException;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.jndi.JndiBeanRepository;

/**
 * A {@link Registry} implementation which looks up the objects in JNDI
 *
 * @deprecated use {@link JndiBeanRepository} instead.
 */
@Deprecated
public class JndiRegistry extends JndiBeanRepository implements Registry {

    public JndiRegistry() {
    }

    public JndiRegistry(Map environment) {
        super(environment);
    }

    public JndiRegistry(Context context) {
        super(context);
    }

    public JndiRegistry(boolean standalone) {
        super(standalone);
    }

    @Override
    public void bind(String id, Class<?> type, Object bean) throws RuntimeCamelException {
        try {
            Object object = wrap(bean);
            getContext().bind(id, object);
        } catch (NamingException e) {
            throw new RuntimeCamelException(e);
        }
    }

}
