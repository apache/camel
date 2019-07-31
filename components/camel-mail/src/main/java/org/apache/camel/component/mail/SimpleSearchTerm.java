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

/**
 * Allows to configure common {@link javax.mail.search.SearchTerm}'s using a POJO style,
 * which can be done from XML DSLs.
 * <p/>
 * This POJO has default <tt>true</tt> for the {@link #isUnseen()} option.
 * <p/>
 * The date options (such as {@link #setFromReceivedDate(String)}) is using
 * the following date pattern <tt>yyyy-MM-dd HH:mm:SS</tt>.
 */
public class SimpleSearchTerm {

    private boolean unseen = true;
    private String subjectOrBody;
    private String subject;
    private String body;
    private String from;
    private String to;
    private String fromSentDate;
    private String toSentDate;
    private String fromReceivedDate;
    private String toReceivedDate;

    public boolean isUnseen() {
        return unseen;
    }

    public void setUnseen(boolean unseen) {
        this.unseen = unseen;
    }

    public String getSubjectOrBody() {
        return subjectOrBody;
    }

    public void setSubjectOrBody(String subjectOrBody) {
        this.subjectOrBody = subjectOrBody;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getFromSentDate() {
        return fromSentDate;
    }

    public void setFromSentDate(String fromSentDate) {
        this.fromSentDate = fromSentDate;
    }

    public String getToSentDate() {
        return toSentDate;
    }

    public void setToSentDate(String toSentDate) {
        this.toSentDate = toSentDate;
    }

    public String getFromReceivedDate() {
        return fromReceivedDate;
    }

    public void setFromReceivedDate(String fromReceivedDate) {
        this.fromReceivedDate = fromReceivedDate;
    }

    public String getToReceivedDate() {
        return toReceivedDate;
    }

    public void setToReceivedDate(String toReceivedDate) {
        this.toReceivedDate = toReceivedDate;
    }
}
