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

import java.util.ArrayList;
import java.util.List;

import javax.xml.crypto.dsig.Reference;

import org.apache.camel.component.xmlsecurity.api.DefaultXmlSignature2Message;

/**
 * Removes all references whose URIs contain "propert" from the relevant
 * references for the mapping to the camel message.
 */
public class XmlSignature2Message2MessageWithTimestampProperty extends DefaultXmlSignature2Message {

    protected List<Reference> getReferencesForMessageMapping(Input input) throws Exception {

        List<Reference> result = new ArrayList<>(1);
        for (Reference ref : input.getReferences()) {
            if (ref.getURI() != null && ref.getURI().contains("propert")) {
                // do not add 
            } else {
                result.add(ref);
            }
        }
        return result;
    }

}
