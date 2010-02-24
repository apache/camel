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
 * Simply ElementNameStrategy that returns one preset QName. This can be handy
 * for simple asynchronous calls
 */
public class QNameStrategy implements ElementNameStrategy {
    private QName elementName;

    /**
     * Initialize with one QName
     * 
     * @param elmentName
     *            QName to be used for all finds
     */
    public QNameStrategy(QName elmentName) {
        this.elementName = elmentName;
    }

    /**
     * @return preset element name
     */
    public QName findQNameForSoapActionOrType(String soapAction, Class<?> type) {
        return elementName;
    }

    public Class<? extends Exception> findExceptionForFaultName(QName faultName) {
        throw new UnsupportedOperationException("Exception lookup is not supported for QNameStrategy");
    }

}
