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
package org.apache.camel.dataformat.soap;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.camel.Exchange;

/**
 * Adapter for either SOAP 1.1 or 1.2 implementations.
 */
public interface SoapDataFormatAdapter {

    /**
     * Gets the {@link SoapJaxbDataFormat} SOAP data format.
     */
    SoapJaxbDataFormat getDataFormat();

    /**
     * Gets the JAXB package names where the JAXB generated sources is for either SOAP 1.1 or 1.2 implementations.
     */
    String getSoapPackageName();

    /**
     * Executes the marshal
     *
     * @return soap envelope
     */
    Object doMarshal(Exchange exchange, Object inputObject, OutputStream stream, String soapAction) throws IOException;

    /**
     * Executes the unmarshal
     *
     * @return the payload
     */
    Object doUnmarshal(Exchange exchange, InputStream stream, Object rootObject) throws IOException;

}
