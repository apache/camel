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
package org.apache.camel.component.dropbox.core;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.dropbox.core.DbxDownloader;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.DbxUserFilesRequests;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.Metadata;
import com.dropbox.core.v2.files.MetadataV2;
import com.dropbox.core.v2.files.SearchMatchV2;
import com.dropbox.core.v2.files.SearchOptions;
import com.dropbox.core.v2.files.SearchV2Builder;
import com.dropbox.core.v2.files.SearchV2Result;
import org.apache.camel.CamelContext;
import org.apache.camel.component.dropbox.dto.DropboxFileDownloadResult;
import org.apache.camel.component.dropbox.dto.DropboxSearchResult;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The Dropbox client is a mock that answers a search and a folder listing in pages, as the service does: a page, the
 * {@code has_more} flag and a cursor for the next page.
 */
class DropboxAPIFacadePagingTest {

    private static final int MAX_REQUESTS = 10;

    private CamelContext context;
    private DbxClientV2 client;
    private DbxUserFilesRequests files;
    private SearchV2Builder searchBuilder;
    private final List<String> cursors = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        context = new DefaultCamelContext();
        client = mock(DbxClientV2.class);
        files = mock(DbxUserFilesRequests.class);
        searchBuilder = mock(SearchV2Builder.class);
        when(client.files()).thenReturn(files);
        when(files.searchV2Builder("report")).thenReturn(searchBuilder);
        when(searchBuilder.withOptions(any(SearchOptions.class))).thenReturn(searchBuilder);
        when(files.download(anyString())).thenAnswer(invocation -> {
            String path = invocation.getArgument(0);
            return new DbxDownloader<>(file(path), new ByteArrayInputStream(path.getBytes(StandardCharsets.UTF_8)));
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        context.close();
    }

    private static FileMetadata file(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        return FileMetadata.newBuilder(name, "id:" + name, new Date(0), new Date(0), "0123456789abcdef", 1)
                .withPathDisplay(path).withPathLower(path).build();
    }

    private static SearchV2Result searchPage(boolean hasMore, String cursor, String... paths) {
        List<SearchMatchV2> matches = new ArrayList<>();
        for (String path : paths) {
            matches.add(new SearchMatchV2(MetadataV2.metadata(file(path))));
        }
        return new SearchV2Result(matches, hasMore, cursor);
    }

    private static ListFolderResult folderPage(boolean hasMore, String cursor, String... paths) {
        List<Metadata> entries = new ArrayList<>();
        for (String path : paths) {
            entries.add(file(path));
        }
        return new ListFolderResult(entries, cursor, hasMore);
    }

    /** Records the cursor of a continue request and fails the test when the facade does not stop asking. */
    private void request(String cursor) {
        cursors.add(cursor);
        if (cursors.size() > MAX_REQUESTS) {
            throw new IllegalStateException("The facade sent more than " + MAX_REQUESTS + " continue requests");
        }
    }

    private static List<String> paths(DropboxSearchResult result) {
        List<String> answer = new ArrayList<>();
        for (SearchMatchV2 match : result.getFound()) {
            answer.add(match.getMetadata().getMetadataValue().getPathDisplay());
        }
        return answer;
    }

    @Test
    void searchReturnsTheMatchesOfEveryPage() throws Exception {
        when(searchBuilder.start()).thenReturn(searchPage(true, "cursor-1", "/docs/report-1.txt", "/docs/report-2.txt"));
        when(files.searchContinueV2(anyString())).thenAnswer(invocation -> {
            String cursor = invocation.getArgument(0);
            request(cursor);
            return "cursor-1".equals(cursor)
                    ? searchPage(true, "cursor-2", "/docs/report-3.txt", "/docs/report-4.txt")
                    : searchPage(false, null, "/docs/report-5.txt");
        });

        DropboxSearchResult result = new DropboxAPIFacade(client, new DefaultExchange(context)).search("/docs", "report");

        assertEquals(List.of("/docs/report-1.txt", "/docs/report-2.txt", "/docs/report-3.txt", "/docs/report-4.txt",
                "/docs/report-5.txt"), paths(result), "the matches of the three pages, each once and in order");
        assertEquals(List.of("cursor-1", "cursor-2"), cursors, "each page is requested with the cursor of the page before");
    }

    @Test
    void getDownloadsTheFilesOfEveryPageOfTheFolder() throws Exception {
        when(files.listFolder("/docs")).thenReturn(folderPage(true, "cursor-1", "/docs/a.txt", "/docs/b.txt"));
        when(files.listFolderContinue(anyString())).thenAnswer(invocation -> {
            String cursor = invocation.getArgument(0);
            request(cursor);
            return "cursor-1".equals(cursor)
                    ? folderPage(true, "cursor-2", "/docs/c.txt")
                    : folderPage(false, "cursor-3", "/docs/d.txt");
        });

        DropboxFileDownloadResult result = new DropboxAPIFacade(client, new DefaultExchange(context)).get("/docs");

        assertEquals(List.of("/docs/a.txt", "/docs/b.txt", "/docs/c.txt", "/docs/d.txt"),
                new ArrayList<>(result.getEntries().keySet()), "the files of the three pages, each once and in order");
        assertEquals(List.of("cursor-1", "cursor-2"), cursors, "each page is requested with the cursor of the page before");
    }

    @Test
    void resultThatFitsInOnePageNeedsNoFurtherRequest() throws Exception {
        // the service returns a cursor with the last page of a folder as well; only has_more says that there is more
        when(searchBuilder.start()).thenReturn(searchPage(false, null, "/docs/report-1.txt"));
        when(files.listFolder("/docs")).thenReturn(folderPage(false, "cursor-1", "/docs/a.txt"));
        DropboxAPIFacade facade = new DropboxAPIFacade(client, new DefaultExchange(context));

        assertEquals(List.of("/docs/report-1.txt"), paths(facade.search("/docs", "report")));
        assertEquals(List.of("/docs/a.txt"), new ArrayList<>(facade.get("/docs").getEntries().keySet()));

        verify(files, never()).searchContinueV2(anyString());
        verify(files, never()).listFolderContinue(anyString());
    }
}
