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
package org.apache.camel.component.aws2.ses;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Enumeration;

import jakarta.activation.DataHandler;
import jakarta.mail.Address;
import jakarta.mail.Flags;
import jakarta.mail.Header;
import jakarta.mail.Message;
import jakarta.mail.Multipart;

public class MockMessage extends Message {

    @Override
    public int getSize() {
        return 0;
    }

    @Override
    public int getLineCount() {
        return 0;
    }

    @Override
    public String getContentType() {
        return null;
    }

    @Override
    public boolean isMimeType(String mimeType) {
        return false;
    }

    @Override
    public String getDisposition() {
        return null;
    }

    @Override
    public void setDisposition(String disposition) {
    }

    @Override
    public String getDescription() {
        return null;
    }

    @Override
    public void setDescription(String description) {
    }

    @Override
    public String getFileName() {
        return null;
    }

    @Override
    public void setFileName(String filename) {
    }

    @Override
    public InputStream getInputStream() {
        return null;
    }

    @Override
    public DataHandler getDataHandler() {
        return null;
    }

    @Override
    public Object getContent() {
        return null;
    }

    @Override
    public void setDataHandler(DataHandler dh) {
    }

    @Override
    public void setContent(Object obj, String type) {
    }

    @Override
    public void setText(String text) {
    }

    @Override
    public void setContent(Multipart mp) {
    }

    @Override
    public void writeTo(OutputStream os) {
    }

    @Override
    public String[] getHeader(String headername) {
        return null;
    }

    @Override
    public void setHeader(String headername, String headervalue) {
    }

    @Override
    public void addHeader(String headername, String headervalue) {
    }

    @Override
    public void removeHeader(String headername) {
    }

    @Override
    public Enumeration<Header> getAllHeaders() {
        return null;
    }

    @Override
    public Enumeration<Header> getMatchingHeaders(String[] headernames) {
        return null;
    }

    @Override
    public Enumeration<Header> getNonMatchingHeaders(String[] headernames) {
        return null;
    }

    @Override
    public Address[] getFrom() {
        return null;
    }

    @Override
    public void setFrom() {
    }

    @Override
    public void setFrom(Address address) {
    }

    @Override
    public void addFrom(Address[] addresses) {
    }

    @Override
    public Address[] getRecipients(RecipientType type) {
        return null;
    }

    @Override
    public void setRecipients(RecipientType type, Address[] addresses) {
    }

    @Override
    public void addRecipients(RecipientType type, Address[] addresses) {
    }

    @Override
    public String getSubject() {
        return null;
    }

    @Override
    public void setSubject(String subject) {
    }

    @Override
    public Date getSentDate() {
        return null;
    }

    @Override
    public void setSentDate(Date date) {
    }

    @Override
    public Date getReceivedDate() {
        return null;
    }

    @Override
    public Flags getFlags() {
        return null;
    }

    @Override
    public void setFlags(Flags flag, boolean set) {
    }

    @Override
    public Message reply(boolean replyToAll) {
        return null;
    }

    @Override
    public void saveChanges() {
    }
}
