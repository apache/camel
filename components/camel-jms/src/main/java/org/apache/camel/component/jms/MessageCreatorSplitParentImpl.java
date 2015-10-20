package org.apache.camel.component.jms;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.UUID;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;

import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.slf4j.Logger;
import org.springframework.jms.core.MessageCreator;

public abstract class MessageCreatorSplitParentImpl implements MessageCreatorSplit {
    protected JmsEndpoint endpoint;
    protected org.apache.camel.Message camelMessage;
    protected Exchange exchange;
    protected MessageCreator messageCreator;
    protected Session session;
    protected int maxSize;
    protected InputStream body;
    protected String pieceID;
    protected boolean big;
    protected Logger LOG;
    protected int counter;

    public MessageCreatorSplitParentImpl(JmsEndpoint endpoint, org.apache.camel.Message camelMessage, Exchange exchange, Logger LOG,
            int maxSize) {
        this.endpoint = endpoint;
        this.camelMessage = camelMessage;
        this.exchange = exchange;
        this.LOG = LOG;
        this.maxSize = maxSize;
    }

    @Override
    public Message createSubMessage() throws JMSException {
        int endOfStream = setByteArrayPayload();
        //jMS property are added to camelMessage
        camelMessage.removeHeaders(JmsConstants.JMS_SPLIT_HEAD);
        camelMessage.removeHeaders(JmsConstants.JMS_SPLIT_COUNT);
        if (endOfStream != -1) {
            Message answer = endpoint.getBinding().makeJmsMessage(exchange, camelMessage, session, null);
            answer.setStringProperty(JmsConstants.JMS_SPLIT_PIECE_ID, pieceID);
            answer.setIntProperty(JmsConstants.JMS_SPLIT_COUNTER, ++counter);
            LOG.debug("Creating sub message: {} ", counter);
            return answer;
        } else {
            return null;
        }
    }

    @Override
    public int getMaxSize() {
        return maxSize;
    }

    @Override
    public void setMaxSize(int maxSize) {
        this.maxSize = maxSize;

    }

    @Override
    public boolean isBig() {
        // TODO Auto-generated method stub
        return big;
    }

    @Override
    public void setBig(boolean big) {
        this.big = big;

    }

    @Override
    public void addCounter(Message message) throws JMSException {
        message.setIntProperty(JmsConstants.JMS_SPLIT_COUNT, counter);
        LOG.debug("Sub message count: {}", counter);
    }

    protected Message createMessageFromEndpoint(Session session) throws JMSException {
        Message answer = null;
        if (camelMessage.getBody() != null) {
            try {
                this.session = session;
                body = camelMessage.getMandatoryBody(InputStream.class);
            } catch (InvalidPayloadException e) {
                throw new JMSException("error accessing message payload as stream");
            }
            setByteArrayPayload();
        }
        answer = endpoint.getBinding().makeJmsMessage(exchange, camelMessage, session, null);
        answer.setStringProperty(JmsConstants.JMS_SPLIT_HEAD, JmsConstants.JMS_SPLIT_HEAD);
        if (big) {
            pieceID = UUID.randomUUID().toString();
            answer.setStringProperty(JmsConstants.JMS_SPLIT_PIECE_ID, pieceID);
            LOG.debug("creating head message {}", pieceID);
        }
        return answer;
    }

    protected int setByteArrayPayload() throws JMSException {
        byte[] buffer = new byte[maxSize];
        try {
            int endOfStream = body.read(buffer);
            big = !(endOfStream < maxSize && endOfStream > 0);
            if (!big) {
                byte[] buffer2 = new byte[endOfStream - 1];
                buffer2 = Arrays.copyOf(buffer, endOfStream);
                camelMessage.setBody(buffer2);
            } else {
                camelMessage.setBody(buffer);
            }
            return endOfStream;
        } catch (IOException e) {
            LOG.error("error when reading stream into buffer", e);
            throw new JMSException("error when reading stream into buffer");
        }
    }
}
