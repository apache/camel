package org.apache.camel.processor.mllp;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.mllp.MllpEndpoint;
import org.apache.camel.component.mllp.impl.MllpConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.util.regex.Pattern;

/**
 * A Camel Processor for generating HL7 Acknowledgements
 */
public class Hl7AcknowledgementGenerator implements Processor {
    Logger log = LoggerFactory.getLogger(this.getClass());

    Charset charset = Charset.defaultCharset();
    boolean useOutMessage = true;
    boolean produceStringBody = true;

    @Override
    public void process(Exchange exchange) throws Exception {
        Object messageBody = null;
        if (exchange.hasOut()) {
            messageBody = exchange.getOut().getBody();
        } else {
            messageBody = exchange.getIn().getBody();
        }

        byte[] hl7Bytes;

        if (messageBody instanceof String) {
            hl7Bytes = ((String)messageBody).getBytes(charset);
        } else if (messageBody instanceof byte[]) {
            hl7Bytes = (byte[])messageBody;
        } else {
            log.warn("Cannot generate an HL7 Acknowledgement from type {}", messageBody.getClass().getName());
            return;
        }

        byte[] acknowledgementBytes = null;
        if ( null == exchange.getException() ) {
            acknowledgementBytes = generateApplicationAcceptAcknowledgementMessage(hl7Bytes);
        } else {
            acknowledgementBytes = generateApplicationErrorAcknowledgementMessage(hl7Bytes);
        }

    }

    public byte[] generateApplicationAcceptAcknowledgementMessage(byte[] hl7MessageBytes) {
        return generateAcknowledgementMessage(hl7MessageBytes, "AA");
    }

    public byte[] generateApplicationRejectAcknowledgementMessage(byte[] hl7MessageBytes) {
        return generateAcknowledgementMessage(hl7MessageBytes, "AR");
    }

    public byte[] generateApplicationErrorAcknowledgementMessage(byte[] hl7MessageBytes) {
        return generateAcknowledgementMessage(hl7MessageBytes, "AE");
    }


    byte[] generateAcknowledgementMessage(byte[] hl7MessageBytes, String acknowledgementCode) {
        final String DEFAULT_NACK_MESSAGE =
                "MSH|^~\\&|||||||NACK||P|2.2" + MllpEndpoint.SEGMENT_DELIMITER
                        + "MSA|AR|" + MllpEndpoint.SEGMENT_DELIMITER
                        + MllpEndpoint.MESSAGE_TERMINATOR;

        if (hl7MessageBytes == null) {
            log.error("Invalid HL7 message for parsing operation. Please check your inputs");
            return DEFAULT_NACK_MESSAGE.getBytes(charset);
        }

        String messageControlId;

        // Find the MSH
        String mshSegment = null;
        for (int i = 0; i < hl7MessageBytes.length; ++i) {
            if (MllpConstants.SEGMENT_DELIMITER == hl7MessageBytes[i]) {
                mshSegment = new String(hl7MessageBytes, 0, i, charset);
                break;
            }
        }

        // TODO:  Rework this so it doesn't need the intermediate String
        if (null != mshSegment) {
            char fieldSeparator = mshSegment.charAt(3);
            String fieldSeparatorPattern = Pattern.quote("" + fieldSeparator);
            String[] mshFields = mshSegment.split(fieldSeparatorPattern);
            if (null == mshFields || 0 == mshFields.length) {
                log.error("Failed to split MSH Segment into fields");
            } else {
                StringBuilder ackBuilder = new StringBuilder(mshSegment.length() + 25);
                // Build the MSH Segment
                ackBuilder
                        .append(mshFields[0]).append(fieldSeparator)
                        .append(mshFields[1]).append(fieldSeparator)
                        .append(mshFields[4]).append(fieldSeparator)
                        .append(mshFields[5]).append(fieldSeparator)
                        .append(mshFields[2]).append(fieldSeparator)
                        .append(mshFields[3]).append(fieldSeparator)
                        .append(mshFields[6]).append(fieldSeparator)
                        .append(mshFields[7]).append(fieldSeparator)
                        .append("ACK").append(mshFields[8].substring(3))
                ;
                for (int i = 9; i < mshFields.length; ++i) {
                    ackBuilder.append(fieldSeparator).append(mshFields[i]);
                }
                // Empty fields at the end are not preserved by String.split, so preserve them
                int emptyFieldIndex = mshSegment.length() - 1;
                if (fieldSeparator == mshSegment.charAt(mshSegment.length() - 1)) {
                    ackBuilder.append(fieldSeparator);
                    while (emptyFieldIndex >= 1 && mshSegment.charAt(emptyFieldIndex) == mshSegment.charAt(emptyFieldIndex - 1)) {
                        ackBuilder.append(fieldSeparator);
                        --emptyFieldIndex;
                    }
                }
                ackBuilder.append(MllpEndpoint.SEGMENT_DELIMITER);

                // Build the MSA Segment
                ackBuilder
                        .append("MSA").append(fieldSeparator)
                        .append(acknowledgementCode).append(fieldSeparator)
                        .append(mshFields[9]).append(fieldSeparator)
                        .append(MllpEndpoint.SEGMENT_DELIMITER)
                ;

                // Terminate the message
                ackBuilder.append(MllpEndpoint.MESSAGE_TERMINATOR);

                return ackBuilder.toString().getBytes(charset);
            }
        } else {
            log.error("Failed to find the end of the  MSH Segment while attempting to generate response");
        }

        return null;
    }

    public Charset getCharset() {
        return charset;
    }

    /**
     * @param charset
     */
    public void setCharset(Charset charset) {
        this.charset = charset;
    }

    public boolean isUseOutMessage() {
        return useOutMessage;
    }

    /**
     * @param useOutMessage
     */
    public void setUseOutMessage(boolean useOutMessage) {
        this.useOutMessage = useOutMessage;
    }

    public boolean isProduceStringBody() {
        return produceStringBody;
    }

    /**
     * @param produceStringBody
     */
    public void setProduceStringBody(boolean produceStringBody) {
        this.produceStringBody = produceStringBody;
    }
}
