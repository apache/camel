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
package org.apache.camel;

import java.util.Map;
import java.util.Set;

import javax.activation.DataHandler;

/**
 * Implements the <a
 * href="http://activemq.apache.org/camel/message.html">Message</a> pattern and
 * represents an inbound or outbound message as part of an {@link Exchange}
 *
 * @version $Revision$
 */
public interface Message {

    /**
     * Returns the id of the message
     *
     * @return the id of the message
     */
    String getMessageId();

    /**
     * Sets the id of the message
     *
     * @param messageId
     */
    void setMessageId(String messageId);

    /**
     * Returns the exchange this message is related to
     */
    Exchange getExchange();

    /**
     * Accesses a specific header
     *
     * @param name  name of header
     * @return object header associated with the name
     */
    Object getHeader(String name);

    /**
     * Returns a header associated with this message by name and specifying the
     * type required
     *
     * @param name the name of the header
     * @param type the type of the header
     * @return the value of the given header or null if there is no property for
     *         the given name or it cannot be converted to the given type
     */
    <T> T getHeader(String name, Class<T> type);

    /**
     * Sets a header on the message
     *
     * @param name of the header
     * @param value to associate with the name
     */
    void setHeader(String name, Object value);

    /**
     * Removes the named header from this message
     *
     * @param name
     * @return the old value of the header
     */
    Object removeHeader(String name);

    /**
     * Returns all of the headers associated with the message
     *
     * @return all the headers in a Map
     */
    Map<String, Object> getHeaders();

    /**
     * Set all the headers associated with this message
     *
     * @param headers
     */
    void setHeaders(Map<String, Object> headers);

    /**
     * Returns the body of the message as a POJO
     *
     * @return the body of the message
     */
    Object getBody();

    /**
     * Returns the body as the specified type
     *
     * @param type the type that the body
     * @return the body of the message as the specified type
     */
    <T> T getBody(Class<T> type);

    /**
     * Sets the body of the message
     */
    void setBody(Object body);

    /**
     * Sets the body of the message as a specific type
     */
    <T> void setBody(Object body, Class<T> type);

    /**
     * Creates a copy of this message so that it can be used and possibly
     * modified further in another exchange
     *
     * @return a new message instance copied from this message
     */
    Message copy();

    /**
     * Copies the contents of the other message into this message
     */
    void copyFrom(Message message);

    /**
     * Returns the attachment specified by the id
     *
     * @param id        the id under which the attachment is stored
     * @return          the data handler for this attachment or null
     */
    DataHandler getAttachment(String id);

    /**
     * Returns a set of attachment names of the message
     *
     * @return  a set of attachment names
     */
    Set<String> getAttachmentNames();

    /**
     * Removes the attachment specified by the id
     *
     * @param id        the id of the attachment to remove
     */
    void removeAttachment(String id);

    /**
     * Adds an attachment to the message using the id
     *
     * @param id        the id to store the attachment under
     * @param content   the data handler for the attachment
     */
    void addAttachment(String id, DataHandler content);

    /**
     * Returns all attachments of the message
     *
     * @return  the attachments in a map or null
     */
    Map<String, DataHandler> getAttachments();

    /**
     * Set all the attachments associated with this message
     *
     * @param attachments
     */
    void setAttachments(Map<String, DataHandler> attachments);

    /**
     * Returns <tt>true</tt> if this message has any attachments.
     */
    boolean hasAttachments();
}
