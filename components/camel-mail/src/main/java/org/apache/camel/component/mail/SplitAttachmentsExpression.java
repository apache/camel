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
package org.apache.camel.component.mail;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.attachment.Attachment;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.support.DefaultMessage;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.camel.util.IOHelper;

/**
 * A {@link org.apache.camel.Expression} which can be used to split a {@link MailMessage}
 * per attachment. For example if a mail message has 5 attachments, then this
 * expression will return a <tt>List&lt;Message&gt;</tt> that contains 5 {@link Message}.
 * The message is split:
 * <table>
 *   <tr>
 *     <td>As a byte[] or String</td>
 *     <td>
 *       The attachments are split into new messages as the body. This allows the split messages to be easily used by
 *       other processors / routes, as many other camel components can work on the byte[] or String, e.g. it can be written to disk
 *       using camel-file.
 *     </td>
 *   </tr>
 * </table>
 *
 * In both cases the attachment name is written to a the camel header &quot;CamelSplitAttachmentId&quot;
 */
public class SplitAttachmentsExpression extends ExpressionAdapter {

    public static final String HEADER_NAME = "CamelSplitAttachmentId";

    public SplitAttachmentsExpression() {
    }

    @Override
    public Object evaluate(Exchange exchange) {
        // must use getAttachments to ensure attachments is initial populated
        if (!exchange.getIn(AttachmentMessage.class).hasAttachments()) {
            return null;
        }

        try {
            List<Message> answer = new ArrayList<>();
            AttachmentMessage inMessage = exchange.getIn(AttachmentMessage.class);
            for (Map.Entry<String, Attachment> entry : inMessage.getAttachmentObjects().entrySet()) {
                Message attachmentMessage = extractAttachment(entry.getValue(), entry.getKey(), exchange.getContext());
                if (attachmentMessage != null) {
                    answer.add(attachmentMessage);
                }
            }

            // clear attachments on original message after we have split them
            inMessage.getAttachmentObjects().clear();

            return answer;
        } catch (Exception e) {
            throw new RuntimeCamelException("Unable to split attachments from MimeMultipart message", e);
        }
    }

    private Message extractAttachment(Attachment attachment, String attachmentName, CamelContext camelContext) throws Exception {
        final Message outMessage = new DefaultMessage(camelContext);
        outMessage.setHeader(HEADER_NAME, attachmentName);
        Object obj = attachment.getDataHandler().getContent();
        if (obj instanceof InputStream) {
            outMessage.setBody(readMimePart((InputStream) obj));
            return outMessage;
        } else if (obj instanceof String || obj instanceof byte[]) {
            outMessage.setBody(obj);
            return outMessage;
        } else {
            return null;
        }
    }

    private byte[] readMimePart(InputStream mimePartStream) throws Exception {
        // mimePartStream could be base64 encoded, or not, but we don't need to worry about it as
        // Camel is smart enough to wrap it in a decoder stream (eg Base64DecoderStream) when required
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        IOHelper.copyAndCloseInput(mimePartStream, bos);
        return bos.toByteArray();
    }

}
