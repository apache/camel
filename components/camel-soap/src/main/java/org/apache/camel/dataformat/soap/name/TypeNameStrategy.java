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
package org.apache.camel.dataformat.soap.name;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSchema;
import javax.xml.bind.annotation.XmlType;
import javax.xml.namespace.QName;

import org.apache.camel.util.ObjectHelper;

/**
 * Strategy to determine the marshalled element name by looking at the
 * annotations of the class to be marshalled
 */
public class TypeNameStrategy implements ElementNameStrategy {

    /**
     * @return determine element name by using the XmlType.name() of the type to
     *         be marshalled and the XmlSchema.namespace() of the package-info
     */
    @Override
    public QName findQNameForSoapActionOrType(String soapAction, Class<?> type) {
        XmlType xmlType = type.getAnnotation(XmlType.class);
        if (xmlType == null || xmlType.name() == null) {
            throw new RuntimeException("The type " + type.getName() + " needs to have an XmlType annotation with name");
        }
        String nameSpace = xmlType.namespace();
        if ("##default".equals(nameSpace)) {
            XmlSchema xmlSchema = type.getPackage().getAnnotation(XmlSchema.class);
            if (xmlSchema != null) {
                nameSpace = xmlSchema.namespace();
            }
        }
        // prefer name from the XmlType, and fallback to XmlRootElement
        String localName = xmlType.name();
        if (ObjectHelper.isEmpty(localName)) {
            XmlRootElement root = type.getAnnotation(XmlRootElement.class);
            if (root != null) {
                localName = root.name();
            }
        }
        return new QName(nameSpace, localName);
    }

    @Override
    public Class<? extends Exception> findExceptionForFaultName(QName faultName) {
        throw new UnsupportedOperationException("Exception lookup is not supported for TypeNameStrategy");
    }

}
