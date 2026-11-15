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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.networknt.schema.Error;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.catalog.LanguageValidationResult;
import org.apache.camel.dsl.yaml.validator.ProjectFacts;
import org.apache.camel.dsl.yaml.validator.YamlValidator;
import org.apache.camel.tooling.model.LanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The checks of what an AI model says, as opposed to what it writes: a file goes through the validator before it is
 * written, an answer shown in a chat does not, and it is what the user copies. The simple expressions of an answer are
 * checked against the catalog, the way {@code camel_validate_source} checks a route: the {@code ${...}} placeholders of
 * the text and of any code block that is not YAML, and the {@code simple:} values and log messages of the YAML blocks.
 * <p>
 * A placeholder is only checked when it starts with a simple function or value (body, header, exchangeProperty, random,
 * date, ...), so a Maven {@code ${camel-version}} or a shell variable in the same answer is left alone. What the
 * catalog cannot judge is skipped as in {@link SimpleChecks}: a function of a language that is not on the classpath, a
 * property placeholder used as a logical operand.
 * <p>
 * The code blocks of an answer are checked for what does not exist (CAMEL-26960): the keys and options of YAML routes
 * and of fragments of them ({@link YamlFragment}), the options of their endpoint uris, and the {@code camel.*} keys of
 * properties lines. A model told to look options up and to validate its snippets wrote more of them and validated none,
 * so the check does not wait to be asked. Its mistakes are marked under their block, not sent back for a correction.
 */
public final class AnswerChecks {

    /** A simple expression of an answer that the catalog rejects: what was written, and why. */
    public record Problem(String expression, String error) {

        /** {@code ${header.user ?: Guest}: Other operator ?: does not accept Guest on the right hand side: ...}. */
        public String message() {
            return expression + ": " + error;
        }
    }

    /**
     * A mistake in the code of an answer: a key, an option or a {@code camel.*} property that does not exist, which a
     * user who copies the code takes into a route. block is the number of the fenced code block in the answer, 1 for
     * the first; line is the line in that block, 1 for the line after the fence, or 0 when it is not known.
     */
    public record Mark(int block, int line, String message) {

        /** {@code code block 2, line 4: `multiplier` is not an option of `redeliveryPolicy`}. */
        public String describe() {
            return "code block " + block + (line > 0 ? ", line " + line : "") + ": " + message;
        }
    }

    /** A fenced code block of an answer: its number (1 for the first), language, code and the line of its end fence. */
    private record CodeBlock(int number, String language, String code, int closingLine) {
    }

    /** A camel.* key at the start of a properties line: camel.server.port=8080. */
    private static final Pattern CAMEL_PROPERTY = Pattern.compile("^\\s*(camel\\.[\\w.\\[\\]-]+)\\s*=");

    /** The camel.* keys of the CLI and of other runtimes, which the catalog does not know. */
    private static final List<String> OTHER_PROPERTIES = List.of("camel.jbang.", "camel.springboot.", "camel.k.");

    /** What the properties check says of a key that does not exist, and the hint after it. */
    private static final Pattern UNKNOWN_PROPERTY = Pattern.compile("Unknown (option|component|dataformat|language)(.*)$");

    /** What the endpoint check says of an option the component does not have: line, scheme, option, the hint after. */
    private static final Pattern UNKNOWN_ENDPOINT_OPTION
            = Pattern.compile("^Line (\\d+): ([^:\\s]+): Unknown option '([^']+)'(.*)$");

    /**
     * A value of the wrong type, as the schema says it: what was found, and the hint of the validator if it has one.
     */
    private static final Pattern TYPE_FOUND = Pattern.compile("^(\\w+) found, \\w+ expected(?: \\((.+)\\))?$");

    /** The catalog's suggestions, and the words after them. */
    private static final Pattern DID_YOU_MEAN = Pattern.compile("^\\.?\\s*Did you mean: \\[([^]]*)]\\.?(.*)$");

    private static final Pattern UNKNOWN_KEY = Pattern.compile("property '([^']+)'");

    private static final Logger LOG = LoggerFactory.getLogger(AnswerChecks.class);

    private static volatile CamelCatalog defaultCatalog;

    private AnswerChecks() {
    }

    /**
     * What the model is asked, once, when its answer has invalid simple expressions: the answer is what the user copies
     * (CAMEL-24805), in the AI panel and in {@code camel ask} alike.
     */
    public static String correctionRequest(List<Problem> problems) {
        StringBuilder sb = new StringBuilder("Your answer contains ");
        sb.append(problems.size() == 1 ? "an invalid simple expression" : "invalid simple expressions").append(":\n");
        for (Problem p : problems) {
            sb.append("- ").append(p.message()).append('\n');
        }
        sb.append("Rule: functions and values go inside ${...}, operators go between placeholders with spaces:")
                .append(" ${header.user} ?: 'Guest', ${header.a} == 'b', ${body} != null.")
                .append(" Send the whole answer again with the expressions fixed; do not call a tool.");
        return sb.toString();
    }

    /** The problems of the simple expressions of an answer, checked against the default catalog. */
    public static List<Problem> checkSimple(String markdown) {
        return checkSimple(markdown, defaultCatalog());
    }

    /** The problems of the simple expressions of an answer, checked against the given catalog. */
    public static List<Problem> checkSimple(String markdown, CamelCatalog catalog) {
        if (markdown == null || markdown.isBlank() || catalog == null) {
            return List.of();
        }
        Map<String, Problem> problems = new LinkedHashMap<>();
        StringBuilder text = new StringBuilder();
        StringBuilder block = null;
        boolean yaml = false;
        for (String line : markdown.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("```")) {
                if (block == null) {
                    String language = trimmed.substring(3).trim().toLowerCase(Locale.ROOT);
                    yaml = language.equals("yaml") || language.equals("yml");
                    block = new StringBuilder();
                } else {
                    if (yaml) {
                        for (String error : SimpleChecks.validateYamlSimple(block.toString(), catalog)) {
                            problems.putIfAbsent(error, new Problem("the YAML block", error));
                        }
                    } else {
                        text.append(block).append('\n');
                    }
                    block = null;
                }
                continue;
            }
            if (block != null) {
                block.append(line).append('\n');
            } else {
                text.append(line).append('\n');
            }
        }
        if (block != null) {
            // an unterminated fence: the model ran out of tokens, judge what is there as text
            text.append(block);
        }
        Set<String> roots = roots(catalog);
        for (String placeholder : placeholders(text.toString())) {
            if (problems.containsKey(placeholder) || !isSimple(placeholder, roots)
                    || SimpleChecks.hasPlaceholderAsLogicalOperand(placeholder)) {
                continue;
            }
            try {
                LanguageValidationResult result = catalog.validateLanguageExpression(null, "simple", placeholder);
                if (!result.isSuccess()) {
                    String error = result.getShortError() != null ? result.getShortError() : result.getError();
                    if (error != null && !SimpleChecks.isMissingDependency(error)) {
                        problems.put(placeholder, new Problem(placeholder, error));
                    }
                } else {
                    // valid, and still not what it looks like: ${header.offset ?:0} reads a header named 'offset ?:0'
                    String elvis = SimpleChecks.elvisInsideFunction(placeholder, catalog.getCatalogVersion());
                    if (elvis != null) {
                        problems.put(placeholder, new Problem(placeholder, elvis));
                    }
                }
            } catch (Exception e) {
                // best effort: what the catalog cannot judge is not reported, the cause is in the debug log
                LOG.debug("Cannot validate the simple expression {} of the answer", placeholder, e);
            }
        }
        return new ArrayList<>(problems.values());
    }

    /** The mistakes of the code blocks of an answer, checked against the default catalog. */
    public static List<Mark> checkCode(String markdown) {
        return checkCode(markdown, defaultCatalog());
    }

    /**
     * The mistakes of the code blocks of an answer, checked against the given catalog: the keys and options of YAML
     * routes and of fragments of them, the options of their endpoint uris, and the camel.* keys of properties lines in
     * any block. Code in an answer is partial on purpose, so only what does not exist is a mistake, not a missing
     * option, a placeholder value or a fragment shown without its route: a mark that is wrong costs the user's trust in
     * the others.
     */
    public static List<Mark> checkCode(String markdown, CamelCatalog catalog) {
        if (markdown == null || markdown.isBlank() || catalog == null) {
            return List.of();
        }
        List<Mark> marks = new ArrayList<>();
        for (CodeBlock block : codeBlocks(markdown)) {
            try {
                marks.addAll(propertyMarks(block, catalog));
                if (block.language().equals("yaml") || block.language().equals("yml")) {
                    marks.addAll(yamlMarks(block, catalog));
                }
            } catch (Exception e) {
                // best effort, as for the simple expressions: the cause is in the debug log
                LOG.debug("Cannot check code block {} of the answer", block.number(), e);
            }
        }
        return marks;
    }

    /**
     * The answer with its marks: under each code block with a mistake, one line per mark, indented as the fence is, so
     * the user sees it where the code is. The code is left as it is, so copying a code block copies only the code.
     */
    public static String withMarks(String markdown, List<Mark> marks) {
        if (markdown == null || marks == null || marks.isEmpty()) {
            return markdown;
        }
        Map<Integer, Integer> blockEndingAt = new HashMap<>();
        for (CodeBlock block : codeBlocks(markdown)) {
            blockEndingAt.put(block.closingLine(), block.number());
        }
        String[] lines = markdown.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            sb.append(lines[i]);
            Integer number = blockEndingAt.get(i);
            List<Mark> here = number == null ? List.of() : marks.stream().filter(m -> m.block() == number).toList();
            if (!here.isEmpty()) {
                String indent = lines[i].substring(0, lines[i].indexOf('`'));
                sb.append('\n');
                for (Mark mark : here) {
                    sb.append('\n').append(indent).append("✗ ").append(mark.line() > 0 ? "line " + mark.line() + ": " : "")
                            .append(mark.message());
                }
                if (i + 1 < lines.length && !lines[i + 1].isBlank()) {
                    sb.append('\n');
                }
            }
            if (i < lines.length - 1) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * What the model is told with the next question about the mistakes left in its last answer, the marks of its code
     * and the simple expressions it did not fix, so that its next answer does not repeat them; null when there are
     * none. The user sees them under the answer already.
     */
    public static String historyNote(List<Mark> marks, List<Problem> problems) {
        List<String> found = new ArrayList<>();
        if (marks != null) {
            marks.forEach(m -> found.add(m.describe()));
        }
        if (problems != null) {
            problems.forEach(p -> found.add("invalid simple expression " + p.message()));
        }
        return found.isEmpty()
                ? null
                : "Checked against the Camel catalog, your last answer has mistakes, which the user sees marked: "
                  + String.join("; ", found) + ". Do not repeat them.";
    }

    /** The fenced code blocks of an answer; one whose fence is not closed (the model ran out of tokens) is left out. */
    private static List<CodeBlock> codeBlocks(String markdown) {
        List<CodeBlock> answer = new ArrayList<>();
        String[] lines = markdown.split("\n", -1);
        String language = null;
        StringBuilder code = null;
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (trimmed.startsWith("```")) {
                if (code == null) {
                    String tag = trimmed.substring(3).trim().toLowerCase(Locale.ROOT);
                    language = tag.contains(" ") ? tag.substring(0, tag.indexOf(' ')) : tag;
                    code = new StringBuilder();
                } else {
                    answer.add(new CodeBlock(answer.size() + 1, language, code.toString(), i));
                    code = null;
                }
            } else if (code != null) {
                code.append(lines[i]).append('\n');
            }
        }
        return answer;
    }

    /**
     * The camel.* keys of the properties lines of a block that the catalog does not know, in a block of any language: a
     * model writes application.properties lines in a yaml block too.
     */
    private static List<Mark> propertyMarks(CodeBlock block, CamelCatalog catalog) {
        List<Mark> marks = new ArrayList<>();
        String[] lines = block.code().split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            Matcher key = CAMEL_PROPERTY.matcher(lines[i]);
            if (!key.find() || OTHER_PROPERTIES.stream().anyMatch(key.group(1)::startsWith)) {
                continue;
            }
            String msg = PropertiesChecks.validatePropertyLine(lines[i].strip(), catalog, null);
            Matcher unknown = msg != null ? UNKNOWN_PROPERTY.matcher(msg) : null;
            if (unknown != null && unknown.find()) {
                String what = unknown.group(1).equals("option")
                        ? "a Camel option" : "a Camel option: there is no such " + unknown.group(1);
                marks.add(new Mark(block.number(), i + 1, "`" + key.group(1) + "` is not " + what + hint(unknown.group(2))));
            }
        }
        return marks;
    }

    /** The keys, options and endpoint options of a YAML block that do not exist; nothing when it is not Camel YAML. */
    private static List<Mark> yamlMarks(CodeBlock block, CamelCatalog catalog) throws Exception {
        YamlFragment fragment = YamlFragment.of(block.code());
        if (fragment == null) {
            return List.of();
        }
        List<Mark> marks = new ArrayList<>();
        for (Error error : SourceValidator.yamlValidator(catalog).validate(fragment.content(), ProjectFacts.NONE)) {
            Mark mark = schemaMark(block.number(), fragment, error);
            if (mark != null) {
                marks.add(mark);
            }
        }
        for (String msg : EndpointChecks.validateYamlEndpoints(fragment.content(), catalog)) {
            Matcher m = UNKNOWN_ENDPOINT_OPTION.matcher(msg);
            int line = m.matches() ? Integer.parseInt(m.group(1)) - fragment.shift() : 0;
            if (line > 0) {
                marks.add(new Mark(
                        block.number(), line,
                        "`" + m.group(3) + "` is not an option of the `" + m.group(2) + "` component" + hint(m.group(4))));
            }
        }
        return marks;
    }

    /**
     * The mark of an error of the schema, or null when it is no mistake of the block: an unknown key where the wrapping
     * put the block, a required key it leaves out, a value of the wrong type the validator has no hint for (a
     * placeholder often is one).
     */
    private static Mark schemaMark(int block, YamlFragment fragment, Error error) {
        String location = String.valueOf(error.getInstanceLocation());
        String message = error.getMessage();
        if (message == null || fragment.items().matcher(location).matches()) {
            return null;
        }
        String text = null;
        if ("additionalProperties".equals(error.getKeyword())) {
            Matcher key = UNKNOWN_KEY.matcher(message);
            int after = message.indexOf("additional properties");
            if (key.find()) {
                text = "`" + key.group(1) + "` is not " + owner(location)
                       + (after >= 0 ? hint(message.substring(after + "additional properties".length())) : "");
            }
        } else if ("type".equals(error.getKeyword())) {
            // a hint of the validator in place of the message (expression: holds the language as its key, ...) or after
            // it (onException is a map, not a list, ...); a bare "string found, object expected" is left out, and so is
            // null, the value of a key a fragment leaves empty (steps: with a comment under it)
            Matcher found = TYPE_FOUND.matcher(message);
            text = !found.matches() ? message : "null".equals(found.group(1)) ? null : found.group(2);
        }
        if (text == null) {
            return null;
        }
        return new Mark(block, Math.max(YamlValidator.lineOf(fragment.content(), error) - fragment.shift(), 0), text);
    }

    /** What an unknown key is not a part of: an option of the EIP at the location, or an EIP for an item of steps. */
    private static String owner(String location) {
        String[] parts = location.split("/");
        String last = parts.length > 0 ? parts[parts.length - 1] : "";
        if (!last.matches("\\d+")) {
            return "an option of `" + last + "`";
        }
        String list = parts.length > 1 ? parts[parts.length - 2] : "";
        return switch (list) {
            case "steps" -> "an EIP";
            case "beans" -> "a key of a bean";
            default -> "a key of the items of `" + list + "`";
        };
    }

    /**
     * The hint after what a check says, as a mark writes it: " (did you mean `delete`?)" for the catalog's ". Did you
     * mean: [delete]", the check's own words in parentheses, or nothing.
     */
    private static String hint(String rest) {
        // the schema's "did you mean 'backOffMultiplier'?" as code, as the names of the mark are
        String text = rest == null ? "" : rest.strip().replaceAll("did you mean '([\\w.-]+)'\\?", "did you mean `$1`?");
        Matcher dym = DID_YOU_MEAN.matcher(text);
        if (dym.matches()) {
            List<String> names = new ArrayList<>();
            for (String name : dym.group(1).split(",")) {
                if (!name.isBlank()) {
                    names.add("`" + name.strip() + "`");
                }
            }
            String more = dym.group(2).strip();
            return (names.isEmpty() ? "" : " (did you mean " + String.join(" or ", names) + "?)")
                   + (more.isEmpty() ? "" : " " + more);
        }
        if (text.isEmpty()) {
            return "";
        }
        return text.startsWith("(") ? " " + text : " (" + text.replaceFirst("^[.,:;]\\s*", "") + ")";
    }

    /**
     * The {@code ${...}} placeholders of a text, braces balanced so {@code ${header.${header.key}}} is one placeholder,
     * in the order they appear.
     */
    static List<String> placeholders(String text) {
        List<String> answer = new ArrayList<>();
        int i = 0;
        while (i < text.length() - 1) {
            if (text.charAt(i) == '$' && text.charAt(i + 1) == '{') {
                int depth = 0;
                int end = -1;
                for (int j = i + 1; j < text.length(); j++) {
                    char ch = text.charAt(j);
                    if (ch == '{') {
                        depth++;
                    } else if (ch == '}') {
                        depth--;
                        if (depth == 0) {
                            end = j;
                            break;
                        }
                    } else if (ch == '\n' && depth == 1) {
                        // a placeholder does not span lines; an unclosed one is prose
                        break;
                    }
                }
                if (end < 0) {
                    i += 2;
                    continue;
                }
                answer.add(text.substring(i, end + 1));
                i = end + 1;
            } else {
                i++;
            }
        }
        return answer;
    }

    /**
     * Whether the placeholder starts with a simple function or value, one of the {@link #roots(CamelCatalog) roots}, so
     * the catalog is the judge of it.
     */
    static boolean isSimple(String placeholder, Set<String> roots) {
        String content = placeholder.substring(2, placeholder.length() - 1).strip();
        int end = 0;
        while (end < content.length()) {
            char ch = content.charAt(end);
            if (!Character.isLetterOrDigit(ch) && ch != '-' && ch != '_') {
                break;
            }
            end++;
        }
        if (end == 0) {
            return false;
        }
        return roots.contains(content.substring(0, end));
    }

    /**
     * The first word of every simple function name: header for header.name, date for date:command:pattern. Computed
     * once per answer from the catalog given, so a catalog of another Camel version answers for its own functions.
     */
    static Set<String> roots(CamelCatalog catalog) {
        // the language model behind it is cached by the catalog, so this is a walk over a list of names
        Set<String> answer = new HashSet<>();
        LanguageModel simple = catalog.languageModel("simple");
        if (simple != null && simple.getFunctions() != null) {
            for (LanguageModel.LanguageFunctionModel fn : simple.getFunctions()) {
                String name = fn.getName();
                if (name != null) {
                    int end = 0;
                    while (end < name.length()) {
                        char ch = name.charAt(end);
                        if (!Character.isLetterOrDigit(ch) && ch != '-' && ch != '_') {
                            break;
                        }
                        end++;
                    }
                    if (end > 0) {
                        answer.add(name.substring(0, end));
                    }
                }
            }
        }
        return answer;
    }

    private static CamelCatalog defaultCatalog() {
        CamelCatalog answer = defaultCatalog;
        if (answer == null) {
            synchronized (AnswerChecks.class) {
                answer = defaultCatalog;
                if (answer == null) {
                    answer = new DefaultCamelCatalog();
                    defaultCatalog = answer;
                }
            }
        }
        return answer;
    }
}
