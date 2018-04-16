package org.apache.camel.component.as2.api;

public enum AS2MessageStructure {
    PLAIN(false, false, false), 
    SIGNED(false, false, false),
    ENCRYPTED(false, false, false),
    ENCRYPTED_SIGNED(false, false, false);
    
    private final boolean isSigned;
    private final boolean isEncrypted;
    private final boolean isCompressed;
    
    private AS2MessageStructure(boolean isSigned, boolean isEncrypted, boolean isCompressed) {
        this.isSigned = isSigned;
        this.isEncrypted = isEncrypted;
        this.isCompressed = isCompressed;
    }
    
    public boolean isSigned() {
        return isSigned;
    }
    public boolean isEncrypted() {
        return isEncrypted;
    }
    public boolean isCompressed() {
        return isCompressed;
    }
}
