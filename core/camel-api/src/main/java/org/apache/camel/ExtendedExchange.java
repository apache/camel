package org.apache.camel;

/**
 * Extended {@link Exchange} which contains the methods and APIs that are not primary intended for Camel end users
 * but for SPI, custom components, or more advanced used-cases with Camel.
 */
public interface ExtendedExchange extends Exchange {

    /**
     * Sets the history node id (the current processor that will process the exchange)
     */
    void setHistoryNodeId(String historyNodeId);

    /**
     * Gets the history node id (the current processor that will process the exchange)
     */
    String getHistoryNodeId();

    /**
     * Sets the history node label (the current processor that will process the exchange)
     */
    void setHistoryNodeLabel(String historyNodeLabel);

    /**
     * Gets the history node label (the current processor that will process the exchange)
     */
    String getHistoryNodeLabel();

}
