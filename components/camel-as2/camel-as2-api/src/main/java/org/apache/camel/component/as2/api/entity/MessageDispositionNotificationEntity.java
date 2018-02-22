package org.apache.camel.component.as2.api.entity;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.component.as2.api.AS2MimeType;
import org.apache.http.entity.ContentType;
import org.apache.http.util.Args;

public class MessageDispositionNotificationEntity extends MimeEntity {
    
    String reportingUA; // Optional
    String mtnName; // Optional
    String originalRecipient; // Optional
    String finalRecipient; // Required
    String originalMessageId; // Optional
    DispositionMode dispositionMode;
    DispositionType dispositionType;
    String dispositionModifier;
    String error; // Optional
    Map<String,String> extensions = new HashMap<String,String>();
    
    public MessageDispositionNotificationEntity(String reportingUA, String mtnName, String originalRecipient,
            String finalRecipient, DispositionMode dispositionMode, DispositionType dispositionType,
            String dispositionModifier, String error, Map<String, String> extensions, String charset,
            boolean isMainBody) {
        setMainBody(isMainBody);
        setContentType(ContentType.create(AS2MimeType.MESSAGE_DISPOSITION_NOTIFICATION, charset));
        this.reportingUA = reportingUA;
        this.mtnName = mtnName;
        this.originalRecipient = originalRecipient;
        this.finalRecipient = Args.notNull(finalRecipient, "Final Recipient");
        this.originalRecipient = originalRecipient;
        this.dispositionMode = Args.notNull(dispositionMode, "Disposition Mode");
        this.dispositionType = Args.notNull(dispositionType, "Disposition Type");
        this.dispositionModifier = dispositionModifier;
        this.error = error;
        if (extensions == null || extensions.isEmpty()) {
            this.extensions.clear();
        } else {
            this.extensions.putAll(extensions);
        }

    }

    public String getReportingUA() {
        return reportingUA;
    }

    public String getMtnName() {
        return mtnName;
    }

    public String getOriginalRecipient() {
        return originalRecipient;
    }

    public String getFinalRecipient() {
        return finalRecipient;
    }

    public String getOriginalMessageId() {
        return originalMessageId;
    }

    public DispositionMode getDispositionMode() {
        return dispositionMode;
    }

    public DispositionType getDispositionType() {
        return dispositionType;
    }

    public String getDispositionModifier() {
        return dispositionModifier;
    }

    public String getError() {
        return error;
    }

    public Map<String, String> getExtensions() {
        return extensions;
    }

    @Override
    public void writeTo(OutputStream outstream) throws IOException {
    }

}
