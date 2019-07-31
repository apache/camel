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
package org.apache.camel.dataformat.bindy.model.fix.complex.onetomany;

import java.util.Date;

import org.apache.camel.dataformat.bindy.annotation.KeyValuePairField;
import org.apache.camel.dataformat.bindy.annotation.Link;

@Link
public class Header {

    @KeyValuePairField(tag = 8)
    // Message Header
    private String beginString;

    @KeyValuePairField(tag = 9)
    // Checksum
    private int bodyLength;

    @KeyValuePairField(tag = 34)
    // Sequence number
    private int msgSeqNum;

    @KeyValuePairField(tag = 35)
    // Message Type
    private String msgType;

    @KeyValuePairField(tag = 49)
    // Company sender Id
    private String sendCompId;

    @KeyValuePairField(tag = 56)
    // target company id
    private String targetCompId;

    @KeyValuePairField(tag = 777, pattern = "dd-MM-yyyy HH:mm:ss", timezone = "GMT-3")
    // created
    private Date created;

    public String getBeginString() {
        return beginString;
    }

    public void setBeginString(String beginString) {
        this.beginString = beginString;
    }

    public int getBodyLength() {
        return bodyLength;
    }

    public void setBodyLength(int bodyLength) {
        this.bodyLength = bodyLength;
    }

    public int getMsgSeqNum() {
        return msgSeqNum;
    }

    public void setMsgSeqNum(int msgSeqNum) {
        this.msgSeqNum = msgSeqNum;
    }

    public String getMsgType() {
        return msgType;
    }

    public void setMsgType(String msgType) {
        this.msgType = msgType;
    }

    public String getSendCompId() {
        return sendCompId;
    }

    public void setSendCompId(String sendCompId) {
        this.sendCompId = sendCompId;
    }

    public String getTargetCompId() {
        return targetCompId;
    }

    public void setTargetCompId(String targetCompId) {
        this.targetCompId = targetCompId;
    }

    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    @Override
    public String toString() {
        return Header.class.getName() + " --> 8: " + this.beginString + ", 9: " + this.bodyLength + ", 34: " + this.msgSeqNum + " , 35: " + this.msgType + ", 49: "
               + this.sendCompId + ", 56: " + this.targetCompId + ", 777: " + this.created;
    }

}
