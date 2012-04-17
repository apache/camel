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
package org.apache.camel.web.util;

import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

/**
 * A resolver of the JAXB context primed for the Camel XML languages
 * which supports JSON as well as XML encoding
 */
@Provider
public  class JAXBContextResolver implements ContextResolver<JAXBContext> {
    private final JAXBContext context;
    private String packages;

    public JAXBContextResolver() throws Exception {
        this(org.apache.camel.web.resources.Constants.JAXB_PACKAGES);
    }

    public JAXBContextResolver(String packages) throws JAXBException {
        this.packages = packages;
        this.context = JAXBContext.newInstance(packages);
    }

    public JAXBContext getContext(Class<?> objectType) {
        Package aPackage = objectType.getPackage();
        if (aPackage != null) {
            String name = aPackage.getName();
            if (name.length() > 0) {
                if (packages.contains(name)) {
                    return context;
                }
            }
        }
        return null;
    }

    public String getPackages() {
        return packages;
    }

    public JAXBContext getContext() {
        return context;
    }
}
