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
package org.apache.camel.converter.soap.name;

import javax.xml.namespace.QName;
import javax.xml.ws.WebFault;

import org.apache.camel.spi.ClassResolver;

/**
 * Determine element name for an exception
 */
public class ExceptionNameStrategy implements ElementNameStrategy {
    
    /**
     * @return QName from exception class by evaluating the WebFault annotataion
     */
    public QName findQNameForSoapActionOrType(String soapAction, Class<?> type, ClassResolver classResolver) {
        WebFault webFault = type.getAnnotation(WebFault.class);
        if (webFault == null || webFault.targetNamespace() == null) {
            throw new RuntimeException("The type " + type.getName() + " needs to have an WebFault annotation with name and targetNamespace");
        }
        return new QName(webFault.targetNamespace(), webFault.name());
    }
}
