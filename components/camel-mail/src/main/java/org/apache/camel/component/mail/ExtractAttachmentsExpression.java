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
package org.apache.camel.component.mail;

import javax.activation.DataHandler;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.sun.mail.util.BASE64DecoderStream;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultMessage;
import org.apache.camel.support.ExpressionAdapter;

/**
 * A {@link org.apache.camel.Expression} which can be used to split a {@link MailMessage}
 * per attachment. For example if a mail message has 5 attachments, then this
 * expression will return a <tt>List&lt;Message&gt;</tt> that contains 5 {@link Message}
 * where the body of each Message is a byte[] containing an attachment.
 */
public class ExtractAttachmentsExpression extends ExpressionAdapter {

    @Override
    public Object evaluate(Exchange exchange) {
        // must use getAttachments to ensure attachments is initially populated
        if (exchange.getIn().getAttachments().isEmpty()) {
            return null;
        }

        try {
            return convertMimeparts(exchange.getIn());
        } catch (Exception e) {
            throw new RuntimeCamelException("Unable to extract attachments from MimeMultipart message", e);
        }
    }

    private List<Message> convertMimeparts(Message inMessage) throws Exception {
        List<Message> outMessages = new ArrayList<>();
        for (Map.Entry<String, DataHandler> entry : inMessage.getAttachments().entrySet()) {
            final Message outMessage = new DefaultMessage();
            final String key = entry.getKey();
            outMessage.setHeader("CamelSplitAttachmentName", key);
            Object attachment = inMessage.getAttachment(key).getContent();
            if (attachment instanceof InputStream) {
                outMessage.setBody(readMimePart((InputStream) attachment));
                outMessages.add(outMessage);
            }
        }
        return outMessages;
    }

    private byte[] readMimePart(InputStream mimePartStream) throws Exception {
        //  mimePartStream could be base64 encoded, or not, but we dont need to worry about it as
        // camel is smart enough to wrap it in a decoder stream (eg Base64DecoderStream) when required
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int len;
        byte[] buf = new byte[1024];
        while ((len = mimePartStream.read(buf, 0, 1024)) != -1) {
            bos.write(buf, 0, len);
        }
        mimePartStream.close();
        return bos.toByteArray();
    }

}
