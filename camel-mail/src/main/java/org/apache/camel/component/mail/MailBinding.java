/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.mail;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.util.Map;
import java.util.Set;

/**
 * A Strategy used to convert between a Camel {@Exchange} and {@Message} to and from a
 * Mail {@link MimeMessage}
 *
 * @version $Revision: 521240 $
 */
public class MailBinding {
    public void populateMailMessage(MimeMessage mimeMessage, MailExchange exchange) {
        try {
            appendMailHeaders(mimeMessage, exchange.getIn());
            mimeMessage.setContent(exchange.getIn().getBody(), "body");
        }
        catch (Exception e) {
            throw new RuntimeMailException("Failed to populate body due to: " + e + ". Exchange: " + exchange, e);
        }
    }

    /**
     * Extracts the body from the Mail message
     *
     * @param exchange
     * @param message
     */
    public Object extractBodyFromMail(MailExchange exchange, Message message) {
        try {
            return message.getContent();
        }
        catch (Exception e) {
            throw new RuntimeMailException("Failed to extract body due to: " + e + ". Message: " + message, e);
        }
    }

    /**
     * Appends the Mail headers from the Camel {@link MailMessage}
     */
    protected void appendMailHeaders(MimeMessage mimeMessage, MailMessage camelMessage) throws MessagingException {
        Set<Map.Entry<String, Object>> entries = camelMessage.getHeaders().entrySet();
        for (Map.Entry<String, Object> entry : entries) {
            String headerName = entry.getKey();
            Object headerValue = entry.getValue();
            if (headerValue != null) {
                if (shouldOutputHeader(camelMessage, headerName, headerValue)) {
                    mimeMessage.setHeader(headerName, headerValue.toString());
                }
            }
        }
    }

    /**
     * Strategy to allow filtering of headers which are put on the Mail message
     */
    protected boolean shouldOutputHeader(MailMessage camelMessage, String headerName, Object headerValue) {
        return true;
    }
}
