package org.apache.camel.component.as2.api;

public interface AS2MimeType {
    /**
     * Mime Type for Multipart Signed Data
     */
    public static final String MULTIPART_SIGNED = "multipart/signed";
    /**
     * Mime Type for Application PKCS7 Signature
     */
    public static final String APPLICATION_PKCS7_SIGNATURE = "application/pkcs7-signature";
    /**
     * Mime Type for Text/Plain Data
     */
    public static final String TEXT_PLAIN = "text/plain";
    /**
     * Mime Type for Application/EDIFACT
     */
    public static final String APPLICATION_EDIFACT = "application/edifact";
    /**
     * Mime Type for Application/EDI-X12
     */
    public static final String APPLICATION_EDI_X12 = "application/edi-x12";
    /**
     * Mime Type for Application/EDI-consent
     */
    public static final String APPLICATION_EDI_CONSENT = "application/edi-consent";

}
