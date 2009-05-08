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
package org.apache.camel.component.quickfix.converter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.component.quickfix.QuickfixApplication;
import org.apache.camel.converter.IOConverter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import quickfix.InvalidMessage;
import quickfix.Message;

/**
 * @author Anton Arhipov
 */
@Converter
public final class QuickFixConverter {
        
    public static final int BUFFER_SIZE = 8192; 
    private static final Log LOG = LogFactory.getLog(QuickFixConverter.class);
    
    private QuickFixConverter() {
        // helper class
    }
    
    @Converter
    public static Message convert(InputStream in, Exchange exchange) throws IOException, InvalidMessage {        
        byte buffer[] = new byte[BUFFER_SIZE];
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int count;
        count = in.read(buffer);
        while (count != -1) {
            baos.write(buffer, 0, count);
            count = in.read(buffer);
        }
        String str = null;
        String charsetName = exchange.getProperty(Exchange.CHARSET_NAME, String.class);
        if (charsetName != null) {
            try {
                str = baos.toString(charsetName);
            } catch (UnsupportedEncodingException e) {
                LOG.warn("Cannot convert the byte[] into String with the charset: " + charsetName, e);
                str = baos.toString();
            }
        } else {
            // using the default encoding 
            str = baos.toString();
        }
        in.close();
        return new Message(str);
    }

    @Converter
    public static InputStream toInputStream(Message message) throws Exception {
        System.out.println("Calling to inputStream");
        return IOConverter.toInputStream(toString(message).getBytes());
    }

    @Converter
    public static String toString(Message message) throws IOException {
        System.out.println("Calling to string");
        return message.toString();
    }

}
