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

import java.util.Properties;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;

/**
 * The JavaMailSender interface contains all the methods of a JavaMailSender
 * implementation currently used by the mail component.
 */
public interface JavaMailSender {

    /**
     * Send the mail
     *
     * @param mimeMessage the message to send
     * @throws javax.mail.MessagingException is thrown if error sending the mail.
     */
    void send(MimeMessage mimeMessage) throws MessagingException;

    Properties getJavaMailProperties();

    void setJavaMailProperties(Properties javaMailProperties);

    void setHost(String host);

    String getHost();

    void setPort(int port);

    int getPort();

    void setUsername(String username);

    String getUsername();

    void setPassword(String password);

    String getPassword();

    void setProtocol(String protocol);

    String getProtocol();

    void setSession(Session session);

    Session getSession();
}
