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
package org.apache.camel.osgi;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

/**
 * Created by IntelliJ IDEA.
 * User: gnodet
 * Date: Sep 20, 2007
 * Time: 11:24:23 AM
 * To change this template use File | Settings | File Templates.
 */
public class CamelNamespaceHandler extends org.apache.camel.spring.handler.CamelNamespaceHandler {

    public void init() {
        super.init();
        registerParser("camelContext", new CamelContextBeanDefinitionParser(CamelContextFactoryBean.class));
    }

    protected JAXBContext createJaxbContext() throws JAXBException {
        return JAXBContext.newInstance("org.apache.camel.osgi:" + JAXB_PACKAGES);
    }

}
