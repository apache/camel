package org.apache.camel.component.jms;

import javax.jms.JMSException;
import javax.jms.Message;

import org.springframework.jms.core.MessageCreator;

public interface MessageCreatorSplit extends MessageCreator {

    public abstract Message createSubMessage() throws JMSException;

    public abstract int getMaxSize();

    public abstract void setMaxSize(int maxSize);

    public abstract boolean isBig();

    public abstract void setBig(boolean big);

    public abstract void addCounter(Message message) throws JMSException;

}