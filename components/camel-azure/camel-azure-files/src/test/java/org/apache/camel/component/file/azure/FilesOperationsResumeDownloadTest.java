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
package org.apache.camel.component.file.azure;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.azure.core.http.HttpClient;
import com.azure.core.http.HttpHeaderName;
import com.azure.core.http.HttpHeaders;
import com.azure.core.http.HttpRequest;
import com.azure.core.http.HttpResponse;
import com.azure.storage.common.policy.RequestRetryOptions;
import com.azure.storage.file.share.ShareDirectoryClient;
import com.azure.storage.file.share.ShareServiceClient;
import com.azure.storage.file.share.ShareServiceClientBuilder;
import com.azure.storage.file.share.models.ShareFileItem;
import org.apache.camel.Exchange;
import org.apache.camel.component.file.FileComponent;
import org.apache.camel.component.file.remote.RemoteFile;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * resumeDownload=true continues an interrupted download with a ranged GET, which the file service answers with 206
 * Partial Content. The fake service below answers like Azure Files: 200 without a range, 206 with a range that starts
 * inside the file and 416 with a range that starts at or after its end.
 */
public class FilesOperationsResumeDownloadTest extends CamelTestSupport {

    private static final byte[] CONTENT = "0123456789".getBytes(StandardCharsets.US_ASCII);

    @TempDir
    Path workDir;

    @Test
    void resumedDownloadIsCompletedAndRenamed() throws Exception {
        FakeFileService service = new FakeFileService();
        FilesOperations operations = operations(service);
        // a previous download of the file was interrupted after 4 bytes
        Files.write(workDir.resolve("hello.txt.inprogress"), Arrays.copyOf(CONTENT, 4));

        boolean retrieved = operations.retrieveFile("hello.txt", exchange(), CONTENT.length);

        assertEquals(List.of("bytes=4-"), service.ranges, "the download is resumed with a range");
        assertTrue(retrieved, "a resumed download that returned the rest of the file must be retrieved");
        assertEquals("0123456789", Files.readString(workDir.resolve("hello.txt"), StandardCharsets.US_ASCII));
        assertFalse(Files.exists(workDir.resolve("hello.txt.inprogress")));
    }

    @Test
    void downloadWithoutPartialFileIsRetrieved() throws Exception {
        FakeFileService service = new FakeFileService();
        FilesOperations operations = operations(service);

        boolean retrieved = operations.retrieveFile("hello.txt", exchange(), CONTENT.length);

        assertEquals(1, service.ranges.size());
        assertEquals("none", service.ranges.get(0));
        assertTrue(retrieved);
        assertEquals("0123456789", Files.readString(workDir.resolve("hello.txt"), StandardCharsets.US_ASCII));
    }

    private Exchange exchange() {
        RemoteFile<ShareFileItem> file = new RemoteFile<>();
        file.setAbsoluteFilePath("/hello.txt");
        file.setRelativeFilePath("hello.txt");
        file.setFileName("hello.txt");
        file.setFileNameOnly("hello.txt");
        Exchange exchange = new DefaultExchange(context);
        exchange.setProperty(FileComponent.FILE_EXCHANGE_FILE, file);
        return exchange;
    }

    private FilesOperations operations(FakeFileService service) throws Exception {
        FilesEndpoint endpoint = context.getEndpoint(
                "azure-files://account.file.core.windows.net/share?credentialType=AZURE_SAS&sv=2022-11-02&sig=x"
                                                     + "&resumeDownload=true&localWorkDirectory=" + workDir,
                FilesEndpoint.class);
        FilesOperations operations = new FilesOperations(endpoint);
        ShareServiceClient client = new ShareServiceClientBuilder()
                .endpoint("https://account.file.core.windows.net")
                .sasToken("sv=2022-11-02&sig=x")
                .httpClient(service)
                .retryOptions(new RequestRetryOptions(null, 1, (Integer) null, null, null, null))
                .buildClient();
        ShareDirectoryClient root = client.getShareClient("share").getRootDirectoryClient();
        ArrayDeque<ShareDirectoryClient> dirStack = new ArrayDeque<>();
        dirStack.push(root);
        setField(operations, "client", client);
        setField(operations, "root", root);
        setField(operations, "dirStack", dirStack);
        return operations;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = FilesOperations.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    /**
     * Answers Get File like Azure Files for the file /share/hello.txt.
     */
    private static final class FakeFileService implements HttpClient {
        final List<String> ranges = new CopyOnWriteArrayList<>();

        @Override
        public Mono<HttpResponse> send(HttpRequest request) {
            if (!"GET".equals(request.getHttpMethod().name()) || !request.getUrl().getPath().equals("/share/hello.txt")) {
                return Mono.just(new FakeResponse(request, 404, new HttpHeaders(), new byte[0]));
            }
            String range = request.getHeaders().getValue(HttpHeaderName.fromString("x-ms-range"));
            if (range == null) {
                range = request.getHeaders().getValue(HttpHeaderName.RANGE);
            }
            ranges.add(range == null ? "none" : range);
            HttpHeaders headers = new HttpHeaders()
                    .set(HttpHeaderName.ETAG, "\"0x1\"")
                    .set(HttpHeaderName.LAST_MODIFIED, "Sun, 18 Oct 2026 10:00:00 GMT")
                    .set(HttpHeaderName.CONTENT_TYPE, "application/octet-stream")
                    .set(HttpHeaderName.fromString("x-ms-type"), "File")
                    .set(HttpHeaderName.fromString("x-ms-version"), "2025-05-05")
                    .set(HttpHeaderName.ACCEPT_RANGES, "bytes");
            if (range == null) {
                headers.set(HttpHeaderName.CONTENT_LENGTH, String.valueOf(CONTENT.length));
                return Mono.just(new FakeResponse(request, 200, headers, CONTENT));
            }
            long start = Long.parseLong(range.substring("bytes=".length(), range.indexOf('-')));
            if (start >= CONTENT.length) {
                byte[] error = ("<?xml version=\"1.0\" encoding=\"utf-8\"?><Error><Code>InvalidRange</Code>"
                                + "<Message>The range specified is invalid for the current size of the resource.</Message>"
                                + "</Error>")
                        .getBytes(StandardCharsets.UTF_8);
                HttpHeaders errorHeaders = new HttpHeaders()
                        .set(HttpHeaderName.fromString("x-ms-error-code"), "InvalidRange")
                        .set(HttpHeaderName.CONTENT_TYPE, "application/xml")
                        .set(HttpHeaderName.CONTENT_RANGE, "bytes */" + CONTENT.length)
                        .set(HttpHeaderName.CONTENT_LENGTH, String.valueOf(error.length));
                return Mono.just(new FakeResponse(request, 416, errorHeaders, error));
            }
            byte[] rest = Arrays.copyOfRange(CONTENT, (int) start, CONTENT.length);
            headers.set(HttpHeaderName.CONTENT_LENGTH, String.valueOf(rest.length));
            headers.set(HttpHeaderName.CONTENT_RANGE,
                    "bytes " + start + "-" + (CONTENT.length - 1) + "/" + CONTENT.length);
            return Mono.just(new FakeResponse(request, 206, headers, rest));
        }
    }

    private static final class FakeResponse extends HttpResponse {
        private final int status;
        private final HttpHeaders headers;
        private final byte[] body;

        FakeResponse(HttpRequest request, int status, HttpHeaders headers, byte[] body) {
            super(request);
            this.status = status;
            this.headers = headers;
            this.body = body;
        }

        @Override
        public int getStatusCode() {
            return status;
        }

        @Override
        @Deprecated
        public String getHeaderValue(String name) {
            return headers.getValue(HttpHeaderName.fromString(name));
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }

        @Override
        public Flux<ByteBuffer> getBody() {
            return Flux.just(ByteBuffer.wrap(body));
        }

        @Override
        public Mono<byte[]> getBodyAsByteArray() {
            return Mono.just(body);
        }

        @Override
        public Mono<String> getBodyAsString() {
            return Mono.just(new String(body, StandardCharsets.UTF_8));
        }

        @Override
        public Mono<String> getBodyAsString(Charset charset) {
            return Mono.just(new String(body, charset));
        }
    }
}
