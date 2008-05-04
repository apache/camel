package org.apache.camel.component.mail;

import javax.mail.Store;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.internet.MimeMessage;

import org.jvnet.mock_javamail.Mailbox;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.ContextTestSupport;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Unit test for a special corner case with fetchSize=0
 */
public class MailFetchSizeZeroTest extends ContextTestSupport {

    public void testFetchSize() throws Exception {
        prepareMailbox();
        Mailbox mailbox = Mailbox.get("james@localhost");
        assertEquals(5, mailbox.size());

        MockEndpoint mock = getMockEndpoint("mock:result");
        // no messages expected as we have a fetch size of zero
        mock.expectedMessageCount(0);
        // should be done within 2 seconds as no delay when started
        mock.setResultWaitTime(2000L);
        mock.assertIsSatisfied();

        assertEquals(5, mailbox.size());
    }

    private void prepareMailbox() throws Exception {
        // connect to mailbox
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        Store store = sender.getSession().getStore("pop3");
        store.connect("localhost", 25, "james", "secret");
        Folder folder = store.getFolder("INBOX");
        folder.open(Folder.READ_WRITE);

        // inserts 5 new messages
        Message[] messages = new Message[5];
        for (int i = 0; i < 5; i++) {
            messages[i] = new MimeMessage(sender.getSession());
            messages[i].setText("Message " + i);
        }
        folder.appendMessages(messages);
        folder.close(true);
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("pop3://james@localhost?password=secret&fetchSize=0").to("mock:result");
            }
        };
    }

}
