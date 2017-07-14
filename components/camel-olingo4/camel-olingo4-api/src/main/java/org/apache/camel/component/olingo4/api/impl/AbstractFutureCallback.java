/**
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
package org.apache.camel.component.olingo4.api.impl;

import java.io.Closeable;
import java.io.IOException;
import java.io.StringWriter;
import java.util.regex.Pattern;

import org.apache.camel.component.olingo4.api.Olingo4ResponseHandler;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.concurrent.FutureCallback;
import org.apache.olingo.client.api.ODataClient;
import org.apache.olingo.client.api.communication.ODataClientErrorException;
import org.apache.olingo.client.api.serialization.ODataReader;
import org.apache.olingo.client.core.ODataClientFactory;
import org.apache.olingo.client.core.serialization.ODataReaderImpl;
import org.apache.olingo.commons.api.ex.ODataError;
import org.apache.olingo.commons.api.ex.ODataException;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.http.HttpStatusCode;

import static org.apache.camel.component.olingo4.api.impl.Olingo4Helper.getContentTypeHeader;

/**
* Helper implementation of {@link org.apache.http.concurrent.FutureCallback}
 * for {@link org.apache.camel.component.olingo4.api.impl.Olingo4AppImpl}
*/
public abstract class AbstractFutureCallback<T> implements FutureCallback<HttpResponse> {

    public static final Pattern ODATA_MIME_TYPE_PATTERN = Pattern.compile("application/((atom)|(json)|(xml)).*");
    public static final int NETWORK_CONNECT_TIMEOUT_ERROR = 599;
    
    private final Olingo4ResponseHandler<T> responseHandler;

    AbstractFutureCallback(Olingo4ResponseHandler<T> responseHandler) {
        this.responseHandler = responseHandler;
    }

    public static HttpStatusCode checkStatus(HttpResponse response) throws ODataException, ODataClientErrorException {
        final StatusLine statusLine = response.getStatusLine();
        HttpStatusCode httpStatusCode = HttpStatusCode.fromStatusCode(statusLine.getStatusCode());
        if (HttpStatusCode.BAD_REQUEST.getStatusCode() <= httpStatusCode.getStatusCode() && httpStatusCode.getStatusCode() <= NETWORK_CONNECT_TIMEOUT_ERROR) {
            if (response.getEntity() != null) {
                try {
                    final ContentType responseContentType = getContentTypeHeader(response);
                              
                    if (ODATA_MIME_TYPE_PATTERN.matcher(responseContentType.toContentTypeString()).matches()) {
                        final ODataReader reader = ODataClientFactory.getClient().getReader();
                        final ODataError error = reader.readError(response.getEntity().getContent(), responseContentType);
                        
                        throw new ODataClientErrorException(statusLine, error);
                    }
                } catch (IOException e) {
                    throw new ODataException(e.getMessage(), e);
                }
            }

            throw new ODataException(statusLine.getReasonPhrase());
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
                    ((Closeable) result).close();
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
