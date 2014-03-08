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
import javax.xml.bind.Marshaller;

import com.sun.xml.bind.marshaller.NamespacePrefixMapper;

/**
 *
 */
@Provider
public class JAXBMarshallerResolver implements ContextResolver<Marshaller> {
    private JAXBContextResolver contextResolver;

    public JAXBMarshallerResolver() throws Exception {
        contextResolver = new JAXBContextResolver();
    }

    public Marshaller getContext(Class<?> aClass) {
        try {
            JAXBContext context = contextResolver.getContext();
            Marshaller marshaller = context.createMarshaller();
            NamespacePrefixMapper namespaceMapper = new NamespacePrefixMapper() {

                /**
                 * Returns a preferred prefix for the given namespace URI.
                 *
                 * This method is intended to be overrided by a derived class.
                 *
                 * @param namespaceUri
                 *      The namespace URI for which the prefix needs to be found.
                 *      Never be null. "" is used to denote the default namespace.
                 * @param suggestion
                 *      When the content tree has a suggestion for the prefix
                 *      to the given namespaceUri, that suggestion is passed as a
                 *      parameter. Typically this value comes from QName.getPrefix()
                 *      to show the preference of the content tree. This parameter
                 *      may be null, and this parameter may represent an already
                 *      occupied prefix.
                 * @param requirePrefix
                 *      If this method is expected to return non-empty prefix.
                 *      When this flag is true, it means that the given namespace URI
                 *      cannot be set as the default namespace.
                 *
                 * @return
                 *      null if there's no preferred prefix for the namespace URI.
                 *      In this case, the system will generate a prefix for you.
                 *
                 *      Otherwise the system will try to use the returned prefix,
                 *      but generally there's no guarantee if the prefix will be
                 *      actually used or not.
                 *
                 *      return "" to map this namespace URI to the default namespace.
                 *      Again, there's no guarantee that this preference will be
                 *      honored.
                 *
                 *      If this method returns "" when requirePrefix=true, the return
                 *      value will be ignored and the system will generate one.
                 */
                @Override
                public String getPreferredPrefix(String namespaceUri, String suggestion, boolean requirePrefix) {
                    if (namespaceUri.equals("http://camel.apache.org/schema/web")) {
                        return "w";
                    } else if (namespaceUri.equals("http://camel.apache.org/schema/spring")) {
                        if (requirePrefix) {
                            return "c";
                        }
                        return "";
                    } else {
                        return suggestion;
                    }
                }

            };
            marshaller.setProperty("com.sun.xml.bind.namespacePrefixMapper", namespaceMapper);
            return marshaller;
        } catch (JAXBException e) {
            throw new RuntimeException(e);
        }
    }
}
