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
package org.apache.camel.component.iec60870;

import java.util.Objects;
import java.util.TimeZone;

import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.eclipse.neoscada.protocol.iec60870.ASDUAddressType;
import org.eclipse.neoscada.protocol.iec60870.CauseOfTransmissionType;
import org.eclipse.neoscada.protocol.iec60870.InformationObjectAddressType;
import org.eclipse.neoscada.protocol.iec60870.ProtocolOptions;

@UriParams
public abstract class BaseOptions<T extends BaseOptions<T>> {

    /**
     * Protocol options
     */
    @UriParam(javaType = "org.eclipse.neoscada.protocol.iec60870.ProtocolOptions")
    private ProtocolOptions.Builder protocolOptions;

    // dummy for doc generation
    /**
     * The common ASDU address size.
     * <p>
     * May be either {@code SIZE_1} or {@code SIZE_2}.
     * </p>
     */
    @UriParam(enums = "SIZE_1, SIZE_2", label = "connection")
    private ASDUAddressType adsuAddressType;

    // dummy for doc generation
    /**
     * The information address size.
     * <p>
     * May be either {@code SIZE_1}, {@code SIZE_2} or {@code SIZE_3}.
     * </p>
     */
    @UriParam(enums = "SIZE_1, SIZE_2, SIZE_3", label = "connection")
    private InformationObjectAddressType informationObjectAddressType;

    // dummy for doc generation
    /**
     * The cause of transmission type.
     * <p>
     * May be either {@code SIZE_1} or {@code SIZE_2}.
     * </p>
     */
    @UriParam(enums = "SIZE_1, SIZE_2", label = "connection")
    private CauseOfTransmissionType causeOfTransmissionType;

    // dummy for doc generation
    /**
     * The timezone to use.
     * <p>
     * May be any Java time zone string
     * </p>
     */
    @UriParam(label = "data", defaultValue = "UTC")
    private TimeZone timeZone;

    // dummy for doc generation
    /**
     * Whether to ignore or respect DST
     */
    @UriParam(label = "data")
    private boolean ignoreDaylightSavingTime;

    // dummy for doc generation
    /**
     * Timeout T1 in milliseconds.
     */
    @UriParam(label = "connection", defaultValue = "15000")
    private int timeout1;

    // dummy for doc generation
    /**
     * Timeout T2 in milliseconds.
     */
    @UriParam(label = "connection", defaultValue = "10000")
    private int timeout2;

    // dummy for doc generation
    /**
     * Timeout T3 in milliseconds.
     */
    @UriParam(label = "connection", defaultValue = "20000")
    private int timeout3;

    // dummy for doc generation
    /**
     * Parameter "K" - Maximum number of un-acknowledged messages.
     */
    @UriParam(label = "connection", defaultValue = "15")
    private short maxUnacknowledged;

    // dummy for doc generation
    /**
     * Parameter "W" - Acknowledgment window.
     */
    @UriParam(label = "connection", defaultValue = "10")
    private short acknowledgeWindow;

    protected BaseOptions() {
        this.protocolOptions = new ProtocolOptions.Builder();
    }

    protected BaseOptions(final ProtocolOptions protocolOptions) {
        Objects.requireNonNull(protocolOptions);
        this.protocolOptions = new ProtocolOptions.Builder(protocolOptions);
    }

    public void setProtocolOptions(final ProtocolOptions protocolOptions) {
        Objects.requireNonNull(protocolOptions);

        this.protocolOptions = new ProtocolOptions.Builder(protocolOptions);
    }

    public ProtocolOptions getProtocolOptions() {
        return this.protocolOptions.build();
    }

    public abstract T copy();

    // wrapper methods - ProtocolOptions

    public int getTimeout1() {
        return this.protocolOptions.getTimeout1();
    }

    public void setTimeout1(final int timeout1) {
        this.protocolOptions.setTimeout1(timeout1);
    }

    public int getTimeout2() {
        return this.protocolOptions.getTimeout2();
    }

    public void setTimeout2(final int timeout2) {
        this.protocolOptions.setTimeout2(timeout2);
    }

    public int getTimeout3() {
        return this.protocolOptions.getTimeout3();
    }

    public void setTimeout3(final int timeout3) {
        this.protocolOptions.setTimeout3(timeout3);
    }

    public short getAcknowledgeWindow() {
        return this.protocolOptions.getAcknowledgeWindow();
    }

    public void setAcknowledgeWindow(final short acknowledgeWindow) {
        this.protocolOptions.setAcknowledgeWindow(acknowledgeWindow);
    }

    public short getMaxUnacknowledged() {
        return this.protocolOptions.getMaxUnacknowledged();
    }

    public void setMaxUnacknowledged(final short maxUnacknowledged) {
        this.protocolOptions.setMaxUnacknowledged(maxUnacknowledged);
    }

    public ASDUAddressType getAdsuAddressType() {
        return this.protocolOptions.getAdsuAddressType();
    }

    public void setAdsuAddressType(final ASDUAddressType adsuAddressType) {
        this.protocolOptions.setAdsuAddressType(adsuAddressType);
    }

    public InformationObjectAddressType getInformationObjectAddressType() {
        return this.protocolOptions.getInformationObjectAddressType();
    }

    public void setInformationObjectAddressType(final InformationObjectAddressType informationObjectAddressType) {
        this.protocolOptions.setInformationObjectAddressType(informationObjectAddressType);
    }

    public CauseOfTransmissionType getCauseOfTransmissionType() {
        return this.protocolOptions.getCauseOfTransmissionType();
    }

    public void setCauseOfTransmissionType(final CauseOfTransmissionType causeOfTransmissionType) {
        this.protocolOptions.setCauseOfTransmissionType(causeOfTransmissionType);
    }

    public TimeZone getTimeZone() {
        return this.protocolOptions.getTimeZone();
    }

    public void setTimeZone(final TimeZone timeZone) {
        this.protocolOptions.setTimeZone(timeZone);
    }

    public void setIgnoreDaylightSavingTime(final boolean ignoreDaylightSavingTime) {
        this.protocolOptions.setIgnoreDaylightSavingTime(ignoreDaylightSavingTime);
    }

    public boolean isIgnoreDaylightSavingTime() {
        return this.protocolOptions.isIgnoreDaylightSavingTime();
    }

}
