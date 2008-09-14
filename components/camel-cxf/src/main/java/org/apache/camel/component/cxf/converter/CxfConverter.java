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
package org.apache.camel.component.cxf.converter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;

import org.apache.camel.Converter;
import org.apache.camel.Endpoint;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.cxf.message.MessageContentsList;


/**
 * The <a href="http://activemq.apache.org/camel/type-converter.html">Type Converters</a>
 * for CXF related types' converting .
 *
 * @version $Revision$
 */
@Converter
public final class CxfConverter {
    private static final Log LOG = LogFactory.getLog(CxfConverter.class);

    private CxfConverter() {
        // Helper class
    }

    @Converter
    public static Object[] toArray(final MessageContentsList list) throws Exception {
        if (list == null) {
            throw new IllegalArgumentException("The MessageChannel is null");
        }
        return list.toArray();
    }

    @Converter
    public static MessageContentsList toMessageContentsList(final Object[] array) {
        if (array != null) {
            return new MessageContentsList(array);
        } else {
            return new MessageContentsList();
        }
    }

    @Converter
    public static String soapMessageToString(final SOAPMessage soapMessage) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            soapMessage.writeTo(baos);
        } catch (Exception e) {
            LOG.error("Get the exception when converting the SOAPMessage into String, the exception is " + e);
        }
        return baos.toString();
    }





}
