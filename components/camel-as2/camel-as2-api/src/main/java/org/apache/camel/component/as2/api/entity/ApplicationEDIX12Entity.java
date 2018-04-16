package org.apache.camel.component.as2.api.entity;

import org.apache.camel.component.as2.api.AS2MediaType;
import org.apache.http.entity.ContentType;

public class ApplicationEDIX12Entity extends ApplicationEDIEntity {

    public ApplicationEDIX12Entity(String content, String charset, String contentTransferEncoding,
            boolean isMainBody) {
        super(content, ContentType.create(AS2MediaType.APPLICATION_EDI_X12, charset), contentTransferEncoding, isMainBody);
    }

}
