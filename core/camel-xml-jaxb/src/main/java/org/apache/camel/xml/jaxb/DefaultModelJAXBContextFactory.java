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
package org.apache.camel.xml.jaxb;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

import org.apache.camel.model.Constants;
import org.apache.camel.spi.ModelJAXBContextFactory;
import org.apache.camel.spi.annotations.JdkService;

/**
 * Default implementation of
 * {@link org.apache.camel.spi.ModelJAXBContextFactory}.
 */
@JdkService(ModelJAXBContextFactory.FACTORY)
public class DefaultModelJAXBContextFactory implements ModelJAXBContextFactory {

    private volatile JAXBContext context;

    @Override
    public JAXBContext newJAXBContext() throws JAXBException {
        if (context == null) {
            synchronized (this) {
                if (context == null) {
                    context = JAXBContext.newInstance(getPackages(), getClassLoader());
                }
            }
        }
        return context;
    }

    protected String getPackages() {
        return Constants.JAXB_CONTEXT_PACKAGES;
    }

    protected ClassLoader getClassLoader() {
        return getClass().getClassLoader();
    }
}
