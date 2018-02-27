package org.apache.camel.component.as2.api.entity;

public class DispositionNotificationOptions {
    
    private Importance protocolImportance;
    private String protocolSymbol;
    private Importance micalgsImportance;
    private String[] micAlgorithms;
    
    public DispositionNotificationOptions(Importance protocolImportance, String protocolSymbol, Importance micalgsImportance, String[] micAlgorithms) {
        this.protocolImportance = protocolImportance;
        this.protocolSymbol = protocolSymbol;
        this.micalgsImportance = micalgsImportance;
        this.micAlgorithms = micAlgorithms;
    }

    public Importance getProtocolImportance() {
        return protocolImportance;
    }

    public String getProtocolSymbol() {
        return protocolSymbol;
    }

    public Importance getMicalgsImportance() {
        return micalgsImportance;
    }

    public String[] getMicAlgorithms() {
        return micAlgorithms;
    }
    
    
 }
