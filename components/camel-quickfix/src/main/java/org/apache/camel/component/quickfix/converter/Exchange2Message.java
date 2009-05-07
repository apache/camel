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

import org.apache.camel.Converter;
import org.apache.camel.converter.IOConverter;
import quickfix.InvalidMessage;
import quickfix.Message;
import quickfix.MessageCracker;

/**
 * @author Anton Arhipov
 */
@Converter
public class Exchange2Message extends MessageCracker {
    public static final int BUFFER_SIZE = 8192; 
    @Converter
    public static Message convert(InputStream in) throws IOException, InvalidMessage {        
        byte buffer[] = new byte[BUFFER_SIZE];
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int count;
        count = in.read(buffer);
        while (count != -1) {
            baos.write(buffer, 0, count);
            count = in.read(buffer);
        }

        String str = baos.toString("ISO-8859-1");
        in.close();
        return new Message(str);
    }

    @Converter
    public static InputStream toInputStream(Message message) throws Exception {
        return IOConverter.toInputStream(toString(message).getBytes());
    }

    @Converter
    public static String toString(Message message) throws IOException {
        return message.toString();
    }

}
