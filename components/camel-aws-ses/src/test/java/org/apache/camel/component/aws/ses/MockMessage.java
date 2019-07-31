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
package org.apache.camel.component.aws.ses;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Enumeration;

import javax.activation.DataHandler;
import javax.mail.Address;
import javax.mail.Flags;
import javax.mail.Header;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;

public class MockMessage extends Message {

    @Override
    public int getSize() throws MessagingException {
        return 0;
    }

    @Override
    public int getLineCount() throws MessagingException {
        return 0;
    }

    @Override
    public String getContentType() throws MessagingException {
        return null;
    }

    @Override
    public boolean isMimeType(String mimeType) throws MessagingException {
        return false;
    }

    @Override
    public String getDisposition() throws MessagingException {
        return null;
    }

    @Override
    public void setDisposition(String disposition) throws MessagingException {
    }

    @Override
    public String getDescription() throws MessagingException {
        return null;
    }

    @Override
    public void setDescription(String description) throws MessagingException {
    }

    @Override
    public String getFileName() throws MessagingException {
        return null;
    }

    @Override
    public void setFileName(String filename) throws MessagingException {
    }

    @Override
    public InputStream getInputStream() throws IOException, MessagingException {
        return null;
    }

    @Override
    public DataHandler getDataHandler() throws MessagingException {
        return null;
    }

    @Override
    public Object getContent() throws IOException, MessagingException {
        return null;
    }

    @Override
    public void setDataHandler(DataHandler dh) throws MessagingException {
    }

    @Override
    public void setContent(Object obj, String type) throws MessagingException {
    }

    @Override
    public void setText(String text) throws MessagingException {
    }
    
    @Override
    public void setContent(Multipart mp) throws MessagingException {
    }

    @Override
    public void writeTo(OutputStream os) throws IOException, MessagingException {
    }

    @Override
    public String[] getHeader(String headername) throws MessagingException {
        return null;
    }

    @Override
    public void setHeader(String headername, String headervalue)
        throws MessagingException {
    }

    @Override
    public void addHeader(String headername, String headervalue)
        throws MessagingException {
    }

    @Override
    public void removeHeader(String headername) throws MessagingException {
    }

    @Override
    public Enumeration<Header> getAllHeaders() throws MessagingException {
        return null;
    }

    @Override
    public Enumeration<Header> getMatchingHeaders(String[] headernames)
        throws MessagingException {
        return null;
    }

    @Override
    public Enumeration<Header> getNonMatchingHeaders(String[] headernames)
        throws MessagingException {
        return null;
    }

    @Override
    public Address[] getFrom() throws MessagingException {
        return null;
    }

    @Override
    public void setFrom() throws MessagingException {
    }

    @Override
    public void setFrom(Address address) throws MessagingException {
    }

    @Override
    public void addFrom(Address[] addresses) throws MessagingException {
    }

    @Override
    public Address[] getRecipients(RecipientType type)
        throws MessagingException {
        return null;
    }

    @Override
    public void setRecipients(RecipientType type, Address[] addresses)
        throws MessagingException {
    }

    @Override
    public void addRecipients(RecipientType type, Address[] addresses)
        throws MessagingException {
    }

    @Override
    public String getSubject() throws MessagingException {
        return null;
    }

    @Override
    public void setSubject(String subject) throws MessagingException {
    }

    @Override
    public Date getSentDate() throws MessagingException {
        return null;
    }

    @Override
    public void setSentDate(Date date) throws MessagingException {
    }

    @Override
    public Date getReceivedDate() throws MessagingException {
        return null;
    }

    @Override
    public Flags getFlags() throws MessagingException {
        return null;
    }

    @Override
    public void setFlags(Flags flag, boolean set) throws MessagingException {
    }

    @Override
    public Message reply(boolean replyToAll) throws MessagingException {
        return null;
    }

    @Override
    public void saveChanges() throws MessagingException {
    }
}
