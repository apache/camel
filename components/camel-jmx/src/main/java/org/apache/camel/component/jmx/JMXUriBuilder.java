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
package org.apache.camel.component.jmx;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Builder for JMX endpoint URI's. Saves you from having to do the string concat'ing
 * and messing up the param names
 */
public class JMXUriBuilder {
    private Map<String, String> mQueryProps = new LinkedHashMap<>();
    private String mServerName = "platform";

    public JMXUriBuilder() {
    }

    public JMXUriBuilder(String aServerName) {
        setServerName(aServerName);
    }

    public JMXUriBuilder withFormat(String aFormat) {
        addProperty("format", aFormat);
        return this;
    }

    public JMXUriBuilder withUser(String aFormat) {
        addProperty("user", aFormat);
        return this;
    }

    public JMXUriBuilder withPassword(String aFormat) {
        addProperty("password", aFormat);
        return this;
    }

    public JMXUriBuilder withObjectDomain(String aFormat) {
        addProperty("objectDomain", aFormat);
        return this;
    }

    public JMXUriBuilder withObjectName(String aFormat) {
        addProperty("objectName", aFormat);
        return this;
    }

    public JMXUriBuilder withNotificationFilter(String aFilter) {
        addProperty("notificationFilter", aFilter);
        return this;
    }

    public JMXUriBuilder withHandback(String aHandback) {
        addProperty("handback", aHandback);
        return this;
    }
    
    public JMXUriBuilder withMonitorType(String aMonitorType) {
        addProperty("monitorType", aMonitorType);
        return this;
    }

    public JMXUriBuilder withInitThreshold(int aInitThreshold) {
        addProperty("initThreshold", String.valueOf(aInitThreshold));
        return this;
    }
    
    public JMXUriBuilder withOffset(int aOffset) {
        addProperty("offset", String.valueOf(aOffset));
        return this;
    }

    public JMXUriBuilder withModulus(int aModulus) {
        addProperty("modulus", String.valueOf(aModulus));
        return this;
    }

    public JMXUriBuilder withDifferenceMode(boolean aDifferenceMode) {
        addProperty("differenceMode", String.valueOf(aDifferenceMode));
        return this;
    }

    public JMXUriBuilder withGranularityPeriod(long aPeriod) {
        addProperty("granularityPeriod", String.valueOf(aPeriod));
        return this;
    }

    public JMXUriBuilder withObservedAttribute(String aObservedAttribute) {
        addProperty("observedAttribute", aObservedAttribute);
        return this;
    }
    
    public JMXUriBuilder withNotifyHigh(boolean aNotifyHigh) {
        addProperty("notifyHigh", String.valueOf(aNotifyHigh));
        return this;
    }

    public JMXUriBuilder withNotifyLow(boolean aNotifyLow) {
        addProperty("notifyLow", String.valueOf(aNotifyLow));
        return this;
    }

    public JMXUriBuilder withThresholdHigh(Number aThresholdHigh) {
        addProperty("thresholdHigh", String.valueOf(aThresholdHigh));
        return this;
    }

    public JMXUriBuilder withThresholdLow(Number aThresholdLow) {
        addProperty("thresholdLow", String.valueOf(aThresholdLow));
        return this;
    }

    public JMXUriBuilder withNotifyDiffer(boolean aNotifyDiffer) {
        addProperty("notifyDiffer", String.valueOf(aNotifyDiffer));
        return this;
    }

    public JMXUriBuilder withNotifyMatch(boolean aNotifyMatch) {
        addProperty("notifyMatch", String.valueOf(aNotifyMatch));
        return this;
    }

    public JMXUriBuilder withStringToCompare(String aStringToCompare) {
        addProperty("stringToCompare", aStringToCompare);
        return this;
    }
    
    public JMXUriBuilder withTestConnectionOnStartup(boolean aTestConnectionOnStartup) {
        addProperty("testConnectionOnStartup", String.valueOf(aTestConnectionOnStartup));
        return this;
    }
    
    public JMXUriBuilder withReconnectOnConnectionFailure(boolean aReconnectOnConnectionFailure) {
        addProperty("reconnectOnConnectionFailure", String.valueOf(aReconnectOnConnectionFailure));
        return this;
    }
    
    public JMXUriBuilder withReconnectDelay(int aReconnectDelay) {
        addProperty("reconnectDelay", String.valueOf(aReconnectDelay));
        return this;
    }

    /**
     * Converts all of the values to params with the "key." prefix so the
     * component will pick up on them and set them on the endpoint. Alternatively,
     * you can pass in a reference to a Hashtable using the version of this
     * method that takes a single string.
     */
    public JMXUriBuilder withObjectProperties(Map<String, String> aPropertiesSansKeyPrefix) {
        for (Entry<String, String> entry : aPropertiesSansKeyPrefix.entrySet()) {
            addProperty("key." + entry.getKey(), entry.getValue());
        }
        return this;
    }

    /**
     * Your value should start with a hash mark since it's a reference to a value.
     * This method will add the hash mark if it's not present.
     */
    public JMXUriBuilder withObjectPropertiesReference(String aReferenceToHashtable) {
        if (aReferenceToHashtable.startsWith("#")) {
            addProperty("objectProperties", aReferenceToHashtable);
        } else {
            addProperty("objectProperties", "#" + aReferenceToHashtable);
        }
        return this;
    }

    protected void addProperty(String aName, String aValue) {
        mQueryProps.put(aName, aValue);
    }

    public String getServerName() {
        return mServerName;
    }

    public void setServerName(String aServerName) {
        mServerName = aServerName;
    }

    public JMXUriBuilder withServerName(String aServerName) {
        setServerName(aServerName);
        return this;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("jmx:").append(getServerName());
        if (!mQueryProps.isEmpty()) {
            sb.append('?');

            String delim = "";
            for (Entry<String, String> entry : mQueryProps.entrySet()) {
                sb.append(delim);
                sb.append(entry.getKey()).append('=').append(entry.getValue());
                delim = "&";
            }
        }
        return sb.toString();
    }

}
