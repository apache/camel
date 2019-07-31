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
package org.apache.camel.cdi;

import java.util.Arrays;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

import org.apache.camel.cdi.xml.ApplicationContextFactoryBean;
import org.apache.camel.model.Constants;

enum XmlCdiJaxbContexts {

    CAMEL_CDI(Constants.JAXB_CONTEXT_PACKAGES, ApplicationContextFactoryBean.class.getPackage().getName());

    private final JAXBContext context;

    XmlCdiJaxbContexts(String... packages) {
        try {
            context = JAXBContext.newInstance(String.join(":", packages));
        } catch (JAXBException cause) {
            throw new IllegalStateException("Error while creating JAXB context for packages " + Arrays.toString(packages), cause);
        }
    }

    JAXBContext instance() {
        return context;
    }
}
