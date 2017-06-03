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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.Attachment;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultMessage;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.camel.util.IOHelper;

/**
 * A {@link org.apache.camel.Expression} which can be used to split a {@link MailMessage}
 * per attachment. For example if a mail message has 5 attachments, then this
 * expression will return a <tt>List&lt;Message&gt;</tt> that contains 5 {@link Message}.
 * The message can be split 2 ways:
 * <table>
 *   <tr>
 *     <td>As an attachment</td>
 *     <td>
 *       The message is split into cloned messages, each has only one attachment.  The mail attachment in each message
 *       remains unprocessed.
 *     </td>
 *   </tr>
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

    private boolean extractAttachments;

    public SplitAttachmentsExpression() {
    }

    public SplitAttachmentsExpression(boolean extractAttachments) {
        this.extractAttachments = extractAttachments;
    }

    @Override
    public Object evaluate(Exchange exchange) {
        // must use getAttachments to ensure attachments is initial populated
        if (exchange.getIn().getAttachments().isEmpty()) {
            return null;
        }

        try {
            List<Message> answer = new ArrayList<Message>();
            Message inMessage = exchange.getIn();
            for (Map.Entry<String, Attachment> entry : inMessage.getAttachmentObjects().entrySet()) {
                Message attachmentMessage;
                if (extractAttachments) {
                    attachmentMessage = extractAttachment(inMessage, entry.getKey());
                } else {
                    attachmentMessage = splitAttachment(inMessage, entry.getKey(), entry.getValue());
                }

                if (attachmentMessage != null) {
                    answer.add(attachmentMessage);
                }
            }

            return answer;
        } catch (Exception e) {
            throw new RuntimeCamelException("Unable to split attachments from MimeMultipart message", e);
        }
    }

    private Message splitAttachment(Message inMessage, String attachmentName, Attachment attachmentHandler) {
        final Message copy = inMessage.copy();
        Map<String, Attachment> attachments = copy.getAttachmentObjects();
        attachments.clear();
        attachments.put(attachmentName, attachmentHandler);
        copy.setHeader(HEADER_NAME, attachmentName);
        return copy;
    }

    private Message extractAttachment(Message inMessage, String attachmentName) throws Exception {
        final Message outMessage = new DefaultMessage(inMessage.getExchange().getContext());
        outMessage.setHeader(HEADER_NAME, attachmentName);
        Object attachment = inMessage.getAttachment(attachmentName).getContent();
        if (attachment instanceof InputStream) {
            outMessage.setBody(readMimePart((InputStream) attachment));
            return outMessage;
        } else if (attachment instanceof String || attachment instanceof byte[]) {
            outMessage.setBody(attachment);
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


    public boolean isExtractAttachments() {
        return extractAttachments;
    }

    public void setExtractAttachments(boolean extractAttachments) {
        this.extractAttachments = extractAttachments;
    }
}
