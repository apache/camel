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
package org.apache.camel.component.xmlsecurity.util;

import javax.xml.crypto.Data;
import javax.xml.crypto.URIDereferencer;
import javax.xml.crypto.URIReference;
import javax.xml.crypto.URIReferenceException;
import javax.xml.crypto.XMLCryptoContext;
import javax.xml.crypto.dom.DOMCryptoContext;
import javax.xml.crypto.dom.DOMURIReference;
import javax.xml.crypto.dsig.XMLSignatureFactory;

/**
 * URI Dereferencer which allows only same document URI references via ids.
 */
public final class SameDocumentUriDereferencer implements URIDereferencer {

    private static final URIDereferencer INSTANCE = new SameDocumentUriDereferencer();
    
    private SameDocumentUriDereferencer() {
        // singelton
    }

    public static URIDereferencer getInstance() {
        return INSTANCE;
    }

    

    public Data dereference(URIReference uriReference, XMLCryptoContext context) throws URIReferenceException {

        if (uriReference == null) {
            throw new NullPointerException("Parameter 'uriReference' cannot be null.");
        }

        if (context == null) {
            throw new NullPointerException("Parameter 'context' can notbe null.");
        }

        if (!(uriReference instanceof DOMURIReference && context instanceof DOMCryptoContext)) {
            throw new IllegalArgumentException(String.format("This %s implementation supports the DOM XML mechanism only.",
                    URIDereferencer.class.getName()));
        }

        String uriString = uriReference.getURI();

        if (uriString == null) {
            throw new URIReferenceException("Cannot resolve a URI of value 'null'.");
        }

        if (uriString != null && ((uriString.length() != 0 && uriString.charAt(0) == '#') || uriString.isEmpty())) {
            // same document uri
            XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");
            return fac.getURIDereferencer().dereference(uriReference, context);
        }

        throw new URIReferenceException(String.format("URI reference %s not supported", uriString));
    }
}
