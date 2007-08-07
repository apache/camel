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
package org.apache.camel.component.jms;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.util.Enumeration;

import javax.jms.BytesMessage;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageEOFException;
import javax.jms.ObjectMessage;
import javax.jms.StreamMessage;
import javax.jms.TextMessage;

import org.apache.camel.Converter;
import org.apache.camel.converter.NIOConverter;

/**
 * Some simple payload conversions to I/O <a
 * href="http://activemq.apache.org/camel/type-converter.html">Type Converters</a>
 * 
 * @version $Revision: 533630 $
 */

@Converter
public final class JmsIOConverter {
    
    private JmsIOConverter() {        
    }
    
    /**
     * @param message
     * @return a ByteBuffer
     * @throws Exception
     */
    @Converter
    public static ByteBuffer toByteBuffer(final Message message) throws Exception {

        if (message instanceof TextMessage) {
            final String text = ((TextMessage)message).getText();
            return NIOConverter.toByteBuffer(text);
        }
        if (message instanceof BytesMessage) {
            final BytesMessage bmsg = (BytesMessage)message;
            final int len = (int)bmsg.getBodyLength();
            final byte[] data = new byte[len];
            bmsg.readBytes(data, len);
            return NIOConverter.toByteBuffer(data);

        }
        if (message instanceof StreamMessage) {
            final StreamMessage msg = (StreamMessage)message;
            final ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
            final DataOutputStream dataOut = new DataOutputStream(bytesOut);
            try {
                while (true) {
                    final Object obj = msg.readObject();
                    writeData(dataOut, obj);
                }
            } catch (MessageEOFException e) {
                // we have no other way of knowing the end of the message
            }
            dataOut.close();
            return NIOConverter.toByteBuffer(bytesOut.toByteArray());
        }
        if (message instanceof MapMessage) {
            final MapMessage msg = (MapMessage)message;
            final ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
            final DataOutputStream dataOut = new DataOutputStream(bytesOut);
            for (final Enumeration en = msg.getMapNames(); en.hasMoreElements();) {
                final Object obj = msg.getObject(en.nextElement().toString());
                writeData(dataOut, obj);
            }
            dataOut.close();
            return NIOConverter.toByteBuffer(bytesOut.toByteArray());
        }
        if (message instanceof ObjectMessage) {
            ObjectMessage objMessage = (ObjectMessage)message;
            Object object = objMessage.getObject();
            ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
            ObjectOutputStream objectOut = new ObjectOutputStream(bytesOut);
            objectOut.writeObject(object);
            objectOut.close();
            return NIOConverter.toByteBuffer(bytesOut.toByteArray());
        }
        return null;

    }

    private static void writeData(DataOutputStream dataOut, Object data) throws Exception {

        if (data instanceof byte[]) {
            dataOut.write((byte[])data);
        } else if (data instanceof String) {
            dataOut.writeUTF(data.toString());
        } else if (data instanceof Double) {
            dataOut.writeDouble(((Double)data).doubleValue());
        } else if (data instanceof Float) {
            dataOut.writeFloat(((Float)data).floatValue());
        } else if (data instanceof Long) {
            dataOut.writeLong(((Long)data).longValue());
        } else if (data instanceof Integer) {
            dataOut.writeInt(((Integer)data).intValue());
        } else if (data instanceof Short) {
            dataOut.writeShort(((Short)data).shortValue());
        } else if (data instanceof Character) {
            dataOut.writeChar(((Character)data).charValue());
        } else if (data instanceof Byte) {
            dataOut.writeByte(((Byte)data).byteValue());
        } else if (data instanceof Boolean) {
            dataOut.writeBoolean(((Boolean)data).booleanValue());
        }

    }
}
