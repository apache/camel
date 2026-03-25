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
package org.apache.camel.dsl.jbang.core.commands;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.transform.DataWeaveConverter;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "dataweave",
         description = "Convert DataWeave scripts to DataSonnet format",
         sortOptions = false, showDefaultValues = true)
public class TransformDataWeave extends CamelCommand {

    @CommandLine.Option(names = { "--input", "-i" },
                        description = "Input .dwl file or directory containing .dwl files")
    private String input;

    @CommandLine.Option(names = { "--output", "-o" },
                        description = "Output .ds file or directory (defaults to stdout)")
    private String output;

    @CommandLine.Option(names = { "--expression", "-e" },
                        description = "Inline DataWeave expression to convert")
    private String expression;

    @CommandLine.Option(names = { "--include-comments" }, defaultValue = "true",
                        description = "Include conversion notes as comments in output")
    private boolean includeComments = true;

    public TransformDataWeave(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        if (expression != null) {
            return convertExpression();
        }
        if (input != null) {
            return convertFiles();
        }

        printer().println("Error: either --input or --expression must be specified");
        return 1;
    }

    private int convertExpression() {
        DataWeaveConverter converter = new DataWeaveConverter();
        converter.setIncludeComments(includeComments);

        String result;
        if (expression.contains("%dw") || expression.contains("---")) {
            result = converter.convert(expression);
        } else {
            result = converter.convertExpression(expression);
        }

        printer().println(result);
        printSummary(converter, 1);
        return 0;
    }

    private int convertFiles() throws IOException {
        Path inputPath = Path.of(input);
        if (!Files.exists(inputPath)) {
            printer().println("Error: input path does not exist: " + input);
            return 1;
        }

        List<Path> dwlFiles = new ArrayList<>();
        if (Files.isDirectory(inputPath)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(inputPath, "*.dwl")) {
                for (Path entry : stream) {
                    dwlFiles.add(entry);
                }
            }
            if (dwlFiles.isEmpty()) {
                printer().println("No .dwl files found in: " + input);
                return 1;
            }
        } else {
            dwlFiles.add(inputPath);
        }

        int totalTodos = 0;
        int totalConverted = 0;

        for (Path dwlFile : dwlFiles) {
            DataWeaveConverter converter = new DataWeaveConverter();
            converter.setIncludeComments(includeComments);

            String dwContent = Files.readString(dwlFile);
            String dsContent = converter.convert(dwContent);

            totalTodos += converter.getTodoCount();
            totalConverted += converter.getConvertedCount();

            if (output != null) {
                Path outputPath = resolveOutputPath(dwlFile, Path.of(output));
                Files.createDirectories(outputPath.getParent());
                Files.writeString(outputPath, dsContent);
                printer().println("Converted: " + dwlFile + " -> " + outputPath);
            } else {
                if (dwlFiles.size() > 1) {
                    printer().println("// === " + dwlFile.getFileName() + " ===");
                }
                printer().println(dsContent);
            }
        }

        printSummary(totalConverted, totalTodos, dwlFiles.size());
        return 0;
    }

    private Path resolveOutputPath(Path dwlFile, Path outputPath) {
        String dsFileName = dwlFile.getFileName().toString().replaceFirst("\\.dwl$", ".ds");
        if (Files.isDirectory(outputPath) || output.endsWith("/")) {
            return outputPath.resolve(dsFileName);
        }
        // Single file output
        return outputPath;
    }

    private void printSummary(DataWeaveConverter converter, int fileCount) {
        printSummary(converter.getConvertedCount(), converter.getTodoCount(), fileCount);
    }

    private void printSummary(int converted, int todos, int fileCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        if (fileCount > 1) {
            sb.append("Files: ").append(fileCount).append(", ");
        }
        sb.append("Converted: ").append(converted).append(" expressions");
        if (todos > 0) {
            sb.append(", ").append(todos).append(" require manual review");
        }
        printer().printErr(sb.toString());
    }
}
