package org.apache.camel.component.as2.api.util;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

import org.apache.camel.component.as2.api.AS2CharSet;
import org.apache.camel.component.as2.api.AS2Header;
import org.apache.camel.component.as2.api.AS2MicAlgorithm;
import org.apache.camel.component.as2.api.AS2MimeType;
import org.apache.camel.component.as2.api.entity.ApplicationEDIEntity;
import org.apache.camel.component.as2.api.entity.DispositionNotificationOptions;
import org.apache.camel.component.as2.api.entity.DispositionNotificationOptionsParser;
import org.apache.camel.component.as2.api.entity.EntityParser;
import org.apache.camel.component.as2.api.entity.EntityUtils;
import org.apache.camel.component.as2.api.entity.MultipartSignedEntity;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.entity.ContentType;

public class MicUtils {
    
    public static byte[] createMic(byte[] content, String algorithmId) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance(algorithmId, "BC");
            return messageDigest.digest(content);
        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            return null;
        }
    }
    
    public static String createMessageIntegrityCheck(HttpEntityEnclosingRequest request) throws HttpException {
        
        String contentTypeString = HttpMessageUtils.getHeaderValue(request, AS2Header.CONTENT_TYPE);
        if(contentTypeString == null) {
            throw new HttpException("content type missing from request");
        }
        ContentType contentType = ContentType.parse(contentTypeString);

        String dispositionNotificationOptionsString =  HttpMessageUtils.getHeaderValue(request, AS2Header.DISPOSITION_NOTIFICATION_OPTIONS);
        if (dispositionNotificationOptionsString == null) {
            throw new HttpException("disposition notification options missing from request ");
        }
        DispositionNotificationOptions dispositionNotificationOptions = DispositionNotificationOptionsParser.parseDispositionNotificationOptions(dispositionNotificationOptionsString, null);
        String micAlgorithm = getMicJdkAlgorithmName(dispositionNotificationOptions.getMicAlgorithms());
        if (micAlgorithm == null) {
            throw new HttpException("no matching MIC algorithms found");
        }

        
        HttpEntity entity = null;
        switch(contentType.getMimeType().toLowerCase()) {
        case AS2MimeType.APPLICATION_EDIFACT:
        case AS2MimeType.APPLICATION_EDI_X12:
        case AS2MimeType.APPLICATION_EDI_CONSENT: {
            EntityParser.parseAS2MessageEntity(request);
            entity = HttpMessageUtils.getEntity(request, ApplicationEDIEntity.class);
            break;
        }
        case AS2MimeType.MULTIPART_SIGNED: {
            EntityParser.parseAS2MessageEntity(request);
            MultipartSignedEntity multipartSignedEntity = HttpMessageUtils.getEntity(request, MultipartSignedEntity.class);
            entity = multipartSignedEntity.getSignedDataEntity();
            break;
        }
         default:
             throw new HttpException("invalid content type '" + contentType.getMimeType() + "' for message integrity check");
        }
        
        byte[] content = EntityUtils.getContent(entity);
        
        byte[] mic = createMic(content, micAlgorithm);
        try {
            mic = EntityUtils.encode(mic, "base64");
            return new String(mic, AS2CharSet.US_ASCII);
        } catch (Exception e) {
            throw new HttpException("failed to encode MIC", e);
        }
    }
    
    private static String getMicJdkAlgorithmName(String[] micAs2AlgorithmNames) {
        for(String micAs2AlgorithmName : micAs2AlgorithmNames) {
            String micAlgorithmName = AS2MicAlgorithm.getJdkAlgorithmName(micAs2AlgorithmName);
            if (micAlgorithmName != null) {
                return micAlgorithmName;
            }
        }    
        return null;
    }
    
}
