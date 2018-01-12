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

package org.apache.camel.processor.mllp;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.mllp.MllpConstants;
import org.apache.camel.component.mllp.MllpProtocolConstants;
import org.apache.camel.component.mllp.internal.MllpSocketBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Camel Processor for generating HL7 Acknowledgements.
 */
public class Hl7AcknowledgementGenerator implements Processor {
    private static final Logger LOG = LoggerFactory.getLogger(Hl7AcknowledgementGenerator.class);

    String defaultNack = "MSH|^~\\&|||||||NACK||P|2.2" + MllpProtocolConstants.SEGMENT_DELIMITER
        + "MSA|AR|" + MllpProtocolConstants.SEGMENT_DELIMITER
        + MllpProtocolConstants.MESSAGE_TERMINATOR;


    @Override
    public void process(Exchange exchange) throws Exception {
        Message message = null;
        if (exchange.hasOut()) {
            message = exchange.getOut();
        } else {
            message = exchange.getIn();
        }

        byte[] hl7Bytes = message.getMandatoryBody(byte[].class);

        byte[] acknowledgementBytes = null;
        if (null == exchange.getException()) {
            acknowledgementBytes = generateApplicationAcceptAcknowledgementMessage(hl7Bytes);
            exchange.setProperty(MllpConstants.MLLP_ACKNOWLEDGEMENT_TYPE, "AA");
        } else {
            acknowledgementBytes = generateApplicationErrorAcknowledgementMessage(hl7Bytes);
            exchange.setProperty(MllpConstants.MLLP_ACKNOWLEDGEMENT_TYPE, "AE");
        }

        exchange.setProperty(MllpConstants.MLLP_ACKNOWLEDGEMENT, acknowledgementBytes);
    }

    public byte[] generateApplicationAcceptAcknowledgementMessage(byte[] hl7MessageBytes) throws Hl7AcknowledgementGenerationException {
        return generateAcknowledgementMessage(hl7MessageBytes, "AA");
    }

    public byte[] generateApplicationRejectAcknowledgementMessage(byte[] hl7MessageBytes) throws Hl7AcknowledgementGenerationException {
        return generateAcknowledgementMessage(hl7MessageBytes, "AR");
    }

    public byte[] generateApplicationErrorAcknowledgementMessage(byte[] hl7MessageBytes) throws Hl7AcknowledgementGenerationException {
        return generateAcknowledgementMessage(hl7MessageBytes, "AE");
    }

    byte[] generateAcknowledgementMessage(byte[] hl7MessageBytes, String acknowledgementCode) throws Hl7AcknowledgementGenerationException {
        if (hl7MessageBytes == null) {
            throw new Hl7AcknowledgementGenerationException("Null HL7 message received for parsing operation");
        }

        final byte fieldSeparator = hl7MessageBytes[3];
        final byte componentSeparator = hl7MessageBytes[4];

        List<Integer> fieldSeparatorIndexes = new ArrayList<>(10);  // We need at least 10 fields to create the acknowledgment

        // Find the end of the MSH and indexes of the fields in the MSH
        int endOfMSH = -1;
        for (int i = 0; i < hl7MessageBytes.length; ++i) {
            if (fieldSeparator == hl7MessageBytes[i]) {
                fieldSeparatorIndexes.add(i);
            } else if (MllpProtocolConstants.SEGMENT_DELIMITER == hl7MessageBytes[i]) {
                endOfMSH = i;
                break;
            }
        }

        if (-1 == endOfMSH) {
            throw new Hl7AcknowledgementGenerationException("Failed to find the end of the  MSH Segment while attempting to generate response", hl7MessageBytes);
        }

        if (8 > fieldSeparatorIndexes.size()) {
            throw new Hl7AcknowledgementGenerationException("Insufficient number of fields in after MSH-2 in MSH to generate a response - 8 are required but "
                + fieldSeparatorIndexes.size() + " " + "were found", hl7MessageBytes);
        }

        // Build the MSH Segment
        ByteArrayOutputStream acknowledgement = new ByteArrayOutputStream(1024);
        acknowledgement.write(hl7MessageBytes, 0, fieldSeparatorIndexes.get(1)); // through MSH-2 (without trailing field separator)
        acknowledgement.write(hl7MessageBytes, fieldSeparatorIndexes.get(3), fieldSeparatorIndexes.get(4) - fieldSeparatorIndexes.get(3)); // MSH-5
        acknowledgement.write(hl7MessageBytes, fieldSeparatorIndexes.get(4), fieldSeparatorIndexes.get(5) - fieldSeparatorIndexes.get(4)); // MSH-6
        acknowledgement.write(hl7MessageBytes, fieldSeparatorIndexes.get(1), fieldSeparatorIndexes.get(2) - fieldSeparatorIndexes.get(1)); // MSH-3
        acknowledgement.write(hl7MessageBytes, fieldSeparatorIndexes.get(2), fieldSeparatorIndexes.get(3) - fieldSeparatorIndexes.get(2)); // MSH-4
        acknowledgement.write(hl7MessageBytes, fieldSeparatorIndexes.get(5), fieldSeparatorIndexes.get(7) - fieldSeparatorIndexes.get(5)); // MSH-7 and MSH-8
        // Need to generate the correct MSH-9
        acknowledgement.write(fieldSeparator);
        acknowledgement.write("ACK".getBytes(), 0, 3); // MSH-9.1
        int msh92start = -1;
        for (int j = fieldSeparatorIndexes.get(7) + 1; j < fieldSeparatorIndexes.get(8); ++j) {
            if (componentSeparator == hl7MessageBytes[j]) {
                msh92start = j;
                break;
            }
        }

        if (-1 == msh92start) {
            LOG.warn("Didn't find component separator for MSH-9.2 - sending ACK in MSH-9");
        } else {
            acknowledgement.write(hl7MessageBytes, msh92start, fieldSeparatorIndexes.get(8) - msh92start); // MSH-9.2
        }

        acknowledgement.write(hl7MessageBytes, fieldSeparatorIndexes.get(8), endOfMSH - fieldSeparatorIndexes.get(8)); // MSH-10 through the end of the MSH
        acknowledgement.write(MllpProtocolConstants.SEGMENT_DELIMITER);

        // Build the MSA Segment
        acknowledgement.write("MSA".getBytes(), 0, 3);
        acknowledgement.write(fieldSeparator);
        acknowledgement.write(acknowledgementCode.getBytes(), 0, 2);
        acknowledgement.write(hl7MessageBytes, fieldSeparatorIndexes.get(8), fieldSeparatorIndexes.get(9) - fieldSeparatorIndexes.get(8)); // MSH-10 end
        acknowledgement.write(MllpProtocolConstants.SEGMENT_DELIMITER);

        // Terminate the message
        // acknowledgement.write(SEGMENT_DELIMITER);
        // acknowledgement.write(MESSAGE_TERMINATOR);

        return acknowledgement.toByteArray();
    }


}
