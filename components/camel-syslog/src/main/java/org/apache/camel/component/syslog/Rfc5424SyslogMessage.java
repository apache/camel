/**
 * 
 */
package org.apache.camel.component.syslog;

/**
 * @author svenrienstra
 * 
 */
public class Rfc5424SyslogMessage extends SyslogMessage {
    private String appName = "-";
    private String procId = "-";
    private String msgId = "-";
    private String structuredData = "-";

    /**
     * @return the appName
     */
    public String getAppName() {
	return appName;
    }

    /**
     * @param appName
     *            the appName to set
     */
    public void setAppName(String appName) {
	this.appName = appName;
    }

    /**
     * @return the procId
     */
    public String getProcId() {
	return procId;
    }

    /**
     * @param procId
     *            the procId to set
     */
    public void setProcId(String procId) {
	this.procId = procId;
    }

    /**
     * @return the msgId
     */
    public String getMsgId() {
	return msgId;
    }

    /**
     * @param msgId
     *            the msgId to set
     */
    public void setMsgId(String msgId) {
	this.msgId = msgId;
    }

    /**
     * @return the structuredData
     */
    public String getStructuredData() {
	return structuredData;
    }

    /**
     * @param structuredData
     *            the structuredData to set
     */
    public void setStructuredData(String structuredData) {
	this.structuredData = structuredData;
    }

}
