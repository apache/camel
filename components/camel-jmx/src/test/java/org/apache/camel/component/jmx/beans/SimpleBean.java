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
package org.apache.camel.component.jmx.beans;

import java.util.ArrayList;
import java.util.List;

import javax.management.AttributeChangeNotification;
import javax.management.MBeanServerNotification;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.ObjectName;
import javax.management.relation.RelationNotification;
import javax.management.remote.JMXConnectionNotification;
import javax.management.timer.TimerNotification;

/**
 * Simple bean that is used for testing.
 */
public class SimpleBean extends NotificationBroadcasterSupport implements ISimpleMXBean {

    private static final long serialVersionUID = -1230507995730071242L;
    
    private int mSequence;
    /**
     * Use the same timestamp every time so the assertions are easier
     */
    private long mTimestamp;

    private String mStringValue;
    private int mMonitorNumber;
    private long mLongNumber;

    @Override
    public String getStringValue() {
        return mStringValue;
    }

    @Override
    public void setStringValue(String aStringValue) {
        String oldValue = getStringValue();
        mStringValue = aStringValue;

        AttributeChangeNotification acn = new AttributeChangeNotification(
                this, mSequence++, mTimestamp, "attribute changed", "stringValue", "string", oldValue, mStringValue);
        sendNotification(acn);
    }

    @Override
    public Integer getMonitorNumber() {
        return mMonitorNumber;
    }
    @Override
    public void setMonitorNumber(Integer aNumber) {
        mMonitorNumber = aNumber;
    }

    @Override
    public Long getLongNumber() {
        return mLongNumber;
    }
    @Override
    public void setLongNumber(Long aNumber) {
        mLongNumber = aNumber;
    }


    public int getSequence() {
        return mSequence;
    }

    public void setSequence(int aSequence) {
        mSequence = aSequence;
    }

    public long getTimestamp() {
        return mTimestamp;
    }

    public void setTimestamp(long aTimestamp) {
        mTimestamp = aTimestamp;
    }

    @Override
    public void userData(String aUserData) {
        Notification n = new Notification("userData", this, mSequence++, mTimestamp, "Here's my user data");
        n.setUserData(aUserData);
        sendNotification(n);
    }

    @Override
    public void touch() {
        Notification n = new Notification("touched", this, mSequence++, mTimestamp, "I was touched");
        sendNotification(n);
    }

    @Override
    public void triggerConnectionNotification() {
        JMXConnectionNotification n = new JMXConnectionNotification("connection", this,
                "conn-123", mSequence++, "connection notification", null);
        n.setTimeStamp(mTimestamp);
        sendNotification(n);
    }

    @Override
    public void triggerMBeanServerNotification() throws Exception {
        MBeanServerNotification n = new MBeanServerNotification("mbeanserver", this, mSequence++, new ObjectName("TestDomain", "name", "foo"));
        n.setTimeStamp(mTimestamp);
        sendNotification(n);
    }

    @Override
    public void triggerRelationNotification() throws Exception {
        List<ObjectName> list = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            list.add(new ObjectName("TestDomain", "name", "mbean-" + i));
        }
        RelationNotification n = new RelationNotification(RelationNotification.RELATION_BASIC_CREATION,
                new ObjectName("TestDomain", "name", "source"), mSequence++, mTimestamp,
                "relation message",
                "relation-id",
                "relation.type",
                new ObjectName("TestDomain", "name", "foo"),
                list);
        sendNotification(n);
    }

    @Override
    public void triggerTimerNotification() {
        TimerNotification n = new TimerNotification("timer.notification", this, mSequence++, mTimestamp, "timer-notification", 100);
        sendNotification(n);
    }
}
