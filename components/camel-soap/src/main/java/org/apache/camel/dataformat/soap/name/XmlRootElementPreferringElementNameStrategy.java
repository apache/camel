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
package org.apache.camel.dataformat.soap.name;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSchema;
import javax.xml.bind.annotation.XmlType;
import javax.xml.namespace.QName;

import org.apache.camel.util.ObjectHelper;

public class XmlRootElementPreferringElementNameStrategy implements ElementNameStrategy {

    private static final String DEFAULT_NS = "##default";

    @Override
    public QName findQNameForSoapActionOrType(String soapAction, Class<?> type) {
        XmlType xmlType = type.getAnnotation(XmlType.class);
        if (xmlType == null || xmlType.name() == null) {
            throw new RuntimeException("The type " + type.getName() + " needs to have an XmlType annotation with name");
        }
        // prefer name+ns from the XmlRootElement, and fallback to XmlType
        String localName = null;
        String nameSpace = null;

        XmlRootElement root = type.getAnnotation(XmlRootElement.class);
        if (root != null) {
            localName = ObjectHelper.isEmpty(localName) ? root.name() : localName;
            nameSpace = isInValidNamespace(nameSpace) ? root.namespace() : nameSpace;
        }

        if (ObjectHelper.isEmpty(localName)) {
            localName = xmlType.name();
        }

        if (isInValidNamespace(nameSpace)) {
            XmlSchema xmlSchema = type.getPackage().getAnnotation(XmlSchema.class);
            if (xmlSchema != null) {
                nameSpace = xmlSchema.namespace();
            }
        }

        if (isInValidNamespace(nameSpace)) {
            nameSpace = xmlType.namespace();
        }

        if (ObjectHelper.isEmpty(localName) || isInValidNamespace(nameSpace)) {
            throw new IllegalStateException("Unable to determine localName or namespace for type <" + type.getName() + ">");
        }
        return new QName(nameSpace, localName);
    }

    private boolean isInValidNamespace(String namespace) {
        return ObjectHelper.isEmpty(namespace) || DEFAULT_NS.equalsIgnoreCase(namespace);
    }

    @Override
    public Class<? extends Exception> findExceptionForFaultName(QName faultName) {
        throw new UnsupportedOperationException("Exception lookup is not supported");
    }

}
