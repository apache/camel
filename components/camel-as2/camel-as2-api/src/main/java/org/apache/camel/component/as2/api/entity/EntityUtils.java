package org.apache.camel.component.as2.api.entity;

import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.component.as2.api.AS2MediaType;
import org.apache.commons.codec.binary.Base64OutputStream;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.util.Args;

public class EntityUtils {

    private static AtomicLong partNumber = new AtomicLong();

    /**
     * Generated a unique value for a Multipart boundary string.
     * <p>
     * The boundary string is composed of the components:
     * "----=_Part_&lt;global_part_number&gt;_&lt;newly_created_object's_hashcode&gt;.&lt;current_time&gt;"
     * <p>
     * The generated string contains only US-ASCII characters and hence is safe
     * for use in RFC822 headers.
     * 
     * @return The generated boundary string.
     */
    public static String createBoundaryValue() {
        // TODO: ensure boundary string is limited to 70 characters or less.
        StringBuffer s = new StringBuffer();
        s.append("----=_Part_").append(partNumber.incrementAndGet()).append("_").append(s.hashCode()).append(".")
                .append(System.currentTimeMillis());
        return s.toString();
    }

    public static boolean validateBoundaryValue(String boundaryValue) {
        return true; // TODO: add validation logic.
    }

    public static String appendParameter(String headerString, String parameterName, String parameterValue) {
        return headerString + "; " + parameterName + "=" + parameterValue;
    }
    
    public static OutputStream encode(OutputStream os, String encoding) throws Exception {
        Args.notNull(os, "Output Stream");
        
        if (encoding == null) {
            // Identity encoding
            return os;
        }
        switch (encoding.toLowerCase()) {
        case "base64":
            return new Base64OutputStream(os, true);
        case "quoted-printable":
            // TODO: implement QuotedPrintableOutputStream
            return new Base64OutputStream(os, true);
        case "binary":
        case "7bit":
        case "8bit":
            // Identity encoding
            return os;
        default:
            throw new Exception("Unknown encoding: " + encoding);
        }
    }
    
    public static ApplicationEDIEntity createEDIEntity(String ediMessage, ContentType ediMessageContentType, String contentTransferEncoding, boolean isMainBody) throws Exception {
        Args.notNull(ediMessage, "EDI Message");
        Args.notNull(ediMessageContentType, "EDI Message Content Type");
        
        switch(ediMessageContentType.getMimeType().toLowerCase()) {
        case AS2MediaType.APPLICATION_EDIFACT:
            return new ApplicationEDIFACTEntity(ediMessage, ediMessageContentType.getCharset().toString(), contentTransferEncoding, isMainBody);            
        case AS2MediaType.APPLICATION_EDI_X12:
            return new ApplicationEDIX12Entity(ediMessage, ediMessageContentType.getCharset().toString(), contentTransferEncoding, isMainBody);            
        case AS2MediaType.APPLICATION_EDI_CONSENT:
            return new ApplicationEDIConsentEntity(ediMessage, ediMessageContentType.getCharset().toString(), contentTransferEncoding, isMainBody);            
        default:
            throw new Exception("Invalid EDI entity mime type: " + ediMessageContentType.getMimeType());
        }
        
    }

    public static void parseAS2MessageEntity(HttpRequest request) throws Exception {
        HttpEntity entity = null;
        if (request instanceof HttpEntityEnclosingRequest) {
            entity = ((HttpEntityEnclosingRequest) request).getEntity();
            if (entity.getContentType() != null) {
                ContentType contentType;
                try {
                    contentType =  ContentType.parse(entity.getContentType().getValue());
                } catch (Exception e) {
                    throw new Exception("Failed to get Content Type", e);
                }
                switch (contentType.getMimeType().toLowerCase()) {
                case "application/edifact":
                    entity = ApplicationEDIEntity.parseEntity(entity, true);
                    ((HttpEntityEnclosingRequest) request).setEntity(entity);
                    break;
                case "application/edi-x12":
                    break;
                case "application/consent":
                    break;
                case "multipart/signed":
                    break;
                case "application/pkcs7-mime":
                    break;
                case "message/disposition-notification":
                    break;
                default:
                    break;
                }
            }
        }
    }
    
}
