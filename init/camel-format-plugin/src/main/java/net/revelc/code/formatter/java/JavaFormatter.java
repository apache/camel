/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.revelc.code.formatter.java;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.revelc.code.formatter.AbstractCacheableFormatter;
import net.revelc.code.formatter.ConfigurationSource;
import net.revelc.code.formatter.Formatter;
import net.revelc.code.formatter.LineEnding;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;
import org.eclipse.text.edits.TextEdit;

public class JavaFormatter extends AbstractCacheableFormatter implements Formatter {

    private static final String EXCLUSION_PATTERN = "org.apache.camel.formatter.java.exclusion_pattern";

    private CodeFormatter formatter;
    private Pattern exclusionPattern;

    @Override
    public void init(Map<String, String> options, ConfigurationSource cfg) {
        super.initCfg(cfg);

        this.formatter = ToolFactory.createCodeFormatter(options, ToolFactory.M_FORMAT_EXISTING);
        String ep = options.get(EXCLUSION_PATTERN);
        if (ep != null) {
            exclusionPattern = Pattern.compile(ep, Pattern.MULTILINE);
        }
    }

    @Override
    public String doFormat(String code, LineEnding ending) throws IOException, BadLocationException {
        TextEdit te;
        try {
            List<IRegion> regions = new ArrayList<>();
            int start = 0;
            if (exclusionPattern != null) {
                Matcher matcher = exclusionPattern.matcher(code);
                while (matcher.find()) {
                    int s = matcher.start();
                    int e = matcher.end();
                    regions.add(new Region(start, s - start));
                    start = e;
                }
            }
            regions.add(new Region(start, code.length() - start));

            te = this.formatter.format(CodeFormatter.K_COMPILATION_UNIT | CodeFormatter.F_INCLUDE_COMMENTS, code,
                    regions.toArray(new IRegion[0]), 0, ending.getChars());
            if (te == null) {
                this.log.debug(
                        "Code cannot be formatted. Possible cause is unmatched source/target/compliance version.");
                return null;
            }
        } catch (IndexOutOfBoundsException e) {
            this.log.debug("Code cannot be formatted for text -->" + code + "<--", e);
            return null;
        }

        IDocument doc = new Document(code);
        te.apply(doc);
        String formattedCode = doc.get();

        if (code.equals(formattedCode)) {
            return null;
        }
        return formattedCode;
    }

    @Override
    public boolean isInitialized() {
        return formatter != null;
    }

}
