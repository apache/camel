package org.apache.camel.component.mail;

import javax.mail.Folder;

/**
 * Is used for doing post processing tasks on the mailbox once the normal processing ended. This includes for example
 * cleaning up old emails.
 */
public interface MailBoxPostProcessAction {
    /**
     * Process the given mail folder
     *
     * @param folder Folder to process
     */
    void process(Folder folder) throws Exception;
}
