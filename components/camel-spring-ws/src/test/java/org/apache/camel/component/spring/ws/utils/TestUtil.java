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
package org.apache.camel.component.spring.ws.utils;

import java.io.IOException;
import java.util.Iterator;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;

import org.junit.Assert;
import org.springframework.util.StringUtils;
import org.springframework.ws.client.core.SourceExtractor;
import org.springframework.ws.soap.SoapHeader;
import org.springframework.ws.soap.SoapHeaderElement;
import org.springframework.ws.soap.SoapMessage;
import org.springframework.ws.soap.addressing.core.MessageAddressingProperties;
import org.springframework.ws.soap.addressing.version.Addressing10;
import org.springframework.ws.soap.addressing.version.Addressing200408;
import org.springframework.ws.soap.addressing.version.AddressingVersion;

public final class TestUtil {

    public static final SourceExtractor<Object> NOOP_SOURCE_EXTRACTOR = new SourceExtractor<Object>() {
        public Object extractData(Source source) throws IOException, TransformerException {
            return null;
        }
    };

    private TestUtil() {
    }

    /**
     * Compare the to string ignoring new lines symbol. Handy if you need to
     * compare some text coming from 2 different OS.
     */
    public static void assertEqualsIgnoreNewLinesSymbol(String expected, String actual) {
        Assert.assertEquals(StringUtils.deleteAny(expected, "\n\r"), StringUtils.deleteAny(actual, "\n\r"));

    }

    /**
     * Retrieve a WS-Addressing properties from the soapMessage
     * 
     * @param messageContext
     * @return
     */
    public static MessageAddressingProperties getWSAProperties(SoapMessage soapMessage) {
        AddressingVersion[] versions = new AddressingVersion[] {new Addressing200408(), new Addressing10()};

        for (AddressingVersion version : versions) {
            if (supports(version, soapMessage)) {
                MessageAddressingProperties requestMap = version.getMessageAddressingProperties(soapMessage);
                return requestMap;
            }
        }
        return null;
    }

    private static boolean supports(AddressingVersion version, SoapMessage request) {
        SoapHeader header = request.getSoapHeader();
        if (header != null) {
            for (Iterator<SoapHeaderElement> iterator = header.examineAllHeaderElements(); iterator.hasNext();) {
                SoapHeaderElement headerElement = iterator.next();
                if (version.understands(headerElement)) {
                    return true;
                }
            }
        }
        return false;
    }
}
