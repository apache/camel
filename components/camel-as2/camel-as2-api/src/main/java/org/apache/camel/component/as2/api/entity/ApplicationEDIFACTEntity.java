package org.apache.camel.component.as2.api.entity;

import org.apache.camel.component.as2.api.AS2MediaType;
import org.apache.http.entity.ContentType;

public class ApplicationEDIFACTEntity extends ApplicationEDIEntity {

    public ApplicationEDIFACTEntity(String content, String charset, String contentTransferEncoding,
            boolean isMainBody) {
        super(content, ContentType.create(AS2MediaType.APPLICATION_EDIFACT, charset), contentTransferEncoding, isMainBody);
    }
    
}
