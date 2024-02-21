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
package org.apache.camel.language.xtokenizer;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.converter.jaxp.StaxConverter;
import org.apache.camel.spi.NamespaceAware;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.xml.io.util.XmlStreamReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link org.apache.camel.language.xtokenizer.XMLTokenizeLanguage} based iterator.
 */
public class XMLTokenExpressionIterator extends ExpressionAdapter implements NamespaceAware {
    protected final String path;
    protected char mode;
    protected int group;
    protected Expression source;
    protected Map<String, String> nsmap;

    public XMLTokenExpressionIterator(String path, char mode) {
        this(null, path, mode);
    }

    public XMLTokenExpressionIterator(Expression source, String path, char mode) {
        this.source = source;
        this.path = path;
        this.mode = mode;
    }

    @Override
    public void init(CamelContext context) {
        super.init(context);
        // group must be 1 or higher
        this.group = Math.max(group, 1);
    }

    @Override
    public void setNamespaces(Map<String, String> nsmap) {
        this.nsmap = nsmap;
    }

    @Override
    public Map<String, String> getNamespaces() {
        return nsmap;
    }

    public void setMode(char mode) {
        this.mode = mode;
    }

    public void setMode(String mode) {
        this.mode = mode != null ? mode.charAt(0) : 0;
    }

    public int getGroup() {
        return group;
    }

    public void setGroup(int group) {
        this.group = group;
    }

    protected Iterator<?> createIterator(InputStream in, String charset)
            throws XMLStreamException, UnsupportedEncodingException {
        return createIterator(new InputStreamReader(in, charset));
    }

    protected Iterator<?> createIterator(InputStream in)
            throws XMLStreamException, IOException {
        return createIterator(new XmlStreamReader(in));
    }

    protected Iterator<?> createIterator(Reader in) throws XMLStreamException {
        return new XMLTokenIterator(path, nsmap, mode, group, in);
    }

    @Override
    public boolean matches(Exchange exchange) {
        // as a predicate we must close the stream, as we do not return an iterator that can be used
        // afterwards to iterate the input stream
        Object value = doEvaluate(exchange, true);
        return ObjectHelper.evaluateValuePredicate(value);
    }

    @Override
    public Object evaluate(Exchange exchange) {
        // as we return an iterator to access the input stream, we should not close it
        return doEvaluate(exchange, false);
    }

    /**
     * Strategy to evaluate the exchange
     *
     * @param  exchange    the exchange
     * @param  closeStream whether to close the stream before returning from this method.
     * @return             the evaluated value
     */
    protected Object doEvaluate(Exchange exchange, boolean closeStream) {
        InputStream in = null;
        Reader reader = null;
        try {
            if (source != null) {
                in = source.evaluate(exchange, InputStream.class);
            } else {
                in = exchange.getIn().getBody(InputStream.class);
            }
            if (in == null) {
                throw new InvalidPayloadException(exchange, InputStream.class);
            }
            // use xml stream reader which is capable of handling reading the xml stream
            // according to <xml encoding> charset
            reader = new XmlStreamReader(in);
            return createIterator(reader);
        } catch (Exception e) {
            exchange.setException(e);
            // must close input stream
            IOHelper.close(in, reader);
            return null;
        } finally {
            if (closeStream) {
                IOHelper.close(in, reader);
            }
        }
    }

    static class XMLTokenIterator implements Iterator<Object>, Closeable {
        private static final Logger LOG = LoggerFactory.getLogger(XMLTokenIterator.class);
        private static final Pattern NAMESPACE_PATTERN = Pattern.compile("xmlns(:\\w+|)\\s*=\\s*('[^']*'|\"[^\"]*\")");

        private AttributedQName[] splitpath;
        private int index;
        private char mode;
        private int group;
        private RecordableReader in;
        private XMLStreamReader reader;
        private List<QName> path;
        private List<Map<String, String>> namespaces;
        private List<String> segments;
        private List<QName> segmentlog;
        private List<String> tokens;
        private int code;
        private int consumed;
        private boolean backtrack;
        private int trackdepth = -1;
        private int depth;
        private boolean compliant;

        private Object nextToken;

        XMLTokenIterator(String path, Map<String, String> nsmap, char mode, int group, Reader in) throws XMLStreamException {
            final String[] sl = path.substring(1).split("/");
            this.splitpath = new AttributedQName[sl.length];
            for (int i = 0; i < sl.length; i++) {
                String s = sl[i];
                if (s.length() > 0) {
                    int d = s.indexOf(':');
                    String pfx = StringHelper.before(s, ":", "");
                    this.splitpath[i] = new AttributedQName(
                            "*".equals(pfx) ? "*" : nsmap == null ? "" : nsmap.get(pfx), d > 0 ? s.substring(d + 1) : s, pfx);
                }
            }

            this.mode = mode != 0 ? mode : 'i';
            this.group = group > 0 ? group : 1;
            this.in = new RecordableReader(in);
            this.reader = new StaxConverter().createXMLStreamReader(this.in);

            LOG.trace("reader.class: {}", reader.getClass());
            // perform the first offset compliance test
            int coff = reader.getLocation().getCharacterOffset();
            if (coff != 0) {
                throw new XMLStreamException("XMLStreamReader does not supporting getting location");
            }

            this.path = new ArrayList<>();

            // wrapped mode needs the segments and the injected mode needs the namespaces
            if (this.mode == 'w') {
                this.segments = new ArrayList<>();
                this.segmentlog = new ArrayList<>();
            } else if (this.mode == 'i') {
                this.namespaces = new ArrayList<>();
            }
            // when grouping the tokens, allocate the storage to temporarily store tokens.
            if (this.group > 1) {
                this.tokens = new ArrayList<>();
            }
            this.nextToken = getNextToken();
        }

        private boolean isDoS() {
            return splitpath[index] == null;
        }

        private AttributedQName current() {
            return splitpath[index + (isDoS() ? 1 : 0)];
        }

        private AttributedQName ancestor() {
            return index == 0 ? null : splitpath[index - 1];
        }

        private void down() {
            if (isDoS()) {
                index++;
            }
            index++;
        }

        private void up() {
            index--;
        }

        private boolean isBottom() {
            return index == splitpath.length - (isDoS() ? 2 : 1);
        }

        private boolean isTop() {
            return index == 0;
        }

        private int readNext() throws XMLStreamException {
            int c = code;
            if (c > 0) {
                code = 0;
            } else {
                c = reader.next();
            }
            return c;
        }

        private String getCurrentText() {
            int pos = reader.getLocation().getCharacterOffset();
            String txt = in.getText(pos - consumed);
            consumed = pos;
            // keep recording
            in.record();
            return txt;
        }

        private void pushName(QName name) {
            path.add(name);
        }

        private QName popName() {
            return path.remove(path.size() - 1);
        }

        private void pushSegment(QName qname, String token) {
            segments.add(token);
            segmentlog.add(qname);
        }

        private String popSegment() {
            return segments.remove(segments.size() - 1);
        }

        private QName peekLog() {
            return segmentlog.get(segmentlog.size() - 1);
        }

        private QName popLog() {
            return segmentlog.remove(segmentlog.size() - 1);
        }

        private void pushNamespaces(XMLStreamReader reader) {
            Map<String, String> m = new HashMap<>();
            if (!namespaces.isEmpty()) {
                m.putAll(namespaces.get(namespaces.size() - 1));
            }
            for (int i = 0; i < reader.getNamespaceCount(); i++) {
                m.put(reader.getNamespacePrefix(i), reader.getNamespaceURI(i));
            }
            namespaces.add(m);
        }

        private void popNamespaces() {
            namespaces.remove(namespaces.size() - 1);
        }

        private Map<String, String> getCurrentNamespaceBindings() {
            return namespaces.get(namespaces.size() - 1);
        }

        private void readCurrent(boolean incl) throws XMLStreamException {
            int d = depth;
            while (d <= depth) {
                int code = reader.next();
                if (code == XMLStreamConstants.START_ELEMENT) {
                    depth++;
                } else if (code == XMLStreamConstants.END_ELEMENT) {
                    depth--;
                }
            }
            // either look ahead to the next token or stay at the end element token
            if (incl) {
                code = reader.next();
            } else {
                code = reader.getEventType();
                if (code == XMLStreamConstants.END_ELEMENT) {
                    // revert the depth count to avoid double counting the up event
                    depth++;
                }
            }
        }

        private String getCurrentToken() throws XMLStreamException {
            readCurrent(true);
            popName();

            String token = createContextualToken(getCurrentText());
            if (mode == 'i') {
                popNamespaces();
            }

            return token;
        }

        private String createContextualToken(String token) {
            StringBuilder sb = new StringBuilder();
            if (mode == 'w' && group == 1) {
                for (int i = 0; i < segments.size(); i++) {
                    sb.append(segments.get(i));
                }
                sb.append(token);
                for (int i = path.size() - 1; i >= 0; i--) {
                    QName q = path.get(i);
                    sb.append("</").append(makeName(q)).append(">");
                }

            } else if (mode == 'i') {
                final String stag = token.substring(0, token.indexOf('>') + 1);
                Set<String> skip = new HashSet<>();
                Matcher matcher = NAMESPACE_PATTERN.matcher(stag);
                char quote = 0;
                while (matcher.find()) {
                    String prefix = matcher.group(1);
                    if (prefix.length() > 0) {
                        prefix = prefix.substring(1);
                    }
                    skip.add(prefix);
                    if (quote == 0) {
                        quote = matcher.group(2).charAt(0);
                    }
                }
                if (quote == 0) {
                    quote = '"';
                }
                boolean empty = stag.endsWith("/>");
                sb.append(token, 0, stag.length() - (empty ? 2 : 1));
                for (Entry<String, String> e : getCurrentNamespaceBindings().entrySet()) {
                    if (!skip.contains(e.getKey())) {
                        boolean defaultNS = e.getKey() == null || e.getKey().isEmpty();
                        if (defaultNS) {
                            sb.append(" xmlns").append("=").append(quote).append(e.getValue()).append(quote);
                        } else {
                            sb.append(" xmlns:")
                                    .append(e.getKey()).append("=").append(quote).append(e.getValue()).append(quote);
                        }
                    }
                }
                sb.append(token, stag.length() - (empty ? 2 : 1), token.length());
            } else if (mode == 'u') {
                int bp = token.indexOf(">");
                int ep = token.lastIndexOf("</");
                if (bp > 0 && ep > 0) {
                    sb.append(token, bp + 1, ep);
                }
            } else if (mode == 't') {
                int bp = 0;
                for (;;) {
                    int ep = token.indexOf('>', bp);
                    bp = token.indexOf('<', ep);
                    if (bp < 0) {
                        break;
                    }
                    sb.append(token, ep + 1, bp);
                }
            } else {
                return token;
            }

            return sb.toString();
        }

        private String getGroupedToken() {
            StringBuilder sb = new StringBuilder();
            if (mode == 'w') {
                // for wrapped
                for (int i = 0; i < segments.size(); i++) {
                    sb.append(segments.get(i));
                }
                for (String s : tokens) {
                    sb.append(s);
                }
                for (int i = path.size() - 1; i >= 0; i--) {
                    QName q = path.get(i);
                    sb.append("</").append(makeName(q)).append(">");
                }
            } else {
                // for injected, unwrapped, text
                sb.append("<group>");
                for (String s : tokens) {
                    sb.append(s);
                }
                sb.append("</group>");
            }
            tokens.clear();
            return sb.toString();
        }

        private String getNextToken() throws XMLStreamException {
            int xcode = 0;
            while (xcode != XMLStreamConstants.END_DOCUMENT) {
                xcode = readNext();

                switch (xcode) {
                    case XMLStreamConstants.START_ELEMENT:
                        depth++;
                        QName name = reader.getName();
                        if (LOG.isTraceEnabled()) {
                            LOG.trace("se={}; depth={}; trackdepth={}", name, depth, trackdepth);
                        }

                        String token = getCurrentText();
                        // perform the second compliance test
                        if (!compliant) {
                            if (token != null && token.startsWith("<") && !token.startsWith("<?")) {
                                throw new XMLStreamException("XMLStreamReader does not supporting getting location");
                            }
                            compliant = true;
                        }

                        LOG.trace("token={}", token);
                        if (!backtrack && mode == 'w') {
                            pushSegment(name, token);
                        }
                        pushName(name);
                        if (mode == 'i') {
                            pushNamespaces(reader);
                        }
                        backtrack = false;
                        if (current().matches(name)) {
                            // mark the position of the match in the segments list
                            if (isBottom()) {
                                // final match
                                token = getCurrentToken();
                                backtrack = true;
                                trackdepth = depth;
                                if (group > 1) {
                                    tokens.add(token);
                                    if (group == tokens.size()) {
                                        return getGroupedToken();
                                    }
                                } else {
                                    return token;
                                }
                            } else {
                                // intermediary match
                                down();
                            }
                        } else if (isDoS()) {
                            // continue
                        } else {
                            // skip
                            readCurrent(false);
                        }
                        break;
                    case XMLStreamConstants.END_ELEMENT:
                        if ((backtrack || trackdepth > 0 && depth == trackdepth)
                                && mode == 'w' && group > 1 && !tokens.isEmpty()) {
                            // flush the left over using the current context
                            code = XMLStreamConstants.END_ELEMENT;
                            return getGroupedToken();
                        }

                        depth--;
                        QName endname = reader.getName();
                        LOG.trace("ee={}", endname);

                        popName();
                        if (mode == 'i') {
                            popNamespaces();
                        }

                        int pc = 0;
                        if (backtrack || trackdepth > 0 && depth == trackdepth - 1) {
                            // reactive backtrack if not backtracking and update the track depth
                            backtrack = true;
                            trackdepth--;
                            if (mode == 'w') {
                                while (!endname.equals(peekLog())) {
                                    pc++;
                                    popLog();
                                }
                            }
                        }

                        if (backtrack) {
                            if (mode == 'w') {
                                for (int i = 0; i < pc; i++) {
                                    popSegment();
                                }
                            }
                        }

                        if (ancestor() == null && !isTop()
                                || ancestor() != null && ancestor().matches(endname)) {
                            up();
                        }
                        break;
                    case XMLStreamConstants.END_DOCUMENT:
                        LOG.trace("depth={}", depth);
                        if (group > 1 && !tokens.isEmpty()) {
                            // flush the left over before really going EoD
                            code = XMLStreamConstants.END_DOCUMENT;
                            return getGroupedToken();
                        }
                        break;
                    default:
                        break;
                }
            }
            return null;
        }

        private static String makeName(QName qname) {
            String pfx = qname.getPrefix();
            return pfx.isEmpty() ? qname.getLocalPart() : qname.getPrefix() + ":" + qname.getLocalPart();
        }

        @Override
        public boolean hasNext() {
            return nextToken != null;
        }

        @Override
        public Object next() {
            Object o = nextToken;
            try {
                nextToken = getNextToken();
            } catch (XMLStreamException e) {
                nextToken = null;
                throw new RuntimeException(e);
            }
            return o;
        }

        @Override
        public void remove() {
            // noop
        }

        @Override
        public void close() throws IOException {
            try {
                reader.close();
            } catch (Exception e) {
                // ignore
            }
            // need to close the original input stream as well as the reader do not delegate close it
            IOHelper.close(in);
        }
    }

    static class AttributedQName extends QName {
        private static final long serialVersionUID = 9878370226894144L;
        private Pattern lcpattern;
        private boolean nsany;

        AttributedQName(String localPart) {
            super(localPart);
            checkWildcard("", localPart);
        }

        AttributedQName(String namespaceURI, String localPart, String prefix) {
            super(namespaceURI, localPart, prefix);
            checkWildcard(namespaceURI, localPart);
        }

        AttributedQName(String namespaceURI, String localPart) {
            super(namespaceURI, localPart);
            checkWildcard(namespaceURI, localPart);
        }

        public boolean matches(QName qname) {
            return (nsany || getNamespaceURI().equals(qname.getNamespaceURI()))
                    && (lcpattern != null
                            ? lcpattern.matcher(qname.getLocalPart()).matches()
                            : getLocalPart().equals(qname.getLocalPart()));
        }

        private void checkWildcard(String nsa, String lcp) {
            nsany = "*".equals(nsa);
            boolean wc = false;
            for (int i = 0; i < lcp.length(); i++) {
                char c = lcp.charAt(i);
                if (c == '?' || c == '*') {
                    wc = true;
                    break;
                }
            }
            if (wc) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < lcp.length(); i++) {
                    char c = lcp.charAt(i);
                    switch (c) {
                        case '.':
                            sb.append("\\.");
                            break;
                        case '*':
                            sb.append(".*");
                            break;
                        case '?':
                            sb.append('.');
                            break;
                        default:
                            sb.append(c);
                            break;
                    }
                }
                lcpattern = Pattern.compile(sb.toString());
            }
        }
    }
}
