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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.activation.DataHandler;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.ExpressionAdapter;

/**
 * A {@link org.apache.camel.Expression} which can be used to split a {@link MailMessage}
 * per attachment. For example if a mail message has 5 attachments, then this
 * expression will return a <tt>List&lt;Message&gt;</tt> that contains 5 {@link Message}
 * and each have a single attachment from the source {@link MailMessage}.
 */
public class SplitAttachmentsExpression extends ExpressionAdapter {

    @Override
    public Object evaluate(Exchange exchange) {
        // must use getAttachments to ensure attachments is initial populated
        if (exchange.getIn().getAttachments().isEmpty()) {
            return null;
        }

        // we want to provide a list of messages with 1 attachment per mail
        List<Message> answer = new ArrayList<Message>();

        for (Map.Entry<String, DataHandler> entry : exchange.getIn().getAttachments().entrySet()) {
            final Message copy = exchange.getIn().copy();
            final String key = entry.getKey();
            Map<String, DataHandler> attachments = copy.getAttachments();
            attachments.clear();
            attachments.put(key, entry.getValue());
            copy.setHeader("CamelSplitAttachmentId", key);
            answer.add(copy);
        }

        return answer;
    }
}
