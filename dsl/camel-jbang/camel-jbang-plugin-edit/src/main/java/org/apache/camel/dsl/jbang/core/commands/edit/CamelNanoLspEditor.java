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

package org.apache.camel.dsl.jbang.core.commands.edit;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import com.github.cameltooling.lsp.internal.CamelLanguageServer;
import com.github.cameltooling.lsp.internal.CamelTextDocumentService;
import com.github.cameltooling.lsp.internal.diagnostic.CamelKModelineDiagnosticService;
import com.github.cameltooling.lsp.internal.diagnostic.ConfigurationPropertiesDiagnosticService;
import com.github.cameltooling.lsp.internal.diagnostic.ConnectedModeDiagnosticService;
import com.github.cameltooling.lsp.internal.diagnostic.EndpointDiagnosticService;
import com.github.cameltooling.lsp.internal.settings.SettingsManager;
import org.apache.camel.catalog.ConfigurationPropertiesValidationResult;
import org.apache.camel.catalog.EndpointValidationResult;
import org.apache.camel.parser.model.CamelEndpointDetails;
import org.apache.camel.tooling.util.Strings;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemTag;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.InsertReplaceEdit;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jline.builtins.ConfigurationPath;
import org.jline.builtins.Nano;
import org.jline.builtins.Options;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;

public class CamelNanoLspEditor extends Nano {
    private final LocalCamelDocumentService camelDocumentService = new LocalCamelDocumentService();

    public CamelNanoLspEditor(Terminal terminal, Path root, Options opts) {
        this(terminal, root, opts, null);
    }

    public CamelNanoLspEditor(Terminal terminal, Path root, Options opts, ConfigurationPath configPath) {
        super(terminal, root, opts, configPath);
        mouseSupport = true;
    }

    @Override
    protected List<Diagnostic> computeDiagnostic() {
        String fileName = buffer.getFile();
        String text = String.join("\n", this.buffer.getLines()) + "\n";
        TextDocumentItem textDocumentItem = new TextDocumentItem(fileName, CamelLanguageServer.LANGUAGE_ID, 0, text);
        return camelDocumentService.computeDiagnostic(textDocumentItem);
    }

    @Override
    protected LinkedHashMap<AttributedString, List<AttributedString>> computeSuggestions() {
        LinkedHashMap<AttributedString, List<AttributedString>> suggestions = new LinkedHashMap<>();
        String fileName = buffer.getFile();
        StringBuilder text = new StringBuilder();
        for (String line : this.buffer.getLines()) {
            text.append(line);
            text.append('\n');
        }
        TextDocumentItem textDocumentItem =
                new TextDocumentItem(fileName, CamelLanguageServer.LANGUAGE_ID, 0, text.toString());
        CompletableFuture<Either<List<CompletionItem>, CompletionList>> eitherCompletableFuture =
                this.camelDocumentService.completionLocal(
                        textDocumentItem,
                        new Position(buffer.getLine(), buffer.getOffsetInLine() + buffer.getColumn()));
        if (eitherCompletableFuture.isCompletedExceptionally()) {
            return suggestions;
        }
        try {
            List<CompletionItem> left = eitherCompletableFuture.get().getLeft();
            for (CompletionItem item : left) {
                List<AttributedString> docs = new ArrayList<>();
                docs.add(new AttributedString(item.getLabel(), AttributedStyle.BOLD));
                docs.add(new AttributedString(""));
                String type = item.getDetail();
                if (!Strings.isEmpty(type)) {
                    docs.add(new AttributedString("Type: " + type));
                }
                List<CompletionItemTag> tags = item.getTags();
                if (tags != null && tags.contains(CompletionItemTag.Deprecated)) {
                    docs.add(new AttributedString("Deprecated: true"));
                }
                Either<String, MarkupContent> docMap = item.getDocumentation();
                if (docMap != null) {
                    String doc = docMap.getLeft();
                    if (!Strings.isEmpty(doc)) {
                        doc.lines().forEach(s -> docs.add(new AttributedString(s)));
                    }
                }
                suggestions.put(new AttributedString(item.getLabel()), docs);
            }
        } catch (Exception e) {
            // ignore
            // TODO: show exception message in help
        }
        return suggestions;
    }

    @Override
    protected void insertHelp(int selected) {
        String fileName = buffer.getFile();
        StringBuilder text = new StringBuilder();
        for (String line : this.buffer.getLines()) {
            text.append(line);
            text.append('\n');
        }
        // TODO store completions so we don't recompute them when inserting
        TextDocumentItem textDocumentItem =
                new TextDocumentItem(fileName, CamelLanguageServer.LANGUAGE_ID, 0, text.toString());
        CompletableFuture<Either<List<CompletionItem>, CompletionList>> eitherCompletableFuture =
                this.camelDocumentService.completionLocal(
                        textDocumentItem,
                        new Position(buffer.getLine(), buffer.getOffsetInLine() + buffer.getColumn()));
        List<CompletionItem> lines;
        try {
            lines = eitherCompletableFuture.get().getLeft();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
        CompletionItem item = lines.get(selected);
        Either<TextEdit, InsertReplaceEdit> textEdit = item.getTextEdit();
        if (textEdit != null) {
            TextEdit left = textEdit.getLeft();
            Range range = left.getRange();
            Position start = range.getStart();
            int endLine = start.getLine();
            Position end = range.getEnd();
            int startLine = end.getLine();
            if (startLine == endLine) {
                StringBuilder newLine = new StringBuilder();
                String line = this.buffer.getLines().get(startLine);
                newLine.append(line, 0, start.getCharacter());
                newLine.append(left.getNewText());
                newLine.append(line.substring(end.getCharacter()));
                this.buffer.getLines().set(startLine, newLine.toString());
                this.buffer.moveRight(left.getNewText().length());
            } else {
                // first line
                StringBuilder newFirstLine = new StringBuilder();
                String oldFirstLine = this.buffer.getLines().get(startLine);
                String newText = left.getNewText();
                newFirstLine.append(oldFirstLine, 0, start.getCharacter());
                newFirstLine.append(newText, 0, oldFirstLine.length() - start.getCharacter());
                this.buffer.getLines().set(startLine, newFirstLine.toString());
                // second line
                StringBuilder newSecondLine = new StringBuilder();
                String oldSecondLine = this.buffer.getLines().get(endLine);
                newSecondLine.append(newText, oldFirstLine.length() - start.getCharacter(), end.getCharacter());
                newSecondLine.append(oldSecondLine, end.getCharacter(), end.getCharacter());
                this.buffer.getLines().set(endLine, newSecondLine.toString());
                this.buffer.moveRight(left.getNewText().length());
            }
        } else if (!Strings.isEmpty(item.getInsertText()) || !Strings.isEmpty(item.getLabel())) {
            String insert = Strings.isEmpty(item.getInsertText()) ? item.getLabel() : item.getInsertText();
            StringBuilder newLine = new StringBuilder();
            String line = this.buffer.getLines().get(buffer.getLine());
            newLine.append(line, 0, buffer.getOffsetInLine() + buffer.getColumn());
            newLine.append(insert);
            this.buffer.getLines().set(buffer.getLine(), newLine.toString());
            this.buffer.moveRight(insert.length());
        }
        this.buffer.setDirty(true);
    }

    private static class LocalCamelDocumentService extends CamelTextDocumentService {
        private final EndpointDiagnosticService endpointDiagnosticService;
        private final ConfigurationPropertiesDiagnosticService configurationPropertiesDiagnosticService;
        private final CamelKModelineDiagnosticService camelKModelineDiagnosticService;
        private final ConnectedModeDiagnosticService connectedModeDiagnosticService;

        private LocalCamelDocumentService() {
            super(null);
            endpointDiagnosticService = new EndpointDiagnosticService(getCamelCatalog());
            configurationPropertiesDiagnosticService = new ConfigurationPropertiesDiagnosticService(getCamelCatalog());
            camelKModelineDiagnosticService = new CamelKModelineDiagnosticService();
            connectedModeDiagnosticService = new ConnectedModeDiagnosticService();
        }

        private CompletableFuture<Either<List<CompletionItem>, CompletionList>> completionLocal(
                TextDocumentItem textDocument, Position position) {
            String uri = textDocument.getUri();
            openedDocuments.put(uri, textDocument);
            return super.completion(new CompletionParams(new TextDocumentIdentifier(uri), position));
        }

        @Override
        public SettingsManager getSettingsManager() {
            return new SettingsManager(this);
        }

        private List<Diagnostic> computeDiagnostic(TextDocumentItem documentItem) {
            String uri = documentItem.getUri();
            String camelText = documentItem.getText();
            Map<CamelEndpointDetails, EndpointValidationResult> endpointErrors =
                    endpointDiagnosticService.computeCamelEndpointErrors(camelText, uri);
            List<org.eclipse.lsp4j.Diagnostic> diagnostics =
                    endpointDiagnosticService.converToLSPDiagnostics(camelText, endpointErrors, documentItem);
            Map<String, ConfigurationPropertiesValidationResult> configurationPropertiesErrors =
                    configurationPropertiesDiagnosticService.computeCamelConfigurationPropertiesErrors(camelText, uri);
            diagnostics.addAll(
                    configurationPropertiesDiagnosticService.converToLSPDiagnostics(configurationPropertiesErrors));
            diagnostics.addAll(camelKModelineDiagnosticService.compute(camelText, documentItem));
            diagnostics.addAll(connectedModeDiagnosticService.compute(camelText, documentItem));
            diagnostics.addAll(connectedModeDiagnosticService.compute(camelText, documentItem));
            return diagnostics.stream()
                    .map(diag -> (Diagnostic) new DiagnosticWrapper(diag))
                    .toList();
        }

        private static class DiagnosticWrapper implements Diagnostic {
            private final org.eclipse.lsp4j.Diagnostic diag;

            public DiagnosticWrapper(org.eclipse.lsp4j.Diagnostic diag) {
                this.diag = diag;
            }

            @Override
            public int getStartLine() {
                return diag.getRange().getStart().getLine();
            }

            @Override
            public int getStartColumn() {
                return diag.getRange().getStart().getCharacter();
            }

            @Override
            public int getEndLine() {
                return diag.getRange().getEnd().getLine();
            }

            @Override
            public int getEndColumn() {
                return diag.getRange().getEnd().getCharacter();
            }

            @Override
            public String getMessage() {
                return diag.getMessage();
            }
        }
    }
}
