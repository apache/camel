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

import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.mail.Flags.Flag;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import com.icegreen.greenmail.store.FolderException;
import com.icegreen.greenmail.store.MailFolder;
import com.icegreen.greenmail.store.StoredMessage;
import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.user.UserException;
import com.icegreen.greenmail.user.UserManager;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import com.icegreen.greenmail.util.ServerSetupTest;

/**
 * An attempt to wrap GreenMail inside a class similar to {@code org.jvnet.mock_javamail.Mailbox} to make the migration
 * from mock_javamail to GreenMail easier.
 * <p>
 * Note that {@link Mailbox} stores a static copy of the underlying GreenMail folder which was up to date only at the
 * time when the given {@link Mailbox} was created. This is by design to avoid needing to synchronize in the caller
 * code.
 */
public final class Mailbox {

    private static final GreenMail GREEN_MAIL;
    private static final ServerSetup[] SERVER_SETUP
            = new ServerSetup[] { ServerSetupTest.SMTP, ServerSetupTest.POP3, ServerSetupTest.IMAP };
    static {
        GREEN_MAIL = new GreenMail(SERVER_SETUP);
        GREEN_MAIL.start();
    }

    private final List<MimeMessage> messages;

    private Mailbox(MailFolder folder) {
        final List<StoredMessage> msgs = folder.getMessages();
        synchronized (msgs) {
            this.messages = msgs.stream()
                    .map(StoredMessage::getMimeMessage)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Purge all mail folders in the static test server instance.
     */
    public static void clearAll() {
        try {
            GREEN_MAIL.purgeEmailFromAllMailboxes();
        } catch (FolderException e) {
            throw new RuntimeException();
        }
    }

    /**
     * @param  protocol the protocol whose should be returned
     * @return          the port of the given {@code protocol} the static test server instance is listening on
     */
    public static int getPort(Protocol protocol) {
        final int port = Stream.of(SERVER_SETUP)
                .filter(setup -> setup.getProtocol().equals(protocol.name()))
                .mapToInt(ServerSetup::getPort)
                .findFirst()
                .orElseGet(() -> {
                    throw new IllegalStateException("Unexpected protocol " + protocol.name());
                });
        return port;
    }

    /**
     * @return a {@link Session} usable for sending messages
     */
    public static Session getSmtpSession() {
        return Session.getInstance(ServerSetupTest.SMTP.configureJavaMailSessionProperties(null, false), null);
    }

    /**
     * @param  protocol the protocol for which the session properties should be prepared
     * @return          a new {@link Properties} instance containing connection properties for the given
     *                  {@code protocol}
     */
    public static Properties getSessionProperties(Protocol protocol) {
        final ServerSetup serverSetup = Stream.of(SERVER_SETUP)
                .filter(setup -> setup.getProtocol().equals(protocol.name()))
                .findFirst()
                .get();
        return serverSetup.configureJavaMailSessionProperties(null, false);
    }

    /**
     * @param  login
     * @return       a new or existing user with the given {@code login}, random password and e-mail address
     *               <code>[login]@localhost</code>.
     */
    public static MailboxUser getOrCreateUser(String login) {
        return getOrCreateUser(login, UUID.randomUUID().toString());
    }

    /**
     * @param  login
     * @param  password
     * @return          a new or existing user with the given {@code login} and {@code password} and e-mail address
     *                  <code>[login]@localhost</code>.
     */
    public static MailboxUser getOrCreateUser(String login, String password) {
        return getOrCreateUser(login + "@localhost", login, password);
    }

    /**
     * @param  email
     * @param  login
     * @param  password
     * @return          a new or existing user with the given {@code login} and {@code password} and {@code email}
     */
    public static MailboxUser getOrCreateUser(String email, String login, String password) {
        final UserManager userManager = GREEN_MAIL.getUserManager();
        final GreenMailUser result = userManager.getUser(login);
        if (result != null) {
            return new MailboxUser(GREEN_MAIL, result);
        }
        try {
            return new MailboxUser(GREEN_MAIL, userManager.createUser(email, login, password));
        } catch (UserException e) {
            throw new RuntimeException();
        }
    }

    /**
     * @param  index
     * @return       a message stored in this {@link Mailbox} at the given {@code index}
     */
    public MimeMessage get(int index) {
        return messages.get(index);
    }

    /**
     * @return the number of messages stored in this Mailbox possibly including seen and deleted messages
     */
    public int getMessageCount() {
        return messages.size();
    }

    /**
     * @return the number of unseen messages
     */
    public int getNewMessageCount() {
        return (int) messages.stream()
                .filter(m -> {
                    try {
                        return !m.getFlags().contains(Flag.SEEN);
                    } catch (MessagingException e) {
                        throw new RuntimeException(e);
                    }
                })
                .count();
    }

    public enum Protocol {
        smtp,
        pop3,
        imap
    }

    /**
     * A user of an E-Mail server
     */
    public static class MailboxUser {
        private final GreenMail greenMail;
        private final GreenMailUser user;

        public MailboxUser(GreenMail greenMail, GreenMailUser user) {
            super();
            this.greenMail = greenMail;
            this.user = user;
        }

        public String uriPrefix(Protocol protocol) {
            final int port = getPort(protocol);
            return protocol + "://" + user.getLogin() + "@localhost:" + port + "?password=" + user.getPassword();
        }

        public String getEmail() {
            return user.getEmail();
        }

        public String getLogin() {
            return user.getLogin();
        }

        public String getPassword() {
            return user.getPassword();
        }

        public Mailbox getInbox() {
            return getFolder("INBOX");
        }

        public Mailbox getFolder(String folderName) {
            final GreenMailUser greenMailUser = greenMail.getUserManager().getUserByEmail(user.getEmail());
            final MailFolder folder = greenMail.getManagers().getImapHostManager().getFolder(greenMailUser, folderName);
            final List<StoredMessage> messages = folder.getMessages();
            return new Mailbox(folder);
        }

    }

}
