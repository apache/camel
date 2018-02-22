package org.apache.camel.component.as2.api.entity;

import org.apache.camel.component.as2.api.AS2CharSet;
import org.apache.camel.component.as2.api.AS2MimeType;
import org.apache.camel.component.as2.api.AS2TransferEncoding;
import org.apache.http.entity.ContentType;
import org.apache.http.util.CharArrayBuffer;

public class MultipartReportEntity extends MultipartMimeEntity {
    
    public MultipartReportEntity(String charset, boolean isMainBody, String boundary) {
        super(ContentType.create(AS2MimeType.MULTIPART_REPORT, charset), isMainBody, boundary);
        addPart(buildPlainTextReport());
    }
    
    protected TextPlainEntity buildPlainTextReport() {

        CharArrayBuffer charBuffer = new CharArrayBuffer(10);
        
        charBuffer.append("MDN for -\n");
        charBuffer.append(" Message ID: " + "\n");
        charBuffer.append("  From: " + "\n");
        charBuffer.append("  To: " + "\n");
        charBuffer.append("  Received on: " + "\n");
        charBuffer.append(" Status: " + "\n");
        charBuffer.append(" Comment: " + "\n");
        
        return new TextPlainEntity(charBuffer.toString(), AS2CharSet.US_ASCII, AS2TransferEncoding._7BIT, false);
    }
    
    protected MimeEntity buildDispositionNotificationReport() {
        
        return null;
    }

}
