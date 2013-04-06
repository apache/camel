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

import javax.xml.namespace.QName;

/**
 * Strategy interface for determining the element name for a SOAP body or fault
 */
public interface ElementNameStrategy {
    
    /**
     * Deterimine element name for given type
     * 
     * @param soapAction
     * @param type
     * @return resolved element name
     */
    QName findQNameForSoapActionOrType(String soapAction, Class<?> type);
    
    /**
     * Determine exception class for given SOAP Fault QName
     * @param faultName
     * @return
     */
    Class<? extends Exception> findExceptionForFaultName(QName faultName);
}
