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
package org.apache.camel.component.smpp;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.support.service.BaseService;
import org.apache.camel.support.task.BlockingTask;
import org.apache.camel.support.task.Tasks;
import org.apache.camel.support.task.budget.Budgets;
import org.jsmpp.bean.Alphabet;
import org.jsmpp.bean.DataSm;
import org.jsmpp.bean.DeliverSm;
import org.jsmpp.bean.OptionalParameter;
import org.jsmpp.bean.OptionalParameter.OctetString;
import org.jsmpp.bean.OptionalParameter.Tag;
import org.jsmpp.bean.SubmitMulti;
import org.jsmpp.bean.SubmitSm;
import org.jsmpp.extra.SessionState;
import org.jsmpp.session.SMPPSession;
import org.jsmpp.util.AbsoluteTimeFormatter;
import org.jsmpp.util.TimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SmppUtils {
    /**
     * See http://unicode.org/Public/MAPPINGS/ETSI/GSM0338.TXT
     */
    public static final short[] ISO_GSM_0338 = {
            64, 163, 36, 165, 232, 233, 249, 236,
            242, 199, 10, 216, 248, 13, 197, 229,
            0, 95, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 198, 230, 223, 201,
            32, 33, 34, 35, 164, 37, 38, 39,
            40, 41, 42, 43, 44, 45, 46, 47,
            48, 49, 50, 51, 52, 53, 54, 55,
            56, 57, 58, 59, 60, 61, 62, 63,
            161, 65, 66, 67, 68, 69, 70, 71,
            72, 73, 74, 75, 76, 77, 78, 79,
            80, 81, 82, 83, 84, 85, 86, 87,
            88, 89, 90, 196, 214, 209, 220, 167,
            191, 97, 98, 99, 100, 101, 102, 103,
            104, 105, 106, 107, 108, 109, 110, 111,
            112, 113, 114, 115, 116, 117, 118, 119,
            120, 121, 122, 228, 246, 241, 252, 224
    };

    /**
     * See http://unicode.org/Public/MAPPINGS/ETSI/GSM0338.TXT
     */
    public static final short[][] ISO_GSM_0338_EXT = {
            { 10, 12 }, { 20, 94 }, { 40, 123 }, { 41, 125 }, { 47, 92 },
            { 60, 91 }, { 61, 126 }, { 62, 93 }, { 64, 124 }, { 101, 164 }
    };

    private static final Logger LOG = LoggerFactory.getLogger(SmppUtils.class);
    private static final TimeFormatter TIME_FORMATTER = new AbsoluteTimeFormatter();

    private SmppUtils() {
    }

    public static String formatTime(Date date) {
        return TIME_FORMATTER.format(date);
    }

    /**
     * YYMMDDhhmmSS where:
     * <ul>
     * <li>YY = last two digits of the year (00-99)</li>
     * <li>MM = month (01-12)</li>
     * <li>DD = day (01-31)</li>
     * <li>hh = hour (00-23)</li>
     * <li>mm = minute (00-59)</li>
     * <li>SS = second (00-59)</li>
     * </ul>
     *
     * Java format is (yyMMddHHmmSS).
     *
     * @param  date                      in <tt>String</tt> format.
     * @return                           the date
     * @throws NumberFormatException     if there is contains non number on <code>date</code> parameter.
     * @throws IndexOutOfBoundsException if the date length in <tt>String</tt> format is less than 10.
     */
    public static Date string2Date(String date) {
        if (date == null) {
            return null;
        }

        int year = Integer.parseInt(date.substring(0, 2));
        int month = Integer.parseInt(date.substring(2, 4));
        int day = Integer.parseInt(date.substring(4, 6));
        int hour = Integer.parseInt(date.substring(6, 8));
        int minute = Integer.parseInt(date.substring(8, 10));
        int second = Integer.parseInt(date.substring(10, 12));
        Calendar cal = Calendar.getInstance();
        cal.set(convertTwoDigitYear(year), month - 1, day, hour, minute, second);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    private static int convertTwoDigitYear(int year) {
        if (year >= 0 && year <= 37) {
            return 2000 + year;
        } else if (year >= 38 && year <= 99) {
            return 1900 + year;
        } else {
            // should never happen
            return year;
        }
    }

    /**
     * Returns the payload of a deliverSm
     *
     * @param  deliverSm
     * @return           the shortMessage, by first looking in the shortMessage field of the deliver_sm and if its null
     *                   or empty, fallbacks to the optional parameter "MESSAGE_PAYLOAD".
     */
    public static byte[] getMessageBody(DeliverSm deliverSm) {
        byte[] body = deliverSm.getShortMessage();
        if (body == null || body.length == 0) {
            OptionalParameter param = deliverSm.getOptionalParameter(Tag.MESSAGE_PAYLOAD);
            if (param instanceof OctetString) {
                body = ((OctetString) param).getValue();
            }
        }
        return body;
    }

    public static boolean is8Bit(Alphabet alphabet) {
        return alphabet == Alphabet.ALPHA_UNSPECIFIED_2 || alphabet == Alphabet.ALPHA_8_BIT;
    }

    /**
     * Decides if the characters in the argument are GSM 3.38 encodeable.
     *
     * @param  aMessage must be a set of characters encoded in ISO-8859-1 or a compatible character set. In particular,
     *                  UTF-8 encoded text should not be passed to this method.
     * @return          true if the characters can be represented in GSM 3.38
     */
    public static boolean isGsm0338Encodeable(byte[] aMessage) {
        outer: for (int i = 0; i < aMessage.length; i++) {
            for (int j = 0; j < ISO_GSM_0338.length; j++) {
                if (ISO_GSM_0338[j] == aMessage[i]) {
                    continue outer;
                }
            }
            for (int j = 0; j < ISO_GSM_0338_EXT.length; j++) {
                if (ISO_GSM_0338_EXT[j][1] == aMessage[i]) {
                    continue outer;
                }
            }
            return false;
        }
        return true;
    }

    public static SubmitSm copySubmitSm(SubmitSm src) {
        SubmitSm dest = new SubmitSm();
        dest.setCommandId(src.getCommandId());
        dest.setCommandLength(src.getCommandLength());
        dest.setCommandStatus(src.getCommandStatus());
        dest.setDataCoding(src.getDataCoding());
        dest.setDestAddress(src.getDestAddress());
        dest.setDestAddrNpi(src.getDestAddrNpi());
        dest.setDestAddrTon(src.getDestAddrTon());
        dest.setEsmClass(src.getEsmClass());
        dest.setOptionalParameters(src.getOptionalParameters());
        dest.setPriorityFlag(src.getPriorityFlag());
        dest.setProtocolId(src.getProtocolId());
        dest.setRegisteredDelivery(src.getRegisteredDelivery());
        dest.setReplaceIfPresent(src.getReplaceIfPresent());
        dest.setScheduleDeliveryTime(src.getScheduleDeliveryTime());
        dest.setSequenceNumber(src.getSequenceNumber());
        dest.setServiceType(src.getServiceType());
        dest.setShortMessage(src.getShortMessage());
        dest.setSmDefaultMsgId(src.getSmDefaultMsgId());
        dest.setSourceAddr(src.getSourceAddr());
        dest.setSourceAddrNpi(src.getSourceAddrNpi());
        dest.setSourceAddrTon(src.getSourceAddrTon());
        dest.setValidityPeriod(src.getValidityPeriod());
        if (src.isDatagramMode()) {
            dest.setDatagramMode();
        }
        if (src.isDefaultMessageType()) {
            dest.setDefaultMessageType();
        }
        if (src.isDefaultMode()) {
            dest.setDefaultMode();
        }
        if (src.isEsmeDeliveryAcknowledgement()) {
            dest.setEsmeDeliveryAcknowledgement();
        }
        if (src.isEsmeManualAcknowledgement()) {
            dest.setEsmeManualAcknowledgement();
        }
        if (src.isForwardMode()) {
            dest.setForwardMode();
        }
        if (src.isReplyPath()) {
            dest.setReplyPath();
        }
        if (src.isSmscDelReceiptFailureRequested()) {
            dest.setSmscDelReceiptFailureRequested();
        }
        if (src.isSmscDelReceiptNotRequested()) {
            dest.setSmscDelReceiptNotRequested();
        }
        if (src.isSmscDelReceiptSuccessAndFailureRequested()) {
            dest.setSmscDelReceiptSuccessAndFailureRequested();
        }
        if (src.isStoreAndForwardMode()) {
            dest.setStoreAndForwardMode();
        }
        if (src.isUdhi()) {
            dest.setUdhi();
        }
        if (src.isUdhiAndReplyPath()) {
            dest.setUdhiAndReplyPath();
        }
        return dest;
    }

    public static SubmitMulti copySubmitMulti(SubmitMulti src) {
        SubmitMulti dest = new SubmitMulti();
        dest.setCommandId(src.getCommandId());
        dest.setCommandLength(src.getCommandLength());
        dest.setCommandStatus(src.getCommandStatus());
        dest.setDataCoding(src.getDataCoding());
        dest.setDestAddresses(src.getDestAddresses());
        dest.setEsmClass(src.getEsmClass());
        dest.setOptionalParameters(src.getOptionalParameters());
        dest.setPriorityFlag(src.getPriorityFlag());
        dest.setProtocolId(src.getProtocolId());
        dest.setRegisteredDelivery(src.getRegisteredDelivery());
        dest.setReplaceIfPresentFlag(src.getReplaceIfPresentFlag());
        dest.setScheduleDeliveryTime(src.getScheduleDeliveryTime());
        dest.setSequenceNumber(src.getSequenceNumber());
        dest.setServiceType(src.getServiceType());
        dest.setShortMessage(src.getShortMessage());
        dest.setSmDefaultMsgId(src.getSmDefaultMsgId());
        dest.setSourceAddr(src.getSourceAddr());
        dest.setSourceAddrNpi(src.getSourceAddrNpi());
        dest.setSourceAddrTon(src.getSourceAddrTon());
        dest.setValidityPeriod(src.getValidityPeriod());
        return dest;
    }

    public static DataSm copyDataSm(DataSm src) {
        DataSm dest = new DataSm();
        dest.setCommandId(src.getCommandId());
        dest.setCommandLength(src.getCommandLength());
        dest.setCommandStatus(src.getCommandStatus());
        dest.setDataCoding(src.getDataCoding());
        dest.setDestAddress(src.getDestAddress());
        dest.setDestAddrNpi(src.getDestAddrNpi());
        dest.setDestAddrTon(src.getDestAddrTon());
        dest.setEsmClass(src.getEsmClass());
        dest.setOptionalParameters(src.getOptionalParameters());
        dest.setRegisteredDelivery(src.getRegisteredDelivery());
        dest.setSequenceNumber(src.getSequenceNumber());
        dest.setServiceType(src.getServiceType());
        dest.setSourceAddr(src.getSourceAddr());
        dest.setSourceAddrNpi(src.getSourceAddrNpi());
        dest.setSourceAddrTon(src.getSourceAddrTon());
        if (src.isDefaultMessageType()) {
            dest.setDefaultMessageType();
        }
        if (src.isReplyPath()) {
            dest.setReplyPath();
        }
        if (src.isUdhi()) {
            dest.setUdhi();
        }
        if (src.isUdhiAndReplyPath()) {
            dest.setUdhiAndReplyPath();
        }
        return dest;
    }

    public static boolean isServiceStopping(BaseService service) {
        return service.isStopping() || service.isStopped();
    }

    public static boolean isSessionClosed(SMPPSession session) {
        return session == null || session.getSessionState().equals(SessionState.CLOSED);
    }

    public static ScheduledExecutorService createExecutor(BaseService service, Endpoint endpoint, String taskName) {
        if (endpoint.getCamelContext() != null && endpoint.getCamelContext().getExecutorServiceManager() != null) {
            ExecutorServiceManager manager = endpoint.getCamelContext().getExecutorServiceManager();
            return manager.newSingleThreadScheduledExecutor(service, taskName);
        } else {
            LOG.warn("Not using the Camel scheduled thread executor");
            return Executors.newSingleThreadScheduledExecutor();
        }
    }

    public static BlockingTask newReconnectTask(
            ScheduledExecutorService service, String taskName, long initialReconnectDelay,
            long reconnectDelay, int maxReconnect) {
        return Tasks.backgroundTask()
                .withBudget(Budgets.iterationTimeBudget()
                        .withInitialDelay(Duration.ofMillis(initialReconnectDelay))
                        .withMaxIterations(maxReconnect)
                        .withUnlimitedDuration()
                        .withInterval(Duration.ofMillis(reconnectDelay))
                        .build())
                .withScheduledExecutor(service)
                .withName(taskName)
                .build();
    }

    public static void shutdownReconnectService(ScheduledExecutorService service) throws InterruptedException {
        service.shutdown();
        if (!service.awaitTermination(1, TimeUnit.SECONDS)) {
            LOG.warn("The reconnect service did not finish executing within the timeout");

            service.shutdownNow();
        }
    }

    /**
     * This method would try to decode the bytes provided a dataCoding.
     *
     * Supports: US_ASCII, ISO_8859_1, UTF_16_BE alphabet values
     *
     * @param  body                         Body of the message in bytes
     * @param  dataCoding                   The data coding value
     * @param  defaultEncoding              The default encoding
     * @return                              null if body is null or 8bit encoded, or the decoded body on success
     * @throws UnsupportedEncodingException when the default encoding is unsupported
     */
    public static String decodeBody(byte[] body, byte dataCoding, String defaultEncoding)
            throws UnsupportedEncodingException {
        Alphabet alphabet = Alphabet.parseDataCoding(dataCoding);
        if (body == null || SmppUtils.is8Bit(alphabet)) {
            return null;
        }
        switch (alphabet) {
            case ALPHA_IA5:
                return new String(body, StandardCharsets.US_ASCII);
            case ALPHA_LATIN1:
                return new String(body, StandardCharsets.ISO_8859_1);
            case ALPHA_UCS2:
                return new String(body, StandardCharsets.UTF_16BE);
            default:
                return new String(body, defaultEncoding);
        }
    }
}
