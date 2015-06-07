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
package org.apache.camel.component.xmlsecurity.api;

/**
 * Class representing the Encapsulated PKI Data of the XAdES specification.
 * 
 */
public class XAdESEncapsulatedPKIData {

    private final String base64Conent;

    private final String encoding;

    private final String id;

    /**
     * Constructor
     * 
     * @param base64Conent
     *            base64 encoded content
     * @param encoding
     *            , can be <code>null</code> or empty; encoding
     *            http://uri.etsi.org/01903/v1.2.2#DER for denoting that the
     *            original PKI data were ASN.1 data encoded in DER.
     *            http://uri.etsi.org/01903/v1.2.2#BER for denoting that the
     *            original PKI data were ASN.1 data encoded in BER.
     *            http://uri.etsi.org/01903/v1.2.2#CER for denoting that the
     *            original PKI data were ASN.1 data encoded in CER.
     *            http://uri.etsi.org/01903/v1.2.2#PER for denoting that the
     *            original PKI data were ASN.1 data encoded in PER.
     *            http://uri.etsi.org/01903/v1.2.2#XER for denoting that the
     *            original PKI data were ASN.1 data encoded in XER.
     * 
     * @param id
     *            ID for the Id attribute, can be <code>null</code>
     * @throws IllegalArgumentException
     *             if <tt>base64Conent</tt> is <code>null</code> or empty
     */
    public XAdESEncapsulatedPKIData(String base64Conent, String encoding, String id) {
        if (base64Conent == null || base64Conent.isEmpty()) {
            throw new IllegalArgumentException("Value for parameter 'base64Conent' is null or empty");
        }
        this.base64Conent = base64Conent;
        this.encoding = encoding;
        this.id = id;
    }

    /**
     * Returns the base 64 encoded content. Cannot be <code>null</code> or
     * empty.
     */
    public String getBase64Conent() {
        return base64Conent;
    }

    /**
     * Returns the character encoding of the content. Cannot be
     * <code>null</code> or empty.
     */
    public String getEncoding() {
        return encoding;
    }

    public String getId() {
        return id;
    }

}
