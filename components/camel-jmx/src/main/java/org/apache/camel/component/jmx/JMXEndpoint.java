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
package org.apache.camel.component.jmx;

import java.util.Hashtable;
import javax.management.MalformedObjectNameException;
import javax.management.NotificationFilter;
import javax.management.ObjectName;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * Endpoint that describes a connection to an mbean.
 * <p/>
 * The component can connect to the local platform mbean server with the following URI:
 * <p/>
 * <code>jmx://platform?options</code>
 * <p/>
 * A remote mbean server url can be provided following the initial JMX scheme like so:
 * <p/>
 * <code>jmx:service:jmx:rmi:///jndi/rmi://localhost:1099/jmxrmi?options</code>
 * <p/>
 * You can append query options to the URI in the following format, ?options=value&option2=value&...
 *
 * @author markford
 */
public class JMXEndpoint extends DefaultEndpoint {

    // error messages as constants so they can be asserted on from unit tests
    protected static final String ERR_PLATFORM_SERVER = "Monitor type consumer only supported on platform server.";
    protected static final String ERR_THRESHOLD_LOW = "ThresholdLow must be set when monitoring a gauge attribute.";
    protected static final String ERR_THRESHOLD_HIGH = "ThresholdHigh must be set when monitoring a gauge attribute.";
    protected static final String ERR_GAUGE_NOTIFY = "One or both of NotifyHigh and NotifyLow must be true when monitoring a gauge attribute.";
    protected static final String ERR_STRING_NOTIFY = "One or both of NotifyDiffer and NotifyMatch must be true when monitoring a string attribute.";
    protected static final String ERR_STRING_TO_COMPARE = "StringToCompare must be specified when monitoring a string attribute.";
    protected static final String ERR_OBSERVED_ATTRIBUTE = "Observed attribute must be specified";

    /**
     * URI Property: [monitor types only] The attribute to observe for the monitor bean.  
     */
    private String mObservedAttribute;

    /**
     * URI Property: [monitor types only] The frequency to poll the bean to check the monitor.  
     */
    private long mGranularityPeriod;

    /**
     * URI Property: [monitor types only] The type of monitor to create. One of string, gauge, counter.  
     */
    private String mMonitorType;

    /**
     * URI Property: [counter monitor only] Initial threshold for the monitor. The value must exceed this before notifications are fired.  
     */
    private int mInitThreshold;

    /**
     * URI Property: [counter monitor only] The amount to increment the threshold after it's been exceeded.  
     */
    private int mOffset;

    /**
     * URI Property: [counter monitor only] The value at which the counter is reset to zero  
     */
    private int mModulus;

    /**
     * URI Property: [counter + gauge monitor only] If true, then the value reported in the notification is the difference from the threshold as opposed to the value itself.  
     */
    private boolean mDifferenceMode;

    /**
     * URI Property: [gauge monitor only] If true, the gauge will fire a notification when the high threshold is exceeded  
     */
    private boolean mNotifyHigh;

    /**
     * URI Property: [gauge monitor only] If true, the gauge will fire a notification when the low threshold is exceeded  
     */
    private boolean mNotifyLow;

    /**
     * URI Property: [gauge monitor only] Value for the gauge's high threshold  
     */
    private Double mThresholdHigh;

    /**
     * URI Property: [gauge monitor only] Value for the gauge's low threshold  
     */
    private Double mThresholdLow;

    /**
     * URI Property: [string monitor only] If true, the string monitor will fire a notification when the string attribute differs from the string to compare.  
     */
    private boolean mNotifyDiffer;

    /**
     * URI Property: [string monitor only] If true, the string monitor will fire a notification when the string attribute matches the string to compare.  
     */
    private boolean mNotifyMatch;

    /**
     * URI Property: [string monitor only] Value for the string monitor's string to compare.  
     */
    private String mStringToCompare;
    
    /**
     * URI Property: Format for the message body. Either "xml" or "raw". If xml, the notification is serialized to xml. If raw, then the raw java object is set as the body.
     */
    private String mFormat = "xml";

    /**
     * URI Property: credentials for making a remote connection
     */
    private String mUser;

    /**
     * URI Property: credentials for making a remote connection
     */
    private String mPassword;

    /**
     * URI Property: The domain for the mbean you're connecting to
     */
    private String mObjectDomain;

    /**
     * URI Property: The name key for the mbean you're connecting to. This value is mutually exclusive with the object properties that get passed.
     */
    private String mObjectName;

    /**
     * URI Property: Reference to a bean that implements the NotificationFilter.
     */
    private NotificationFilter mNotificationFilter;

    /**
     * URI Property: Value to handback to the listener when a notification is received. This value will be put in the message header with the key "jmx.handback"
     */
    private Object mHandback;

    /**
     * URI Property: properties for the object name. These values will be used if the objectName param is not set
     */
    private Hashtable<String, String> mObjectProperties;

    /**
     * cached object name that was built from the objectName param or the hashtable
     */
    private ObjectName mJMXObjectName;
    /**
     * server url comes from the remaining endpoint
     */
    private String mServerURL;

    public JMXEndpoint(String aEndpointUri, JMXComponent aComponent) {
        super(aEndpointUri, aComponent);
    }

    public Consumer createConsumer(Processor aProcessor) throws Exception {
        // validate that all of the endpoint is configured properly
        if (getMonitorType() != null) {
            
            if (!isPlatformServer()) {
                throw new IllegalArgumentException(ERR_PLATFORM_SERVER);
            }
            
            if (ObjectHelper.isEmpty(getObservedAttribute())) {
                throw new IllegalArgumentException(ERR_OBSERVED_ATTRIBUTE);
            }
            if (getMonitorType().equals("string")) {
                if (ObjectHelper.isEmpty(getStringToCompare())) {
                    throw new IllegalArgumentException(ERR_STRING_TO_COMPARE);
                }
                if (!isNotifyDiffer() && !isNotifyMatch()) {
                    throw new IllegalArgumentException(ERR_STRING_NOTIFY);
                }
            } else if (getMonitorType().equals("gauge")) {
                if (!isNotifyHigh() && !isNotifyLow()) {
                    throw new IllegalArgumentException(ERR_GAUGE_NOTIFY);
                }
                if (getThresholdHigh() == null) {
                    throw new IllegalArgumentException(ERR_THRESHOLD_HIGH);
                }
                if (getThresholdLow() == null) {
                    throw new IllegalArgumentException(ERR_THRESHOLD_LOW);
                }
            }
            return new JMXMonitorConsumer(this, aProcessor);
        } else {
            // shouldn't need any other validation.
            return new JMXConsumer(this, aProcessor);
        }
    }

    public Producer createProducer() throws Exception {
        throw new UnsupportedOperationException("producing JMX notifications is not supported");
    }

    public boolean isSingleton() {
        return false;
    }

    public String getFormat() {
        return mFormat;
    }

    public void setFormat(String aFormat) {
        mFormat = aFormat;
    }

    public boolean isXML() {
        return "xml".equals(getFormat());
    }

    public boolean isPlatformServer() {
        return "platform".equals(getServerURL());
    }

    public String getUser() {
        return mUser;
    }

    public void setUser(String aUser) {
        mUser = aUser;
    }

    public String getPassword() {
        return mPassword;
    }

    public void setPassword(String aPassword) {
        mPassword = aPassword;
    }

    public String getObjectDomain() {
        return mObjectDomain;
    }

    public void setObjectDomain(String aObjectDomain) {
        mObjectDomain = aObjectDomain;
    }

    public String getObjectName() {
        return mObjectName;
    }

    public void setObjectName(String aObjectName) {
        if (getObjectProperties() != null) {
            throw new IllegalArgumentException("Cannot set both objectName and objectProperties");
        }
        mObjectName = aObjectName;
    }

    protected String getServerURL() {
        return mServerURL;
    }

    protected void setServerURL(String aServerURL) {
        mServerURL = aServerURL;
    }

    public NotificationFilter getNotificationFilter() {
        return mNotificationFilter;
    }

    public void setNotificationFilter(NotificationFilter aFilterRef) {
        mNotificationFilter = aFilterRef;
    }

    public Object getHandback() {
        return mHandback;
    }

    public void setHandback(Object aHandback) {
        mHandback = aHandback;
    }

    public Hashtable<String, String> getObjectProperties() {
        return mObjectProperties;
    }

    /**
     * Setter for the ObjectProperties is either called by reflection when
     * processing the URI or manually by the component.
     * <p/>
     * If the URI contained a value with a reference like "objectProperties=#myHashtable"
     * then the Hashtable will be set in place.
     * <p/>
     * If there are extra properties that begin with "key." then the component will
     * create a Hashtable with these values after removing the "key." prefix.
     */
    public void setObjectProperties(Hashtable<String, String> aObjectProperties) {
        if (getObjectName() != null) {
            throw new IllegalArgumentException("Cannot set both objectName and objectProperties");
        }
        mObjectProperties = aObjectProperties;
    }

    protected ObjectName getJMXObjectName() throws MalformedObjectNameException {
        if (mJMXObjectName == null) {
            ObjectName on = buildObjectName();
            setJMXObjectName(on);
        }
        return mJMXObjectName;
    }

    protected void setJMXObjectName(ObjectName aCachedObjectName) {
        mJMXObjectName = aCachedObjectName;
    }

    public String getObservedAttribute() {
        return mObservedAttribute;
    }

    public void setObservedAttribute(String aObservedAttribute) {
        mObservedAttribute = aObservedAttribute;
    }

    public long getGranularityPeriod() {
        return mGranularityPeriod;
    }

    public void setGranularityPeriod(long aGranularityPeriod) {
        mGranularityPeriod = aGranularityPeriod;
    }

    public String getMonitorType() {
        return mMonitorType;
    }

    public void setMonitorType(String aMonitorType) {
        mMonitorType = aMonitorType;
    }

    public int getInitThreshold() {
        return mInitThreshold;
    }

    public void setInitThreshold(int aInitThreshold) {
        mInitThreshold = aInitThreshold;
    }

    public int getOffset() {
        return mOffset;
    }

    public void setOffset(int aOffset) {
        mOffset = aOffset;
    }

    public int getModulus() {
        return mModulus;
    }

    public void setModulus(int aModulus) {
        mModulus = aModulus;
    }

    public boolean isDifferenceMode() {
        return mDifferenceMode;
    }

    public void setDifferenceMode(boolean aDifferenceMode) {
        mDifferenceMode = aDifferenceMode;
    }

    public boolean isNotifyHigh() {
        return mNotifyHigh;
    }

    public void setNotifyHigh(boolean aNotifyHigh) {
        mNotifyHigh = aNotifyHigh;
    }

    public boolean isNotifyLow() {
        return mNotifyLow;
    }

    public void setNotifyLow(boolean aNotifyLow) {
        mNotifyLow = aNotifyLow;
    }

    public Double getThresholdHigh() {
        return mThresholdHigh;
    }

    public void setThresholdHigh(Double aThresholdHigh) {
        mThresholdHigh = aThresholdHigh;
    }

    public Double getThresholdLow() {
        return mThresholdLow;
    }

    public void setThresholdLow(Double aThresholdLow) {
        mThresholdLow = aThresholdLow;
    }

    public boolean isNotifyDiffer() {
        return mNotifyDiffer;
    }

    public void setNotifyDiffer(boolean aNotifyDiffer) {
        mNotifyDiffer = aNotifyDiffer;
    }

    public boolean isNotifyMatch() {
        return mNotifyMatch;
    }

    public void setNotifyMatch(boolean aNotifyMatch) {
        mNotifyMatch = aNotifyMatch;
    }

    public String getStringToCompare() {
        return mStringToCompare;
    }

    public void setStringToCompare(String aStringToCompare) {
        mStringToCompare = aStringToCompare;
    }

    private ObjectName buildObjectName() throws MalformedObjectNameException {
        ObjectName objectName;
        if (getObjectProperties() == null) {
            StringBuilder sb = new StringBuilder(getObjectDomain()).append(':').append("name=").append(getObjectName());
            objectName = new ObjectName(sb.toString());
        } else {
            objectName = new ObjectName(getObjectDomain(), getObjectProperties());
        }
        return objectName;
    }
}
