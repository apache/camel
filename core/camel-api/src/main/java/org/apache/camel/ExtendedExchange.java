package org.apache.camel;

/**
 * Extended {@link Exchange} which contains the methods and APIs that are not primary intended for Camel end users
 * but for SPI, custom components, or more advanced used-cases with Camel.
 */
public interface ExtendedExchange extends Exchange {

    // TODO: javadoc these

    void setHistoryNodeId(String historyNodeId);
    String getHistoryNodeId();

    void setHistoryNodeLabel(String historyNodeLabel);
    String getHistoryNodeLabel();

}
