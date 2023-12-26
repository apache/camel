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

import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import javax.management.MalformedObjectNameException;
import javax.management.NotificationFilter;
import javax.management.ObjectName;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * Receive JMX notifications.
 *
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
 */
@UriEndpoint(firstVersion = "2.6.0", scheme = "jmx", title = "JMX", syntax = "jmx:serverURL", consumerOnly = true,
             remote = false, category = { Category.MONITORING }, headersClass = JMXConstants.class)
public class JMXEndpoint extends DefaultEndpoint {

    // error messages as constants so they can be asserted on from unit tests
    protected static final String ERR_PLATFORM_SERVER = "Monitor type consumer only supported on platform server.";
    protected static final String ERR_THRESHOLD_LOW = "ThresholdLow must be set when monitoring a gauge attribute.";
    protected static final String ERR_THRESHOLD_HIGH = "ThresholdHigh must be set when monitoring a gauge attribute.";
    protected static final String ERR_GAUGE_NOTIFY
            = "One or both of NotifyHigh and NotifyLow must be true when monitoring a gauge attribute.";
    protected static final String ERR_STRING_NOTIFY
            = "One or both of NotifyDiffer and NotifyMatch must be true when monitoring a string attribute.";
    protected static final String ERR_STRING_TO_COMPARE
            = "StringToCompare must be specified when monitoring a string attribute.";
    protected static final String ERR_OBSERVED_ATTRIBUTE = "Observed attribute must be specified";

    /**
     * Server url comes from the remaining endpoint. Use platform to connect to local JVM.
     */
    @UriPath
    private String serverURL;

    /**
     * The domain for the mbean you're connecting to
     */
    @UriParam
    @Metadata(required = true)
    private String objectDomain;

    /**
     * The name key for the mbean you're connecting to. This value is mutually exclusive with the object properties that
     * get passed.
     */
    @UriParam
    private String objectName;

    /**
     * The attribute to observe for the monitor bean or consumer.
     */
    @UriParam
    private String observedAttribute;

    /**
     * The frequency to poll the bean to check the monitor (monitor types only).
     */
    @UriParam(defaultValue = "10000", javaType = "java.time.Duration")
    private long granularityPeriod = 10000;

    /**
     * The type of monitor to create. One of string, gauge, counter (monitor types only).
     */
    @UriParam(enums = "counter,gauge,string")
    private String monitorType;

    /**
     * Initial threshold for the monitor. The value must exceed this before notifications are fired (counter monitor
     * only).
     */
    @UriParam(label = "counter")
    private int initThreshold;

    /**
     * The amount to increment the threshold after it's been exceeded (counter monitor only).
     */
    @UriParam(label = "counter")
    private int offset;

    /**
     * The value at which the counter is reset to zero (counter monitor only).
     */
    @UriParam(label = "counter")
    private int modulus;

    /**
     * If true, then the value reported in the notification is the difference from the threshold as opposed to the value
     * itself (counter and gauge monitor only).
     */
    @UriParam(label = "counter,gauge")
    private boolean differenceMode;

    /**
     * If true, the gauge will fire a notification when the high threshold is exceeded (gauge monitor only).
     */
    @UriParam(label = "gauge")
    private boolean notifyHigh;

    /**
     * If true, the gauge will fire a notification when the low threshold is exceeded (gauge monitor only).
     */
    @UriParam(label = "gauge")
    private boolean notifyLow;

    /**
     * Value for the gauge's high threshold (gauge monitor only).
     */
    @UriParam(label = "gauge")
    private Double thresholdHigh;

    /**
     * Value for the gauge's low threshold (gauge monitor only).
     */
    @UriParam(label = "gauge")
    private Double thresholdLow;

    /**
     * If true, will fire a notification when the string attribute differs from the string to compare (string monitor or
     * consumer). By default the consumer will notify match if observed attribute and string to compare has been
     * configured.
     */
    @UriParam(label = "consumer,string")
    private boolean notifyDiffer;

    /**
     * If true, will fire a notification when the string attribute matches the string to compare (string monitor or
     * consumer). By default the consumer will notify match if observed attribute and string to compare has been
     * configured.
     */
    @UriParam(label = "consumer,string")
    private boolean notifyMatch;

    /**
     * Value for attribute to compare (string monitor or consumer). By default the consumer will notify match if
     * observed attribute and string to compare has been configured.
     */
    @UriParam(label = "consumer,string")
    private String stringToCompare;

    /**
     * Format for the message body. Either "xml" or "raw". If xml, the notification is serialized to xml. If raw, then
     * the raw java object is set as the body.
     */
    @UriParam(defaultValue = "xml", enums = "xml,raw")
    private String format = "xml";

    /**
     * Credentials for making a remote connection
     */
    @UriParam(label = "security", secret = true)
    private String user;

    /**
     * Credentials for making a remote connection
     */
    @UriParam(label = "security", secret = true)
    private String password;

    /**
     * Reference to a bean that implements the NotificationFilter.
     */
    @UriParam(label = "advanced")
    private NotificationFilter notificationFilter;

    /**
     * Value to handback to the listener when a notification is received. This value will be put in the message header
     * with the key {@link JMXConstants#JMX_HANDBACK}.
     */
    @UriParam(label = "advanced")
    private Object handback;

    /**
     * If true the consumer will throw an exception if unable to establish the JMX connection upon startup. If false,
     * the consumer will attempt to establish the JMX connection every 'x' seconds until the connection is made -- where
     * 'x' is the configured reconnectionDelay
     */
    @UriParam(label = "advanced", defaultValue = "true")
    private boolean testConnectionOnStartup = true;

    /**
     * If true the consumer will attempt to reconnect to the JMX server when any connection failure occurs. The consumer
     * will attempt to re-establish the JMX connection every 'x' seconds until the connection is made-- where 'x' is the
     * configured reconnectionDelay
     */
    @UriParam(label = "advanced")
    private boolean reconnectOnConnectionFailure;

    /**
     * The number of seconds to wait before attempting to retry establishment of the initial connection or attempt to
     * reconnect a lost connection
     */
    @UriParam(label = "advanced", defaultValue = "10")
    private int reconnectDelay = 10;

    /**
     * Properties for the object name. These values will be used if the objectName param is not set
     */
    @UriParam(label = "advanced", prefix = "key.", multiValue = true)
    private Map<String, String> objectProperties;

    /**
     * To use a custom shared thread pool for the consumers. By default each consume has their own thread-pool to
     * process and route notifications.
     */
    @UriParam(label = "advanced")
    private ExecutorService executorService;

    /**
     * Cached object name that was built from the objectName param or the hashtable
     */
    private transient ObjectName jmxObjectName;

    public JMXEndpoint(String aEndpointUri, JMXComponent aComponent) {
        super(aEndpointUri, aComponent);
    }

    @Override
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
            JMXMonitorConsumer answer = new JMXMonitorConsumer(this, aProcessor);
            configureConsumer(answer);
            return answer;
        } else {
            // shouldn't need any other validation.
            JMXConsumer answer = new JMXConsumer(this, aProcessor);
            configureConsumer(answer);
            return answer;
        }
    }

    @Override
    public Producer createProducer() throws Exception {
        throw new UnsupportedOperationException("producing JMX notifications is not supported");
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String aFormat) {
        format = aFormat;
    }

    public boolean isXML() {
        return "xml".equals(getFormat());
    }

    public boolean isPlatformServer() {
        return "platform".equals(getServerURL());
    }

    public String getUser() {
        return user;
    }

    public void setUser(String aUser) {
        user = aUser;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String aPassword) {
        password = aPassword;
    }

    public String getObjectDomain() {
        return objectDomain;
    }

    public void setObjectDomain(String aObjectDomain) {
        objectDomain = aObjectDomain;
    }

    public String getObjectName() {
        return objectName;
    }

    public void setObjectName(String aObjectName) {
        if (getObjectProperties() != null) {
            throw new IllegalArgumentException("Cannot set both objectName and objectProperties");
        }
        objectName = aObjectName;
    }

    protected String getServerURL() {
        return serverURL;
    }

    protected void setServerURL(String aServerURL) {
        serverURL = aServerURL;
    }

    public NotificationFilter getNotificationFilter() {
        return notificationFilter;
    }

    public void setNotificationFilter(NotificationFilter aFilterRef) {
        notificationFilter = aFilterRef;
    }

    public Object getHandback() {
        return handback;
    }

    public void setHandback(Object aHandback) {
        handback = aHandback;
    }

    public Map<String, String> getObjectProperties() {
        return objectProperties;
    }

    /**
     * Setter for the ObjectProperties is either called by reflection when processing the URI or manually by the
     * component.
     * <p/>
     * If the URI contained a value with a reference like "objectProperties=#myHashtable" then the Hashtable will be set
     * in place.
     * <p/>
     * If there are extra properties that begin with "key." then the component will create a Hashtable with these values
     * after removing the "key." prefix.
     */
    public void setObjectProperties(Map<String, String> objectProperties) {
        if (getObjectName() != null) {
            throw new IllegalArgumentException("Cannot set both objectName and objectProperties");
        }
        this.objectProperties = objectProperties;
    }

    protected ObjectName getJMXObjectName() throws MalformedObjectNameException {
        if (jmxObjectName == null) {
            ObjectName on = buildObjectName();
            setJMXObjectName(on);
        }
        return jmxObjectName;
    }

    protected void setJMXObjectName(ObjectName aCachedObjectName) {
        jmxObjectName = aCachedObjectName;
    }

    public String getObservedAttribute() {
        return observedAttribute;
    }

    public void setObservedAttribute(String aObservedAttribute) {
        observedAttribute = aObservedAttribute;
    }

    public long getGranularityPeriod() {
        return granularityPeriod;
    }

    public void setGranularityPeriod(long aGranularityPeriod) {
        granularityPeriod = aGranularityPeriod;
    }

    public String getMonitorType() {
        return monitorType;
    }

    public void setMonitorType(String aMonitorType) {
        monitorType = aMonitorType;
    }

    public int getInitThreshold() {
        return initThreshold;
    }

    public void setInitThreshold(int aInitThreshold) {
        initThreshold = aInitThreshold;
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int aOffset) {
        offset = aOffset;
    }

    public int getModulus() {
        return modulus;
    }

    public void setModulus(int aModulus) {
        modulus = aModulus;
    }

    public boolean isDifferenceMode() {
        return differenceMode;
    }

    public void setDifferenceMode(boolean aDifferenceMode) {
        differenceMode = aDifferenceMode;
    }

    public boolean isNotifyHigh() {
        return notifyHigh;
    }

    public void setNotifyHigh(boolean aNotifyHigh) {
        notifyHigh = aNotifyHigh;
    }

    public boolean isNotifyLow() {
        return notifyLow;
    }

    public void setNotifyLow(boolean aNotifyLow) {
        notifyLow = aNotifyLow;
    }

    public Double getThresholdHigh() {
        return thresholdHigh;
    }

    public void setThresholdHigh(Double aThresholdHigh) {
        thresholdHigh = aThresholdHigh;
    }

    public Double getThresholdLow() {
        return thresholdLow;
    }

    public void setThresholdLow(Double aThresholdLow) {
        thresholdLow = aThresholdLow;
    }

    public boolean isNotifyDiffer() {
        return notifyDiffer;
    }

    public void setNotifyDiffer(boolean aNotifyDiffer) {
        notifyDiffer = aNotifyDiffer;
    }

    public boolean isNotifyMatch() {
        return notifyMatch;
    }

    public void setNotifyMatch(boolean aNotifyMatch) {
        notifyMatch = aNotifyMatch;
    }

    public String getStringToCompare() {
        return stringToCompare;
    }

    public void setStringToCompare(String aStringToCompare) {
        stringToCompare = aStringToCompare;
    }

    public boolean isTestConnectionOnStartup() {
        return this.testConnectionOnStartup;
    }

    public void setTestConnectionOnStartup(boolean testConnectionOnStartup) {
        this.testConnectionOnStartup = testConnectionOnStartup;
    }

    public boolean isReconnectOnConnectionFailure() {
        return this.reconnectOnConnectionFailure;
    }

    public void setReconnectOnConnectionFailure(boolean reconnectOnConnectionFailure) {
        this.reconnectOnConnectionFailure = reconnectOnConnectionFailure;
    }

    public int getReconnectDelay() {
        return this.reconnectDelay;
    }

    public void setReconnectDelay(int reconnectDelay) {
        this.reconnectDelay = reconnectDelay;
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    private ObjectName buildObjectName() throws MalformedObjectNameException {
        ObjectName objectName;
        if (getObjectProperties() == null) {
            StringBuilder sb = new StringBuilder(getObjectDomain()).append(':').append("name=").append(getObjectName());
            objectName = new ObjectName(sb.toString());
        } else {
            Hashtable<String, String> ht = new Hashtable<>(getObjectProperties());
            objectName = new ObjectName(getObjectDomain(), ht);
        }
        return objectName;
    }
}
