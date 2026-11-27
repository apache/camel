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
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * Receive the notifications of an MBean on a local or remote MBean server, or of a JMX monitor that observes an MBean
 * attribute.
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
@BodyTypes(consumerOut = @BodyType(value = Carrier.TEXT, javaType = "java.lang.String", format = "xml",
                                   byOption = @BodyType.When(option = "format", value = "raw", carriers = Carrier.POJO,
                                                             javaType = "javax.management.Notification"),
                                   description = "The notification as XML text, such as an AttributeChangeNotification"
                                                 + " or a MonitorNotification element; with format=raw the Notification"
                                                 + " object. The handback object is in the jmx.handback header"))
public class JMXEndpoint extends DefaultEndpoint implements EndpointServiceLocation {

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

    @UriPath(description = "The MBean server to connect to: platform for the platform MBean server of the JVM that runs "
                           + "Camel, or the JMX service URL of a remote MBean server, for example "
                           + "service:jmx:rmi:///jndi/rmi://localhost:1099/jmxrmi. The monitorType option works only with "
                           + "platform.")
    private String serverURL;

    @UriParam(description = "The domain of the ObjectName of the MBean to listen to, for example java.lang. With "
                            + "monitorType, the consumer also registers its monitor MBean in this domain.")
    @Metadata(required = true)
    private String objectDomain;

    @UriParam(description = "The value of the name key of the ObjectName of the MBean to listen to: the consumer uses the "
                            + "ObjectName objectDomain:name=objectName. Set either this option or the objectProperties option,"
                            + " not both.")
    private String objectName;

    @UriParam(description = "The name of the MBean attribute to observe. With monitorType it is required, and the monitor "
                            + "observes this attribute. Without monitorType and without notificationFilter, the consumer "
                            + "receives only the AttributeChangeNotifications of this attribute, selected further by "
                            + "stringToCompare.")
    private String observedAttribute;

    @UriParam(description = "The period at which the monitor reads the value of the observed attribute (the granularity "
                            + "period of the JMX monitor). Only used with monitorType.",
              defaultValue = "10000", javaType = "java.time.Duration")
    private long granularityPeriod = 10000;

    @UriParam(description = "The type of the JMX monitor to create: counter (CounterMonitor), gauge (GaugeMonitor) or string"
                            + " (StringMonitor). If set, the consumer registers a monitor MBean on the platform MBean server "
                            + "that observes the observedAttribute of the MBean, and receives the notifications of the monitor"
                            + " instead of those of the MBean. Only supported when serverURL is platform.",
              enums = "counter,gauge,string")
    private String monitorType;

    @UriParam(description = "The initial threshold of the counter monitor, which sends a notification when the counter "
                            + "reaches or exceeds the threshold. The value is converted to the type of the observed attribute,"
                            + " which must be an integer type. If not set, the threshold is 0. Only used when monitorType is "
                            + "counter.",
              label = "counter")
    private int initThreshold;

    @UriParam(description = "The value that the counter monitor adds to the threshold each time the counter reaches it, so "
                            + "that it sends a notification each time the counter grows by this value. If not set (0), the "
                            + "threshold does not change. Only used when monitorType is counter.",
              label = "counter")
    private int offset;

    @UriParam(description = "The value at which the observed counter wraps around to zero. The counter monitor sets the "
                            + "threshold back to initThreshold when it exceeds this value, and in difference mode adds it to a"
                            + " negative difference. If not set (0), the counter is taken not to wrap around. Only used when "
                            + "monitorType is counter.",
              label = "counter")
    private int modulus;

    @UriParam(description = "Whether the counter or gauge monitor compares the difference between two successive "
                            + "observations of the attribute with the thresholds, instead of the value of the attribute "
                            + "itself. Only used when monitorType is counter or gauge.",
              label = "counter,gauge")
    private boolean differenceMode;

    @UriParam(description = "Whether the gauge monitor sends a notification when the observed value rises to or above "
                            + "thresholdHigh. After that it sends the next high notification only once the value has fallen to"
                            + " or below thresholdLow. When monitorType is gauge, notifyHigh, notifyLow or both must be true.",
              label = "gauge")
    private boolean notifyHigh;

    @UriParam(description = "Whether the gauge monitor sends a notification when the observed value falls to or below "
                            + "thresholdLow. After that it sends the next low notification only once the value has risen to or"
                            + " above thresholdHigh. When monitorType is gauge, notifyHigh, notifyLow or both must be true.",
              label = "gauge")
    private boolean notifyLow;

    @UriParam(description = "The high threshold of the gauge monitor, converted to the type of the observed attribute. It "
                            + "must not be lower than thresholdLow. Required when monitorType is gauge.",
              label = "gauge")
    private Double thresholdHigh;

    @UriParam(description = "The low threshold of the gauge monitor, converted to the type of the observed attribute. It "
                            + "must not be higher than thresholdHigh. Required when monitorType is gauge.",
              label = "gauge")
    private Double thresholdLow;

    @UriParam(description = "Whether to receive a notification when the value of the observed attribute differs from "
                            + "stringToCompare. With monitorType string, the string monitor sends one each time the value "
                            + "starts to differ. Without monitorType, the consumer then receives the attribute changes whose "
                            + "new value differs from stringToCompare, instead of those whose new value matches it.",
              label = "consumer,string")
    private boolean notifyDiffer;

    @UriParam(description = "Whether the string monitor sends a notification each time the value of the observed attribute "
                            + "starts to match stringToCompare. When monitorType is string, notifyMatch, notifyDiffer or both "
                            + "must be true. Without monitorType this option is not used: the consumer receives the matching "
                            + "attribute changes unless notifyDiffer is true.",
              label = "consumer,string")
    private boolean notifyMatch;

    @UriParam(description = "The text that the value of the observed attribute is compared with. Required when monitorType "
                            + "is string. Without monitorType, the consumer compares it with the new value of each "
                            + "AttributeChangeNotification of the observedAttribute (the text null matches a null value), and "
                            + "receives every change of the attribute when this option is not set.",
              label = "consumer,string")
    private String stringToCompare;

    @UriParam(description = "The format of the message body: xml for the notification as XML text, as defined by the "
                            + "notifications.xsd schema of camel-jmx, or raw for the javax.management.Notification object.",
              defaultValue = "xml", enums = "xml,raw")
    private String format = "xml";

    @UriParam(description = "The user name to authenticate with on a remote MBean server. The consumer passes user and "
                            + "password to the JMX connector as its credentials. Not used for the platform MBean server.",
              label = "security", security = "secret")
    private String user;

    @UriParam(description = "The password to authenticate with on a remote MBean server. The consumer passes user and "
                            + "password to the JMX connector as its credentials. Not used for the platform MBean server.",
              label = "security", security = "secret")
    private String password;

    @UriParam(description = "A javax.management.NotificationFilter that selects the notifications to receive. It replaces "
                            + "the filter that the consumer builds from observedAttribute and stringToCompare, and with "
                            + "monitorType it selects among the notifications of the monitor. A remote MBean server receives "
                            + "the filter serialized and applies it itself, so its class must be on the class path of that "
                            + "server.",
              label = "advanced")
    private NotificationFilter notificationFilter;

    @UriParam(description = "An object that the MBean server hands back with each notification, which the consumer puts in "
                            + "the jmx.handback header. With monitorType this option is not used, and the header holds the "
                            + "monitor MBean instead.",
              label = "advanced")
    private Object handback;

    @UriParam(description = "Whether the consumer fails to start when it cannot connect to a remote MBean server. If false, "
                            + "the consumer logs a warning and tries to connect again every reconnectDelay seconds until it "
                            + "succeeds.",
              label = "advanced", defaultValue = "true")
    private boolean testConnectionOnStartup = true;

    @UriParam(description = "Whether the consumer connects again to a remote MBean server when the connection is closed or "
                            + "fails, or notifications are lost. It tries every reconnectDelay seconds until it succeeds. When"
                            + " the JMX connector opens a new connection by itself, the consumer registers its listener again "
                            + "on that connection.",
              label = "advanced")
    private boolean reconnectOnConnectionFailure;

    @UriParam(description = "The number of seconds to wait before the next attempt to connect to a remote MBean server: at "
                            + "startup when testConnectionOnStartup is false, and after a lost connection when "
                            + "reconnectOnConnectionFailure is true.",
              label = "advanced", defaultValue = "10")
    private int reconnectDelay = 10;

    @UriParam(description = "The key properties of the ObjectName of the MBean to listen to, for example key.type=Threading "
                            + "for the ObjectName java.lang:type=Threading. Set either this option or the objectName option, "
                            + "not both.",
              label = "advanced", prefix = "key.", multiValue = true)
    private Map<String, String> objectProperties;

    @UriParam(description = "A shared thread pool that routes the notifications of the consumers that use it. If not set, "
                            + "each consumer creates its own single-threaded pool, which routes its notifications one at a "
                            + "time in the order they arrive.",
              label = "advanced")
    private ExecutorService executorService;

    /**
     * Cached object name that was built from the objectName param or the hashtable
     */
    private transient ObjectName jmxObjectName;

    public JMXEndpoint(String aEndpointUri, JMXComponent aComponent) {
        super(aEndpointUri, aComponent);
    }

    @Override
    public boolean isRemote() {
        return false;
    }

    @Override
    public String getServiceUrl() {
        return serverURL;
    }

    @Override
    public String getServiceProtocol() {
        return "rmi";
    }

    @Override
    public Map<String, String> getServiceMetadata() {
        if (user != null) {
            return Map.of("username", user);
        }
        return null;
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
