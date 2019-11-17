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
package org.apache.camel.attachment;

import java.util.Map;
import java.util.Set;

import javax.activation.DataHandler;

import org.apache.camel.Message;

/**
 * Extended {@link Message} for Java Attachment Support (with javax.activation).
 */
public interface AttachmentMessage extends Message {

    /**
     * Returns the attachment specified by the id
     *
     * @param id the id under which the attachment is stored
     * @return the data handler for this attachment or <tt>null</tt>
     */
    DataHandler getAttachment(String id);

    /**
     * Returns the attachment specified by the id
     *
     * @param id the id under which the attachment is stored
     * @return the attachment or <tt>null</tt>
     */
    Attachment getAttachmentObject(String id);

    /**
     * Returns a set of attachment names of the message
     *
     * @return a set of attachment names
     */
    Set<String> getAttachmentNames();

    /**
     * Removes the attachment specified by the id
     *
     * @param id   the id of the attachment to remove
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
     * Adds an attachment to the message using the id
     *
     * @param id        the id to store the attachment under
     * @param content   the attachment
     */
    void addAttachmentObject(String id, Attachment content);

    /**
     * Returns all attachments of the message
     *
     * @return the attachments in a map or <tt>null</tt>
     */
    Map<String, DataHandler> getAttachments();

    /**
     * Returns all attachments of the message
     *
     * @return the attachments in a map or <tt>null</tt>
     */
    Map<String, Attachment> getAttachmentObjects();

    /**
     * Set all the attachments associated with this message
     *
     * @param attachments the attachments
     */
    void setAttachments(Map<String, DataHandler> attachments);

    /**
     * Set all the attachments associated with this message
     *
     * @param attachments the attachments
     */
    void setAttachmentObjects(Map<String, Attachment> attachments);

    /**
     * Returns whether this message has attachments.
     *
     * @return <tt>true</tt> if this message has any attachments.
     */
    boolean hasAttachments();

}
