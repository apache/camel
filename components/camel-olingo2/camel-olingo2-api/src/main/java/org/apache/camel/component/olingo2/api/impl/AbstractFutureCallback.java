/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.olingo2.api.impl;

import java.io.Closeable;
import java.io.IOException;
import java.util.regex.Pattern;

import org.apache.camel.component.olingo2.api.Olingo2ResponseHandler;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.entity.ContentType;
import org.apache.olingo.odata2.api.commons.HttpStatusCodes;
import org.apache.olingo.odata2.api.ep.EntityProvider;
import org.apache.olingo.odata2.api.ep.EntityProviderException;
import org.apache.olingo.odata2.api.exception.ODataApplicationException;
import org.apache.olingo.odata2.api.exception.ODataException;
import org.apache.olingo.odata2.api.processor.ODataErrorContext;

import static org.apache.camel.component.olingo2.api.impl.Olingo2Helper.getContentTypeHeader;

/**
 * Helper implementation of {@link org.apache.http.concurrent.FutureCallback}
 * for {@link org.apache.camel.component.olingo2.api.impl.Olingo2AppImpl}
 */
public abstract class AbstractFutureCallback<T> implements FutureCallback<HttpResponse> {

    public static final Pattern ODATA_MIME_TYPE = Pattern.compile("application/((atom)|(json)|(xml)).*");
    private final Olingo2ResponseHandler<T> responseHandler;

    AbstractFutureCallback(Olingo2ResponseHandler<T> responseHandler) {
        this.responseHandler = responseHandler;
    }

    public static HttpStatusCodes checkStatus(HttpResponse response) throws ODataApplicationException {
        final StatusLine statusLine = response.getStatusLine();
        HttpStatusCodes httpStatusCode = HttpStatusCodes.fromStatusCode(statusLine.getStatusCode());
        if (400 <= httpStatusCode.getStatusCode() && httpStatusCode.getStatusCode() <= 599) {
            if (response.getEntity() != null) {
                try {
                    final ContentType responseContentType = getContentTypeHeader(response);

                    if (responseContentType != null && ODATA_MIME_TYPE.matcher(responseContentType.getMimeType()).matches()) {
                        final ODataErrorContext errorContext = EntityProvider.readErrorDocument(response.getEntity().getContent(), responseContentType.toString());
                        throw new ODataApplicationException(errorContext.getMessage(), errorContext.getLocale(), httpStatusCode, errorContext.getErrorCode(),
                                                            errorContext.getException());
                    }
                } catch (EntityProviderException e) {
                    throw new ODataApplicationException(e.getMessage(), response.getLocale(), httpStatusCode, e);
                } catch (IOException e) {
                    throw new ODataApplicationException(e.getMessage(), response.getLocale(), httpStatusCode, e);
                }
            }

            throw new ODataApplicationException(statusLine.getReasonPhrase(), response.getLocale(), httpStatusCode);
        }

        return httpStatusCode;
    }

    @Override
    public final void completed(HttpResponse result) {
        try {
            // check response status
            checkStatus(result);

            onCompleted(result);
        } catch (Exception e) {
            failed(e);
        } finally {
            if (result instanceof Closeable) {
                try {
                    ((Closeable)result).close();
                } catch (final IOException ignore) {
                }
            }
        }
    }

    protected abstract void onCompleted(HttpResponse result) throws ODataException, IOException;

    @Override
    public final void failed(Exception ex) {
        responseHandler.onException(ex);
    }

    @Override
    public final void cancelled() {
        responseHandler.onCanceled();
    }
}
