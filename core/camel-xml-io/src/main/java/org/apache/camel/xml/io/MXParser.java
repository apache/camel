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

/* -*-             c-basic-offset: 4; indent-tabs-mode: nil; -*-  //------100-columns-wide------>|*/
/*
 * Copyright (c) 2003 Extreme! Lab, Indiana University. All rights reserved.
 *
 * This software is open source. See the bottom of this file for the license.
 *
 * $Id: MXParser.java,v 1.52 2006/11/09 18:29:37 aslom Exp $
 */

package org.apache.camel.xml.io;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import org.apache.camel.xml.io.util.XmlStreamReader;

// https://github.com/codelibs/xpp3/blob/master/src/main/java/org/xmlpull/mxp1/MXParser.java

//TODO best handling of interning issues
//   have isAllNewStringInterned ???

//TODO handling surrogate pairs: http://www.unicode.org/unicode/faq/utf_bom.html#6

//TODO review code for use of bufAbsoluteStart when keeping pos between next()/fillBuf()

/**
 * Absolutely minimal implementation of XMLPULL V1 API
 *
 * @author <a href="http://www.extreme.indiana.edu/~aslom/">Aleksander Slominski</a>
 */
public class MXParser implements XmlPullParser {
    // NOTE: no interning of those strings --> by Java lang spec they MUST be
    // already interned
    protected static final String XML_URI = "http://www.w3.org/XML/1998/namespace";
    protected static final String XMLNS_URI = "http://www.w3.org/2000/xmlns/";
    protected static final String FEATURE_XML_ROUNDTRIP =
    // "http://xmlpull.org/v1/doc/features.html#xml-roundtrip";
            "http://xmlpull.org/v1/doc/features.html#xml-roundtrip";
    protected static final String FEATURE_NAMES_INTERNED = "http://xmlpull.org/v1/doc/features.html#names-interned";
    protected static final String PROPERTY_XMLDECL_VERSION = "http://xmlpull.org/v1/doc/properties.html#xmldecl-version";
    protected static final String PROPERTY_XMLDECL_STANDALONE = "http://xmlpull.org/v1/doc/properties.html#xmldecl-standalone";
    protected static final String PROPERTY_XMLDECL_CONTENT = "http://xmlpull.org/v1/doc/properties.html#xmldecl-content";
    protected static final String PROPERTY_LOCATION = "http://xmlpull.org/v1/doc/properties.html#location";

    /**
     * Implementation notice: the is instance variable that controls if newString() is interning.
     * <p>
     * <b>NOTE:</b> newStringIntern <b>always</b> returns interned strings and newString MAY return interned String
     * depending on this variable.
     * <p>
     * <b>NOTE:</b> by default in this minimal implementation it is false!
     */
    protected boolean allStringsInterned;

    protected void resetStringCache() {
        // System.out.println("resetStringCache() minimum called");
    }

    protected String newString(char[] cbuf, int off, int len) {
        return new String(cbuf, off, len);
    }

    protected String newStringIntern(char[] cbuf, int off, int len) {
        return (new String(cbuf, off, len)).intern();
    }

    private static final boolean TRACE_SIZING = false;

    // NOTE: features are not resettable and typically defaults to false ...
    protected boolean processNamespaces;
    protected boolean roundtripSupported;

    // global parser state
    protected String location;
    protected int startLineNumber;
    protected int lineNumber;
    protected int columnNumber;
    protected boolean seenRoot;
    protected boolean reachedEnd;
    protected int eventType;
    protected boolean emptyElementTag;
    // element stack
    protected int depth;
    protected char[][] elRawName;
    protected int[] elRawNameEnd;
    protected int[] elRawNameLine;

    protected String[] elName;
    protected String[] elPrefix;
    protected String[] elUri;
    // protected String elValue[];
    protected int[] elNamespaceCount;

    /**
     * Make sure that we have enough space to keep element stack if passed size. It will always create one additional
     * slot then current depth
     */
    protected void ensureElementsCapacity() {
        final int elStackSize = elName != null ? elName.length : 0;
        if ((depth + 1) >= elStackSize) {
            // we add at least one extra slot ...
            final int newSize = (depth >= 7 ? 2 * depth : 8) + 2; // = lucky 7 +
                                                                 // 1 //25
            if (TRACE_SIZING) {
                System.err.println("TRACE_SIZING elStackSize " + elStackSize + " ==> " + newSize);
            }
            final boolean needsCopying = elStackSize > 0;
            String[] arr = null;
            // reuse arr local variable slot
            arr = new String[newSize];
            if (needsCopying)
                System.arraycopy(elName, 0, arr, 0, elStackSize);
            elName = arr;
            arr = new String[newSize];
            if (needsCopying)
                System.arraycopy(elPrefix, 0, arr, 0, elStackSize);
            elPrefix = arr;
            arr = new String[newSize];
            if (needsCopying)
                System.arraycopy(elUri, 0, arr, 0, elStackSize);
            elUri = arr;

            int[] iarr = new int[newSize];
            if (needsCopying) {
                System.arraycopy(elNamespaceCount, 0, iarr, 0, elStackSize);
            } else {
                // special initialization
                iarr[0] = 0;
            }
            elNamespaceCount = iarr;

            // TODO: avoid using element raw name ...
            iarr = new int[newSize];
            if (needsCopying) {
                System.arraycopy(elRawNameEnd, 0, iarr, 0, elStackSize);
            }
            elRawNameEnd = iarr;

            iarr = new int[newSize];
            if (needsCopying) {
                System.arraycopy(elRawNameLine, 0, iarr, 0, elStackSize);
            }
            elRawNameLine = iarr;

            final char[][] carr = new char[newSize][];
            if (needsCopying) {
                System.arraycopy(elRawName, 0, carr, 0, elStackSize);
            }
            elRawName = carr;
            // arr = new String[newSize];
            // if(needsCopying) System.arraycopy(elLocalName, 0, arr, 0,
            // elStackSize);
            // elLocalName = arr;
            // arr = new String[newSize];
            // if(needsCopying) System.arraycopy(elDefaultNs, 0, arr, 0,
            // elStackSize);
            // elDefaultNs = arr;
            // int[] iarr = new int[newSize];
            // if(needsCopying) System.arraycopy(elNsStackPos, 0, iarr, 0,
            // elStackSize);
            // for (int i = elStackSize; i < iarr.length; i++)
            // {
            // iarr[i] = (i > 0) ? -1 : 0;
            // }
            // elNsStackPos = iarr;
            // assert depth < elName.length;
        }
    }

    // attribute stack
    protected int attributeCount;
    protected String[] attributeName;
    protected int[] attributeNameHash;
    // protected int attributeNameStart[];
    // protected int attributeNameEnd[];
    protected String[] attributePrefix;
    protected String[] attributeUri;
    protected String[] attributeValue;
    // protected int attributeValueStart[];
    // protected int attributeValueEnd[];

    /**
     * Make sure that in attributes temporary array is enough space.
     */
    protected void ensureAttributesCapacity(int size) {
        final int attrPosSize = attributeName != null ? attributeName.length : 0;
        if (size >= attrPosSize) {
            final int newSize = size > 7 ? 2 * size : 8; // = lucky 7 + 1 //25
            if (TRACE_SIZING) {
                System.err.println("TRACE_SIZING attrPosSize " + attrPosSize + " ==> " + newSize);
            }
            final boolean needsCopying = attrPosSize > 0;
            String[] arr = null;

            arr = new String[newSize];
            if (needsCopying)
                System.arraycopy(attributeName, 0, arr, 0, attrPosSize);
            attributeName = arr;

            arr = new String[newSize];
            if (needsCopying)
                System.arraycopy(attributePrefix, 0, arr, 0, attrPosSize);
            attributePrefix = arr;

            arr = new String[newSize];
            if (needsCopying)
                System.arraycopy(attributeUri, 0, arr, 0, attrPosSize);
            attributeUri = arr;

            arr = new String[newSize];
            if (needsCopying)
                System.arraycopy(attributeValue, 0, arr, 0, attrPosSize);
            attributeValue = arr;

            if (!allStringsInterned) {
                final int[] iarr = new int[newSize];
                if (needsCopying)
                    System.arraycopy(attributeNameHash, 0, iarr, 0, attrPosSize);
                attributeNameHash = iarr;
            }

            arr = null;
            // //assert attrUri.length > size
        }
    }

    // namespace stack
    protected int namespaceEnd;
    protected String[] namespacePrefix;
    protected int[] namespacePrefixHash;
    protected String[] namespaceUri;

    protected void ensureNamespacesCapacity(int size) {
        final int namespaceSize = namespacePrefix != null ? namespacePrefix.length : 0;
        if (size >= namespaceSize) {
            final int newSize = size > 7 ? 2 * size : 8; // = lucky 7 + 1 //25
            if (TRACE_SIZING) {
                System.err.println("TRACE_SIZING namespaceSize " + namespaceSize + " ==> " + newSize);
            }
            final String[] newNamespacePrefix = new String[newSize];
            final String[] newNamespaceUri = new String[newSize];
            if (namespacePrefix != null) {
                System.arraycopy(namespacePrefix, 0, newNamespacePrefix, 0, namespaceEnd);
                System.arraycopy(namespaceUri, 0, newNamespaceUri, 0, namespaceEnd);
            }
            namespacePrefix = newNamespacePrefix;
            namespaceUri = newNamespaceUri;

            if (!allStringsInterned) {
                final int[] newNamespacePrefixHash = new int[newSize];
                if (namespacePrefixHash != null) {
                    System.arraycopy(namespacePrefixHash, 0, newNamespacePrefixHash, 0, namespaceEnd);
                }
                namespacePrefixHash = newNamespacePrefixHash;
            }
            // prefixesSize = newSize;
            // //assert nsPrefixes.length > size && nsPrefixes.length == newSize
        }
    }

    /**
     * simplistic implementation of hash function that has <b>constant</b> time to compute - so it also means
     * diminishing hash quality for long strings but for XML parsing it should be good enough ...
     */
    protected static int fastHash(char[] ch, int off, int len) {
        if (len == 0)
            return 0;
        // assert len >0
        int hash = ch[off]; // hash at beginning
        // try {
        hash = (hash << 7) + ch[off + len - 1]; // hash at the end
        // } catch(ArrayIndexOutOfBoundsException aie) {
        // aie.printStackTrace(); //should never happen ...
        // throw new RuntimeException("this is violation of pre-condition");
        // }
        if (len > 16)
            hash = (hash << 7) + ch[off + (len / 4)]; // 1/4 from beginning
        if (len > 8)
            hash = (hash << 7) + ch[off + (len / 2)]; // 1/2 of string size ...
        // notice that hash is at most done 3 times <<7 so shifted by 21 bits 8
        // bit value
        // so max result == 29 bits so it is quite just below 31 bits for long
        // (2^32) ...
        // assert hash >= 0;
        return hash;
    }

    // entity replacement stack
    protected int entityEnd;

    protected String[] entityName;
    protected char[][] entityNameBuf;
    protected String[] entityReplacement;
    protected char[][] entityReplacementBuf;

    protected int[] entityNameHash;

    protected void ensureEntityCapacity() {
        final int entitySize = entityReplacementBuf != null ? entityReplacementBuf.length : 0;
        if (entityEnd >= entitySize) {
            final int newSize = entityEnd > 7 ? 2 * entityEnd : 8; // = lucky 7
                                                                  // + 1 //25
            if (TRACE_SIZING) {
                System.err.println("TRACE_SIZING entitySize " + entitySize + " ==> " + newSize);
            }
            final String[] newEntityName = new String[newSize];
            final char[][] newEntityNameBuf = new char[newSize][];
            final String[] newEntityReplacement = new String[newSize];
            final char[][] newEntityReplacementBuf = new char[newSize][];
            if (entityName != null) {
                System.arraycopy(entityName, 0, newEntityName, 0, entityEnd);
                System.arraycopy(entityNameBuf, 0, newEntityNameBuf, 0, entityEnd);
                System.arraycopy(entityReplacement, 0, newEntityReplacement, 0, entityEnd);
                System.arraycopy(entityReplacementBuf, 0, newEntityReplacementBuf, 0, entityEnd);
            }
            entityName = newEntityName;
            entityNameBuf = newEntityNameBuf;
            entityReplacement = newEntityReplacement;
            entityReplacementBuf = newEntityReplacementBuf;

            if (!allStringsInterned) {
                final int[] newEntityNameHash = new int[newSize];
                if (entityNameHash != null) {
                    System.arraycopy(entityNameHash, 0, newEntityNameHash, 0, entityEnd);
                }
                entityNameHash = newEntityNameHash;
            }
        }
    }

    // input buffer management
    protected static final int READ_CHUNK_SIZE = 8 * 1024; // max data chars in
                                                          // one read() call
    protected Reader reader;
    protected String inputEncoding;

    protected int bufLoadFactor = 95; // 99%
    // protected int bufHardLimit; // only matters when expanding

    protected char[] buf = new char[Runtime.getRuntime().freeMemory() > 1000000L ? READ_CHUNK_SIZE : 256];
    protected int bufSoftLimit = (bufLoadFactor * buf.length) / 100; // desirable
                                                                    // size of
                                                                    // buffer
    protected boolean preventBufferCompaction;

    protected int bufAbsoluteStart; // this is buf
    protected int bufStart;
    protected int bufEnd;
    protected int pos;
    protected int posStart;
    protected int posEnd;

    protected char[] pc = new char[Runtime.getRuntime().freeMemory() > 1000000L ? READ_CHUNK_SIZE : 64];
    protected int pcStart;
    protected int pcEnd;

    // parsing state
    // protected boolean needsMore;
    // protected boolean seenMarkup;
    protected boolean usePC;

    protected boolean seenStartTag;
    protected boolean seenEndTag;
    protected boolean pastEndTag;
    protected boolean seenAmpersand;
    protected boolean seenMarkup;
    protected boolean seenDocdecl;

    // transient variable set during each call to next/Token()
    protected boolean tokenize;
    protected String text;
    protected String entityRefName;

    protected String xmlDeclVersion;
    protected Boolean xmlDeclStandalone;
    protected String xmlDeclContent;

    protected void reset() {
        // System.out.println("reset() called");
        location = null;
        startLineNumber = 1;
        lineNumber = 1;
        columnNumber = 0;
        seenRoot = false;
        reachedEnd = false;
        eventType = START_DOCUMENT;
        emptyElementTag = false;

        depth = 0;

        attributeCount = 0;

        namespaceEnd = 0;

        entityEnd = 0;

        reader = null;
        inputEncoding = null;

        preventBufferCompaction = false;
        bufAbsoluteStart = 0;
        bufEnd = bufStart = 0;
        pos = posStart = posEnd = 0;

        pcEnd = pcStart = 0;

        usePC = false;

        seenStartTag = false;
        seenEndTag = false;
        pastEndTag = false;
        seenAmpersand = false;
        seenMarkup = false;
        seenDocdecl = false;

        xmlDeclVersion = null;
        xmlDeclStandalone = null;
        xmlDeclContent = null;

        resetStringCache();
    }

    public MXParser() {
    }

    /**
     * Method setFeature
     *
     * @param  name                   a String
     * @param  state                  a boolean
     * @throws XmlPullParserException
     */
    public void setFeature(String name, boolean state) throws XmlPullParserException {
        if (name == null)
            throw new IllegalArgumentException("feature name should not be null");
        if (FEATURE_PROCESS_NAMESPACES.equals(name)) {
            if (eventType != START_DOCUMENT)
                throw new XmlPullParserException("namespace processing feature can only be changed before parsing", this, null);
            processNamespaces = state;
            // } else if(FEATURE_REPORT_NAMESPACE_ATTRIBUTES.equals(name)) {
            // if(type != START_DOCUMENT) throw new XmlPullParserException(
            // "namespace reporting feature can only be changed before parsing",
            // this, null);
            // reportNsAttribs = state;
        } else if (FEATURE_NAMES_INTERNED.equals(name)) {
            if (state) {
                throw new XmlPullParserException("interning names in this implementation is not supported");
            }
        } else if (FEATURE_PROCESS_DOCDECL.equals(name)) {
            if (state) {
                throw new XmlPullParserException("processing DOCDECL is not supported");
            }
            // } else if(REPORT_DOCDECL.equals(name)) {
            // paramNotifyDoctype = state;
        } else if (FEATURE_XML_ROUNDTRIP.equals(name)) {
            // if(state == false) {
            // throw new XmlPullParserException(
            // "roundtrip feature can not be switched off");
            // }
            roundtripSupported = state;
        } else {
            throw new XmlPullParserException("unsupported feature " + name);
        }
    }

    /**
     * Unknown properties are <strong>always</strong> returned as false
     */
    public boolean getFeature(String name) {
        if (name == null)
            throw new IllegalArgumentException("feature name should not be null");
        if (FEATURE_PROCESS_NAMESPACES.equals(name)) {
            return processNamespaces;
            // } else if(FEATURE_REPORT_NAMESPACE_ATTRIBUTES.equals(name)) {
            // return reportNsAttribs;
        } else if (FEATURE_NAMES_INTERNED.equals(name)) {
            return false;
        } else if (FEATURE_PROCESS_DOCDECL.equals(name)) {
            return false;
            // } else if(REPORT_DOCDECL.equals(name)) {
            // return paramNotifyDoctype;
        } else if (FEATURE_XML_ROUNDTRIP.equals(name)) {
            // return true;
            return roundtripSupported;
        }
        return false;
    }

    public void setProperty(String name, Object value) throws XmlPullParserException {
        if (PROPERTY_LOCATION.equals(name)) {
            location = (String) value;
        } else {
            throw new XmlPullParserException("unsupported property: '" + name + "'");
        }
    }

    public Object getProperty(String name) {
        if (name == null)
            throw new IllegalArgumentException("property name should not be null");
        if (PROPERTY_XMLDECL_VERSION.equals(name)) {
            return xmlDeclVersion;
        } else if (PROPERTY_XMLDECL_STANDALONE.equals(name)) {
            return xmlDeclStandalone;
        } else if (PROPERTY_XMLDECL_CONTENT.equals(name)) {
            return xmlDeclContent;
        } else if (PROPERTY_LOCATION.equals(name)) {
            return location;
        }
        return null;
    }

    public void setInput(Reader in) throws XmlPullParserException {
        if (in == null) {
            throw new IllegalArgumentException("input reader can not be null");
        }
        reset();
        reader = in;
    }

    public void setInput(InputStream inputStream, String inputEncoding) throws XmlPullParserException {
        if (inputStream == null) {
            throw new IllegalArgumentException("input stream can not be null");
        }
        reset();
        try {
            if (inputEncoding != null) {
                this.reader = new InputStreamReader(inputStream, inputEncoding);
                this.inputEncoding = inputEncoding;
            } else {
                XmlStreamReader xr = new XmlStreamReader(inputStream);
                this.reader = xr;
                this.inputEncoding = xr.getEncoding();
            }
        } catch (IOException une) {
            throw new XmlPullParserException("could not create reader for encoding " + inputEncoding + " : " + une, this, une);
        }
    }

    public String getInputEncoding() {
        return inputEncoding;
    }

    public void defineEntityReplacementText(String entityName, String replacementText) throws XmlPullParserException {
        // throw new XmlPullParserException("not allowed");

        // protected char[] entityReplacement[];
        ensureEntityCapacity();

        // this is to make sure that if interning works we will take advantage
        // of it ...
        this.entityName[entityEnd] = newString(entityName.toCharArray(), 0, entityName.length());
        entityNameBuf[entityEnd] = entityName.toCharArray();

        entityReplacement[entityEnd] = replacementText;
        entityReplacementBuf[entityEnd] = replacementText.toCharArray();
        if (!allStringsInterned) {
            entityNameHash[entityEnd] = fastHash(entityNameBuf[entityEnd], 0, entityNameBuf[entityEnd].length);
        }
        ++entityEnd;
        // TODO disallow < or & in entity replacement text (or ]]>???)
        // TOOD keepEntityNormalizedForAttributeValue cached as well ...
    }

    public int getNamespaceCount(int depth) throws XmlPullParserException {
        if (!processNamespaces || depth == 0) {
            return 0;
        }
        // int maxDepth = eventType == END_TAG ? this.depth + 1 : this.depth;
        // if(depth < 0 || depth > maxDepth) throw new IllegalArgumentException(
        if (depth < 0 || depth > this.depth)
            throw new IllegalArgumentException("allowed namespace depth 0.." + this.depth + " not " + depth);
        return elNamespaceCount[depth];
    }

    public String getNamespacePrefix(int pos) throws XmlPullParserException {

        // int end = eventType == END_TAG ? elNamespaceCount[ depth + 1 ] :
        // namespaceEnd;
        // if(pos < end) {
        if (pos < namespaceEnd) {
            return namespacePrefix[pos];
        } else {
            throw new XmlPullParserException("position " + pos + " exceeded number of available namespaces " + namespaceEnd);
        }
    }

    public String getNamespaceUri(int pos) throws XmlPullParserException {
        // int end = eventType == END_TAG ? elNamespaceCount[ depth + 1 ] :
        // namespaceEnd;
        // if(pos < end) {
        if (pos < namespaceEnd) {
            return namespaceUri[pos];
        } else {
            throw new XmlPullParserException("position " + pos + " exceeded number of available namespaces " + namespaceEnd);
        }
    }

    public String getNamespace(String prefix)
    // throws XmlPullParserException
    {
        // int count = namespaceCount[ depth ];
        if (prefix != null) {
            for (int i = namespaceEnd - 1; i >= 0; i--) {
                if (prefix.equals(namespacePrefix[i])) {
                    return namespaceUri[i];
                }
            }
            if ("xml".equals(prefix)) {
                return XML_URI;
            } else if ("xmlns".equals(prefix)) {
                return XMLNS_URI;
            }
        } else {
            for (int i = namespaceEnd - 1; i >= 0; i--) {
                if (namespacePrefix[i] == null) { // "") { //null ) { //TODO
                                                 // check FIXME Alek
                    return namespaceUri[i];
                }
            }

        }
        return null;
    }

    public int getDepth() {
        return depth;
    }

    private static int findFragment(int bufMinPos, char[] b, int start, int end) {
        // System.err.println("bufStart="+bufStart+" b="+printable(new String(b,
        // start, end - start))+" start="+start+" end="+end);
        if (start < bufMinPos) {
            start = bufMinPos;
            if (start > end)
                start = end;
            return start;
        }
        if (end - start > 65) {
            start = end - 10; // try to find good location
        }
        int i = start + 1;
        while (--i > bufMinPos) {
            if ((end - i) > 65)
                break;
            final char c = b[i];
            if (c == '<' && (start - i) > 10)
                break;
        }
        return i;
    }

    /**
     * Return string describing current position of parsers as text 'STATE [seen %s...] @line:column'.
     */
    public String getPositionDescription() {
        String fragment = null;
        if (posStart <= pos) {
            final int start = findFragment(0, buf, posStart, pos);
            // System.err.println("start="+start);
            if (start < pos) {
                fragment = new String(buf, start, pos - start);
            }
            if (bufAbsoluteStart > 0 || start > 0)
                fragment = "..." + fragment;
        }
        // return " at line "+tokenizerPosRow
        // +" and column "+(tokenizerPosCol-1)
        // +(fragment != null ? " seen "+printable(fragment)+"..." : "");
        return " " + TYPES[eventType] + (fragment != null ? " seen " + printable(fragment) + "..." : "") + " "
               + (location != null ? location : "") + "@" + getLineNumber() + ":"
               + getColumnNumber();
    }

    public int getStartLineNumber() {
        return startLineNumber;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public int getColumnNumber() {
        return columnNumber;
    }

    public boolean isWhitespace() throws XmlPullParserException {
        if (eventType == TEXT || eventType == CDSECT) {
            if (usePC) {
                for (int i = pcStart; i < pcEnd; i++) {
                    if (!isS(pc[i]))
                        return false;
                }
                return true;
            } else {
                for (int i = posStart; i < posEnd; i++) {
                    if (!isS(buf[i]))
                        return false;
                }
                return true;
            }
        } else if (eventType == IGNORABLE_WHITESPACE) {
            return true;
        }
        throw new XmlPullParserException("no content available to check for white spaces");
    }

    public String getText() {
        if (eventType == START_DOCUMENT || eventType == END_DOCUMENT) {
            // throw new XmlPullParserException("no content available to read");
            // if(roundtripSupported) {
            // text = new String(buf, posStart, posEnd - posStart);
            // } else {
            return null;
            // }
        } else if (eventType == ENTITY_REF) {
            return text;
        }
        if (text == null) {
            if (!usePC || eventType == START_TAG || eventType == END_TAG) {
                text = new String(buf, posStart, posEnd - posStart);
            } else {
                text = new String(pc, pcStart, pcEnd - pcStart);
            }
        }
        return text;
    }

    public char[] getTextCharacters(int[] holderForStartAndLength) {
        if (eventType == TEXT) {
            if (usePC) {
                holderForStartAndLength[0] = pcStart;
                holderForStartAndLength[1] = pcEnd - pcStart;
                return pc;
            } else {
                holderForStartAndLength[0] = posStart;
                holderForStartAndLength[1] = posEnd - posStart;
                return buf;

            }
        } else if (eventType == START_TAG || eventType == END_TAG || eventType == CDSECT || eventType == COMMENT
                || eventType == ENTITY_REF || eventType == PROCESSING_INSTRUCTION
                || eventType == IGNORABLE_WHITESPACE || eventType == DOCDECL) {
            holderForStartAndLength[0] = posStart;
            holderForStartAndLength[1] = posEnd - posStart;
            return buf;
        } else if (eventType == START_DOCUMENT || eventType == END_DOCUMENT) {
            // throw new XmlPullParserException("no content available to read");
            holderForStartAndLength[0] = holderForStartAndLength[1] = -1;
            return null;
        } else {
            throw new IllegalArgumentException("unknown text eventType: " + eventType);
        }
        // String s = getText();
        // char[] cb = null;
        // if(s!= null) {
        // cb = s.toCharArray();
        // holderForStartAndLength[0] = 0;
        // holderForStartAndLength[1] = s.length();
        // } else {
        // }
        // return cb;
    }

    public String getNamespace() {
        if (eventType == START_TAG) {
            // return processNamespaces ? elUri[ depth - 1 ] : NO_NAMESPACE;
            return processNamespaces ? elUri[depth] : NO_NAMESPACE;
        } else if (eventType == END_TAG) {
            return processNamespaces ? elUri[depth] : NO_NAMESPACE;
        }
        return null;
        // String prefix = elPrefix[ maxDepth ];
        // if(prefix != null) {
        // for( int i = namespaceEnd -1; i >= 0; i--) {
        // if( prefix.equals( namespacePrefix[ i ] ) ) {
        // return namespaceUri[ i ];
        // }
        // }
        // } else {
        // for( int i = namespaceEnd -1; i >= 0; i--) {
        // if( namespacePrefix[ i ] == null ) {
        // return namespaceUri[ i ];
        // }
        // }
        //
        // }
        // return "";
    }

    public String getName() {
        if (eventType == START_TAG) {
            // return elName[ depth - 1 ] ;
            return elName[depth];
        } else if (eventType == END_TAG) {
            return elName[depth];
        } else if (eventType == ENTITY_REF) {
            if (entityRefName == null) {
                entityRefName = newString(buf, posStart, posEnd - posStart);
            }
            return entityRefName;
        } else {
            return null;
        }
    }

    @Override
    public String[] getNames() {
        return elName;
    }

    public String getPrefix() {
        if (eventType == START_TAG) {
            // return elPrefix[ depth - 1 ] ;
            return elPrefix[depth];
        } else if (eventType == END_TAG) {
            return elPrefix[depth];
        }
        return null;
        // if(eventType != START_TAG && eventType != END_TAG) return null;
        // int maxDepth = eventType == END_TAG ? depth : depth - 1;
        // return elPrefix[ maxDepth ];
    }

    public boolean isEmptyElementTag() throws XmlPullParserException {
        if (eventType != START_TAG)
            throw new XmlPullParserException("parser must be on START_TAG to check for empty element", this, null);
        return emptyElementTag;
    }

    public int getAttributeCount() {
        if (eventType != START_TAG)
            return -1;
        return attributeCount;
    }

    public String getAttributeNamespace(int index) {
        if (eventType != START_TAG)
            throw new IndexOutOfBoundsException("only START_TAG can have attributes");
        if (!processNamespaces)
            return NO_NAMESPACE;
        if (index < 0 || index >= attributeCount)
            throw new IndexOutOfBoundsException("attribute position must be 0.." + (attributeCount - 1) + " and not " + index);
        return attributeUri[index];
    }

    public String getAttributeName(int index) {
        if (eventType != START_TAG)
            throw new IndexOutOfBoundsException("only START_TAG can have attributes");
        if (index < 0 || index >= attributeCount)
            throw new IndexOutOfBoundsException("attribute position must be 0.." + (attributeCount - 1) + " and not " + index);
        return attributeName[index];
    }

    public String getAttributePrefix(int index) {
        if (eventType != START_TAG)
            throw new IndexOutOfBoundsException("only START_TAG can have attributes");
        if (!processNamespaces)
            return null;
        if (index < 0 || index >= attributeCount)
            throw new IndexOutOfBoundsException("attribute position must be 0.." + (attributeCount - 1) + " and not " + index);
        return attributePrefix[index];
    }

    public String getAttributeType(int index) {
        if (eventType != START_TAG)
            throw new IndexOutOfBoundsException("only START_TAG can have attributes");
        if (index < 0 || index >= attributeCount)
            throw new IndexOutOfBoundsException("attribute position must be 0.." + (attributeCount - 1) + " and not " + index);
        return "CDATA";
    }

    public boolean isAttributeDefault(int index) {
        if (eventType != START_TAG)
            throw new IndexOutOfBoundsException("only START_TAG can have attributes");
        if (index < 0 || index >= attributeCount)
            throw new IndexOutOfBoundsException("attribute position must be 0.." + (attributeCount - 1) + " and not " + index);
        return false;
    }

    public String getAttributeValue(int index) {
        if (eventType != START_TAG)
            throw new IndexOutOfBoundsException("only START_TAG can have attributes");
        if (index < 0 || index >= attributeCount)
            throw new IndexOutOfBoundsException("attribute position must be 0.." + (attributeCount - 1) + " and not " + index);
        return attributeValue[index];
    }

    public String getAttributeValue(String namespace, String name) {
        if (eventType != START_TAG)
            throw new IndexOutOfBoundsException("only START_TAG can have attributes" + getPositionDescription());
        if (name == null) {
            throw new IllegalArgumentException("attribute name can not be null");
        }
        // TODO make check if namespace is interned!!! etc. for names!!!
        if (processNamespaces) {
            if (namespace == null) {
                namespace = "";
            }

            for (int i = 0; i < attributeCount; ++i) {
                if ((namespace == attributeUri[i] || namespace.equals(attributeUri[i]))
                        // (namespace != null && namespace.equals(attributeUri[ i
                        // ]))
                        // taking advantage of String.intern()
                        && name.equals(attributeName[i])) {
                    return attributeValue[i];
                }
            }
        } else {
            if (namespace != null && namespace.isEmpty()) {
                namespace = null;
            }
            if (namespace != null)
                throw new IllegalArgumentException("when namespaces processing is disabled attribute namespace must be null");
            for (int i = 0; i < attributeCount; ++i) {
                if (name.equals(attributeName[i])) {
                    return attributeValue[i];
                }
            }
        }
        return null;
    }

    public int getEventType() throws XmlPullParserException {
        return eventType;
    }

    public void require(int type, String namespace, String name) throws XmlPullParserException, IOException {
        if (!processNamespaces && namespace != null) {
            throw new XmlPullParserException(
                    "processing namespaces must be enabled on parser (or factory)"
                                             + " to have possible namespaces declared on elements"
                                             + (" (position:" + getPositionDescription()) + ")");
        }
        if (type != getEventType() || (namespace != null && !namespace.equals(getNamespace()))
                || (name != null && !name.equals(getName()))) {
            throw new XmlPullParserException(
                    "expected event " + TYPES[type] + (name != null ? " with name '" + name + "'" : "")
                                             + (namespace != null && name != null ? " and" : "")
                                             + (namespace != null ? " with namespace '" + namespace + "'" : "") + " but got"
                                             + (type != getEventType() ? " " + TYPES[getEventType()] : "")
                                             + (name != null && getName() != null && !name.equals(getName())
                                                     ? " name '" + getName() + "'" : "")
                                             + (namespace != null && name != null && getName() != null
                                                     && !name.equals(getName()) && getNamespace() != null
                                                     && !namespace.equals(getNamespace()) ? " and" : "")
                                             + (namespace != null && getNamespace() != null && !namespace.equals(getNamespace())
                                                     ? " namespace '" + getNamespace() + "'" : "")
                                             + (" (position:" + getPositionDescription()) + ")");
        }
    }

    /**
     * Skip sub tree that is currently parser positioned on. <br>
     * NOTE: parser must be on START_TAG and when function returns parser will be positioned on corresponding END_TAG
     */
    public void skipSubTree() throws XmlPullParserException, IOException {
        require(START_TAG, null, null);
        int level = 1;
        while (level > 0) {
            int eventType = next();
            if (eventType == END_TAG) {
                --level;
            } else if (eventType == START_TAG) {
                ++level;
            }
        }
    }

    // public String readText() throws XmlPullParserException, IOException
    // {
    // if (getEventType() != TEXT) return "";
    // String result = getText();
    // next();
    // return result;
    // }

    public String nextText() throws XmlPullParserException, IOException {
        // String result = null;
        // boolean onStartTag = false;
        // if(eventType == START_TAG) {
        // onStartTag = true;
        // next();
        // }
        // if(eventType == TEXT) {
        // result = getText();
        // next();
        // } else if(onStartTag && eventType == END_TAG) {
        // result = "";
        // } else {
        // throw new XmlPullParserException(
        // "parser must be on START_TAG or TEXT to read text", this, null);
        // }
        // if(eventType != END_TAG) {
        // throw new XmlPullParserException(
        // "event TEXT it must be immediately followed by END_TAG", this, null);
        // }
        // return result;
        if (getEventType() != START_TAG) {
            throw new XmlPullParserException("parser must be on START_TAG to read next text", this, null);
        }
        int eventType = next();
        if (eventType == TEXT) {
            final String result = getText();
            eventType = next();
            if (eventType != END_TAG) {
                throw new XmlPullParserException(
                        "TEXT must be immediately followed by END_TAG and not " + TYPES[getEventType()], this, null);
            }
            return result;
        } else if (eventType == END_TAG) {
            return "";
        } else {
            throw new XmlPullParserException("parser must be on START_TAG or TEXT to read text", this, null);
        }
    }

    public int nextTag() throws XmlPullParserException, IOException {
        next();
        if (eventType == TEXT && isWhitespace()) { // skip whitespace
            next();
        }
        if (eventType != START_TAG && eventType != END_TAG) {
            throw new XmlPullParserException("expected START_TAG or END_TAG not " + TYPES[getEventType()], this, null);
        }
        return eventType;
    }

    public int next() throws XmlPullParserException, IOException {
        tokenize = false;
        return nextImpl();
    }

    public int nextToken() throws XmlPullParserException, IOException {
        tokenize = true;
        return nextImpl();
    }

    protected int nextImpl() throws XmlPullParserException, IOException {
        text = null;
        pcEnd = pcStart = 0;
        usePC = false;
        bufStart = posEnd;
        if (pastEndTag) {
            pastEndTag = false;
            --depth;
            namespaceEnd = elNamespaceCount[depth]; // less namespaces available
        }
        if (emptyElementTag) {
            emptyElementTag = false;
            pastEndTag = true;
            return eventType = END_TAG;
        }

        // [1] document ::= prolog element Misc*
        if (depth > 0) {

            if (seenStartTag) {
                seenStartTag = false;
                return eventType = parseStartTag();
            }
            if (seenEndTag) {
                seenEndTag = false;
                return eventType = parseEndTag();
            }

            // ASSUMPTION: we are _on_ first character of content or markup!!!!
            // [43] content ::= CharData? ((element | Reference | CDSect | PI |
            // Comment) CharData?)*
            char ch;
            if (seenMarkup) { // we have read ahead ...
                seenMarkup = false;
                ch = '<';
            } else if (seenAmpersand) {
                seenAmpersand = false;
                ch = '&';
            } else {
                ch = more();
            }
            posStart = pos - 1; // VERY IMPORTANT: this is correct start of
                               // event!!!

            // when true there is some potential event TEXT to return - keep
            // gathering
            boolean hadCharData = false;

            // when true TEXT data is not continual (like <![CDATA[text]]>) and
            // requires PC merging
            boolean needsMerging = false;

            MAIN_LOOP: while (true) {
                // work on MARKUP
                if (ch == '<') {
                    if (hadCharData) {
                        // posEnd = pos - 1;
                        if (tokenize) {
                            seenMarkup = true;
                            return eventType = TEXT;
                        }
                    }
                    ch = more();
                    if (ch == '/') {
                        if (!tokenize && hadCharData) {
                            seenEndTag = true;
                            // posEnd = pos - 2;
                            return eventType = TEXT;
                        }
                        return eventType = parseEndTag();
                    } else if (ch == '!') {
                        ch = more();
                        if (ch == '-') {
                            // note: if(tokenize == false) posStart/End is NOT
                            // changed!!!!
                            parseComment();
                            if (tokenize)
                                return eventType = COMMENT;
                            if (!usePC && hadCharData) {
                                needsMerging = true;
                            } else {
                                posStart = pos; // completely ignore comment
                            }
                        } else if (ch == '[') {
                            // posEnd = pos - 3;
                            // must remember previous posStart/End as it merges
                            // with content of CDATA
                            // int oldStart = posStart + bufAbsoluteStart;
                            // int oldEnd = posEnd + bufAbsoluteStart;
                            parseCDSect(hadCharData);
                            if (tokenize)
                                return eventType = CDSECT;
                            final int cdStart = posStart;
                            final int cdEnd = posEnd;
                            final int cdLen = cdEnd - cdStart;

                            if (cdLen > 0) { // was there anything inside CDATA
                                            // section?
                                hadCharData = true;
                                if (!usePC) {
                                    needsMerging = true;
                                }
                            }

                            // posStart = oldStart;
                            // posEnd = oldEnd;
                            // if(cdLen > 0) { // was there anything inside
                            // CDATA section?
                            // if(hadCharData) {
                            // // do merging if there was anything in CDSect!!!!
                            // // if(!usePC) {
                            // // // posEnd is correct already!!!
                            // // if(posEnd > posStart) {
                            // // joinPC();
                            // // } else {
                            // // usePC = true;
                            // // pcStart = pcEnd = 0;
                            // // }
                            // // }
                            // // if(pcEnd + cdLen >= pc.length) ensurePC(pcEnd
                            // + cdLen);
                            // // // copy [cdStart..cdEnd) into PC
                            // // System.arraycopy(buf, cdStart, pc, pcEnd,
                            // cdLen);
                            // // pcEnd += cdLen;
                            // if(!usePC) {
                            // needsMerging = true;
                            // posStart = cdStart;
                            // posEnd = cdEnd;
                            // }
                            // } else {
                            // if(!usePC) {
                            // needsMerging = true;
                            // posStart = cdStart;
                            // posEnd = cdEnd;
                            // hadCharData = true;
                            // }
                            // }
                            // //hadCharData = true;
                            // } else {
                            // if( !usePC && hadCharData ) {
                            // needsMerging = true;
                            // }
                            // }
                        } else {
                            throw new XmlPullParserException("unexpected character in markup " + printable(ch), this, null);
                        }
                    } else if (ch == '?') {
                        parsePI();
                        if (tokenize)
                            return eventType = PROCESSING_INSTRUCTION;
                        if (!usePC && hadCharData) {
                            needsMerging = true;
                        } else {
                            posStart = pos; // completely ignore PI
                        }

                    } else if (isNameStartChar(ch)) {
                        if (!tokenize && hadCharData) {
                            seenStartTag = true;
                            // posEnd = pos - 2;
                            return eventType = TEXT;
                        }
                        return eventType = parseStartTag();
                    } else {
                        throw new XmlPullParserException("unexpected character in markup " + printable(ch), this, null);
                    }
                    // do content compaction if it makes sense!!!!

                } else if (ch == '&') {
                    // work on ENTITTY
                    // posEnd = pos - 1;
                    if (tokenize && hadCharData) {
                        seenAmpersand = true;
                        return eventType = TEXT;
                    }
                    final int oldStart = posStart + bufAbsoluteStart;
                    final int oldEnd = posEnd + bufAbsoluteStart;
                    final char[] resolvedEntity = parseEntityRef();
                    if (tokenize)
                        return eventType = ENTITY_REF;
                    // check if replacement text can be resolved !!!
                    if (resolvedEntity == null) {
                        if (entityRefName == null) {
                            entityRefName = newString(buf, posStart, posEnd - posStart);
                        }
                        throw new XmlPullParserException(
                                "could not resolve entity named '" + printable(entityRefName) + "'", this, null);
                    }
                    // int entStart = posStart;
                    // int entEnd = posEnd;
                    posStart = oldStart - bufAbsoluteStart;
                    posEnd = oldEnd - bufAbsoluteStart;
                    if (!usePC) {
                        if (hadCharData) {
                            joinPC(); // posEnd is already set correctly!!!
                            needsMerging = false;
                        } else {
                            usePC = true;
                            pcStart = pcEnd = 0;
                        }
                    }
                    // assert usePC == true;
                    // write into PC replacement text - do merge for replacement
                    // text!!!!
                    for (int i = 0; i < resolvedEntity.length; i++) {
                        if (pcEnd >= pc.length)
                            ensurePC(pcEnd);
                        pc[pcEnd++] = resolvedEntity[i];

                    }
                    hadCharData = true;
                    // assert needsMerging == false;
                } else {

                    if (needsMerging) {
                        // assert usePC == false;
                        joinPC(); // posEnd is already set correctly!!!
                        // posStart = pos - 1;
                        needsMerging = false;
                    }

                    // no MARKUP not ENTITIES so work on character data ...

                    // [14] CharData ::= [^<&]* - ([^<&]* ']]>' [^<&]*)

                    hadCharData = true;

                    boolean normalizedCR = false;
                    final boolean normalizeInput = !tokenize || !roundtripSupported;
                    // use loop locality here!!!!
                    boolean seenBracket = false;
                    boolean seenBracketBracket = false;
                    do {

                        // check that ]]> does not show in
                        if (ch == ']') {
                            if (seenBracket) {
                                seenBracketBracket = true;
                            } else {
                                seenBracket = true;
                            }
                        } else if (seenBracketBracket && ch == '>') {
                            throw new XmlPullParserException("characters ]]> are not allowed in content", this, null);
                        } else {
                            if (seenBracket) {
                                seenBracketBracket = seenBracket = false;
                            }
                            // assert seenTwoBrackets == seenBracket == false;
                        }
                        if (normalizeInput) {
                            // deal with normalization issues ...
                            if (ch == '\r') {
                                normalizedCR = true;
                                posEnd = pos - 1;
                                // posEnd is already is set
                                if (!usePC) {
                                    if (posEnd > posStart) {
                                        joinPC();
                                    } else {
                                        usePC = true;
                                        pcStart = pcEnd = 0;
                                    }
                                }
                                // assert usePC == true;
                                if (pcEnd >= pc.length)
                                    ensurePC(pcEnd);
                                pc[pcEnd++] = '\n';
                            } else if (ch == '\n') {
                                // if(!usePC) { joinPC(); } else { if(pcEnd >=
                                // pc.length) ensurePC(); }
                                if (!normalizedCR && usePC) {
                                    if (pcEnd >= pc.length)
                                        ensurePC(pcEnd);
                                    pc[pcEnd++] = '\n';
                                }
                                normalizedCR = false;
                            } else {
                                if (usePC) {
                                    if (pcEnd >= pc.length)
                                        ensurePC(pcEnd);
                                    pc[pcEnd++] = ch;
                                }
                                normalizedCR = false;
                            }
                        }

                        ch = more();
                    } while (ch != '<' && ch != '&');
                    posEnd = pos - 1;
                    continue MAIN_LOOP; // skip ch = more() from below - we are
                                       // alreayd ahead ...
                }
                ch = more();
            } // endless while(true)
        } else {
            if (seenRoot) {
                return parseEpilog();
            } else {
                return parseProlog();
            }
        }
    }

    protected int parseProlog() throws XmlPullParserException, IOException {
        // [2] prolog: ::= XMLDecl? Misc* (doctypedecl Misc*)? and look for [39]
        // element

        char ch;
        if (seenMarkup) {
            ch = buf[pos - 1];
        } else {
            ch = more();
        }

        if (eventType == START_DOCUMENT) {
            // bootstrap parsing with getting first character input!
            // deal with BOM
            // detect BOM and drop it (Unicode int Order Mark)
            if (ch == '\uFFFE') {
                throw new XmlPullParserException(
                        "first character in input was UNICODE noncharacter (0xFFFE)" + "- input requires int swapping", this,
                        null);
            }
            if (ch == '\uFEFF') {
                // skipping UNICODE int Order Mark (so called BOM)
                ch = more();
            }
        }
        seenMarkup = false;
        boolean gotS = false;
        posStart = pos - 1;
        final boolean normalizeIgnorableWS = tokenize == true && roundtripSupported == false;
        boolean normalizedCR = false;
        while (true) {
            // deal with Misc
            // [27] Misc ::= Comment | PI | S
            // deal with docdecl --> mark it!
            // else parseStartTag seen <[^/]
            if (ch == '<') {
                if (gotS && tokenize) {
                    posEnd = pos - 1;
                    seenMarkup = true;
                    return eventType = IGNORABLE_WHITESPACE;
                }
                ch = more();
                if (ch == '?') {
                    // check if it is 'xml'
                    // deal with XMLDecl
                    if (parsePI()) { // make sure to skip XMLDecl
                        if (tokenize) {
                            return eventType = PROCESSING_INSTRUCTION;
                        }
                    } else {
                        // skip over - continue tokenizing
                        posStart = pos;
                        gotS = false;
                    }

                } else if (ch == '!') {
                    ch = more();
                    if (ch == 'D') {
                        if (seenDocdecl) {
                            throw new XmlPullParserException("only one docdecl allowed in XML document", this, null);
                        }
                        seenDocdecl = true;
                        parseDocdecl();
                        if (tokenize)
                            return eventType = DOCDECL;
                    } else if (ch == '-') {
                        parseComment();
                        if (tokenize)
                            return eventType = COMMENT;
                    } else {
                        throw new XmlPullParserException("unexpected markup <!" + printable(ch), this, null);
                    }
                } else if (ch == '/') {
                    throw new XmlPullParserException("expected start tag name and not " + printable(ch), this, null);
                } else if (isNameStartChar(ch)) {
                    seenRoot = true;
                    return parseStartTag();
                } else {
                    throw new XmlPullParserException("expected start tag name and not " + printable(ch), this, null);
                }
            } else if (isS(ch)) {
                gotS = true;
                if (normalizeIgnorableWS) {
                    if (ch == '\r') {
                        normalizedCR = true;
                        // posEnd = pos -1;
                        // joinPC();
                        // posEnd is already is set
                        if (!usePC) {
                            posEnd = pos - 1;
                            if (posEnd > posStart) {
                                joinPC();
                            } else {
                                usePC = true;
                                pcStart = pcEnd = 0;
                            }
                        }
                        // assert usePC == true;
                        if (pcEnd >= pc.length)
                            ensurePC(pcEnd);
                        pc[pcEnd++] = '\n';
                    } else if (ch == '\n') {
                        if (!normalizedCR && usePC) {
                            if (pcEnd >= pc.length)
                                ensurePC(pcEnd);
                            pc[pcEnd++] = '\n';
                        }
                        normalizedCR = false;
                    } else {
                        if (usePC) {
                            if (pcEnd >= pc.length)
                                ensurePC(pcEnd);
                            pc[pcEnd++] = ch;
                        }
                        normalizedCR = false;
                    }
                }
            } else {
                throw new XmlPullParserException(
                        "only whitespace content allowed before start tag and not " + printable(ch), this, null);
            }
            ch = more();
        }
    }

    protected int parseEpilog() throws XmlPullParserException, IOException {
        if (eventType == END_DOCUMENT) {
            throw new XmlPullParserException("already reached end of XML input", this, null);
        }
        if (reachedEnd) {
            return eventType = END_DOCUMENT;
        }
        boolean gotS = false;
        final boolean normalizeIgnorableWS = tokenize && !roundtripSupported;
        boolean normalizedCR = false;
        try {
            // epilog: Misc*
            char ch;
            if (seenMarkup) {
                ch = buf[pos - 1];
            } else {
                ch = more();
            }
            seenMarkup = false;
            posStart = pos - 1;
            if (!reachedEnd) {
                while (true) {
                    // deal with Misc
                    // [27] Misc ::= Comment | PI | S
                    if (ch == '<') {
                        if (gotS && tokenize) {
                            posEnd = pos - 1;
                            seenMarkup = true;
                            return eventType = IGNORABLE_WHITESPACE;
                        }
                        ch = more();
                        if (reachedEnd) {
                            break;
                        }
                        if (ch == '?') {
                            // check if it is 'xml'
                            // deal with XMLDecl
                            parsePI();
                            if (tokenize)
                                return eventType = PROCESSING_INSTRUCTION;

                        } else if (ch == '!') {
                            ch = more();
                            if (reachedEnd) {
                                break;
                            }
                            if (ch == 'D') {
                                parseDocdecl(); // FIXME
                                if (tokenize)
                                    return eventType = DOCDECL;
                            } else if (ch == '-') {
                                parseComment();
                                if (tokenize)
                                    return eventType = COMMENT;
                            } else {
                                throw new XmlPullParserException("unexpected markup <!" + printable(ch), this, null);
                            }
                        } else if (ch == '/') {
                            throw new XmlPullParserException(
                                    "end tag not allowed in epilog but got " + printable(ch), this, null);
                        } else if (isNameStartChar(ch)) {
                            throw new XmlPullParserException(
                                    "start tag not allowed in epilog but got " + printable(ch), this, null);
                        } else {
                            throw new XmlPullParserException(
                                    "in epilog expected ignorable content and not " + printable(ch), this, null);
                        }
                    } else if (isS(ch)) {
                        gotS = true;
                        if (normalizeIgnorableWS) {
                            if (ch == '\r') {
                                normalizedCR = true;
                                // posEnd = pos -1;
                                // joinPC();
                                // posEnd is alreadys set
                                if (!usePC) {
                                    posEnd = pos - 1;
                                    if (posEnd > posStart) {
                                        joinPC();
                                    } else {
                                        usePC = true;
                                        pcStart = pcEnd = 0;
                                    }
                                }
                                // assert usePC == true;
                                if (pcEnd >= pc.length)
                                    ensurePC(pcEnd);
                                pc[pcEnd++] = '\n';
                            } else if (ch == '\n') {
                                if (!normalizedCR && usePC) {
                                    if (pcEnd >= pc.length)
                                        ensurePC(pcEnd);
                                    pc[pcEnd++] = '\n';
                                }
                                normalizedCR = false;
                            } else {
                                if (usePC) {
                                    if (pcEnd >= pc.length)
                                        ensurePC(pcEnd);
                                    pc[pcEnd++] = ch;
                                }
                                normalizedCR = false;
                            }
                        }
                    } else {
                        throw new XmlPullParserException(
                                "in epilog non whitespace content is not allowed but got " + printable(ch), this, null);
                    }
                    ch = more();
                    if (reachedEnd) {
                        break;
                    }

                }
            }

            // throw Exception("unexpected content in epilog
            // catch EOFException return END_DOCUEMENT
            // try {
        } catch (EOFException ex) {
            reachedEnd = true;
        }
        if (reachedEnd) {
            if (tokenize && gotS) {
                posEnd = pos; // well - this is LAST available character pos
                return eventType = IGNORABLE_WHITESPACE;
            }
            return eventType = END_DOCUMENT;
        } else {
            throw new XmlPullParserException("internal error in parseEpilog");
        }
    }

    public int parseEndTag() throws XmlPullParserException, IOException {
        // ASSUMPTION ch is past "</"
        // [42] ETag ::= '</' Name S? '>'
        char ch = more();
        if (!isNameStartChar(ch)) {
            throw new XmlPullParserException("expected name start and not " + printable(ch), this, null);
        }
        posStart = pos - 3;
        final int nameStart = pos - 1 + bufAbsoluteStart;
        do {
            ch = more();
        } while (isNameChar(ch));

        // now we go one level down -- do checks
        // --depth; //FIXME

        // check that end tag name is the same as start tag
        // String name = new String(buf, nameStart - bufAbsoluteStart,
        // (pos - 1) - (nameStart - bufAbsoluteStart));
        // int last = pos - 1;
        int off = nameStart - bufAbsoluteStart;
        // final int len = last - off;
        final int len = (pos - 1) - off;
        final char[] cbuf = elRawName[depth];
        if (elRawNameEnd[depth] != len) {
            // construct strings for exception
            final String startname = new String(cbuf, 0, elRawNameEnd[depth]);
            final String endname = new String(buf, off, len);
            throw new XmlPullParserException(
                    "end tag name </" + endname + "> must match start tag name <" + startname + ">" + " from line "
                                             + elRawNameLine[depth],
                    this, null);
        }
        for (int i = 0; i < len; i++) {
            if (buf[off++] != cbuf[i]) {
                // construct strings for exception
                final String startname = new String(cbuf, 0, len);
                final String endname = new String(buf, off - i - 1, len);
                throw new XmlPullParserException(
                        "end tag name </" + endname + "> must be the same as start tag <" + startname + ">" + " from line "
                                                 + elRawNameLine[depth],
                        this,
                        null);
            }
        }

        while (isS(ch)) {
            ch = more();
        } // skip additional white spaces
        if (ch != '>') {
            throw new XmlPullParserException(
                    "expected > to finish end tag not " + printable(ch) + " from line " + elRawNameLine[depth], this, null);
        }

        // namespaceEnd = elNamespaceCount[ depth ]; //FIXME

        posEnd = pos;
        pastEndTag = true;
        return eventType = END_TAG;
    }

    public int parseStartTag() throws XmlPullParserException, IOException {
        // remember starting line number
        startLineNumber = lineNumber;

        // ASSUMPTION ch is past <T
        // [40] STag ::= '<' Name (S Attribute)* S? '>'
        // [44] EmptyElemTag ::= '<' Name (S Attribute)* S? '/>'
        ++depth; // FIXME

        posStart = pos - 2;

        emptyElementTag = false;
        attributeCount = 0;
        // retrieve name
        final int nameStart = pos - 1 + bufAbsoluteStart;
        int colonPos = -1;
        char ch = buf[pos - 1];
        if (ch == ':' && processNamespaces)
            throw new XmlPullParserException(
                    "when namespaces processing enabled colon can not be at element name start", this, null);
        while (true) {
            ch = more();
            if (!isNameChar(ch))
                break;
            if (ch == ':' && processNamespaces) {
                if (colonPos != -1)
                    throw new XmlPullParserException(
                            "only one colon is allowed in name of element when namespaces are enabled", this, null);
                colonPos = pos - 1 + bufAbsoluteStart;
            }
        }

        // retrieve name
        ensureElementsCapacity();

        // TODO check for efficient interning and then use elRawNameInterned!!!!

        int elLen = (pos - 1) - (nameStart - bufAbsoluteStart);
        if (elRawName[depth] == null || elRawName[depth].length < elLen) {
            elRawName[depth] = new char[2 * elLen];
        }
        System.arraycopy(buf, nameStart - bufAbsoluteStart, elRawName[depth], 0, elLen);
        elRawNameEnd[depth] = elLen;
        elRawNameLine[depth] = lineNumber;

        // work on prefixes and namespace URI
        String prefix = null;
        if (processNamespaces) {
            if (colonPos != -1) {
                prefix = elPrefix[depth] = newString(buf, nameStart - bufAbsoluteStart, colonPos - nameStart);
                elName[depth] = newString(buf, colonPos + 1 - bufAbsoluteStart,
                        // (pos -1) - (colonPos + 1));
                        pos - 2 - (colonPos - bufAbsoluteStart));
            } else {
                prefix = elPrefix[depth] = null;
                elName[depth] = newString(buf, nameStart - bufAbsoluteStart, elLen);
            }
        } else {
            elName[depth] = newString(buf, nameStart - bufAbsoluteStart, elLen);
        }

        while (true) {

            while (isS(ch)) {
                ch = more();
            } // skip additional white spaces

            if (ch == '>') {
                break;
            } else if (ch == '/') {
                if (emptyElementTag)
                    throw new XmlPullParserException("repeated / in tag declaration", this, null);
                emptyElementTag = true;
                ch = more();
                if (ch != '>')
                    throw new XmlPullParserException("expected > to end empty tag not " + printable(ch), this, null);
                break;
            } else if (isNameStartChar(ch)) {
                ch = parseAttribute();
                ch = more();
                continue;
            } else {
                throw new XmlPullParserException("start tag unexpected character " + printable(ch), this, null);
            }
            // ch = more(); // skip space
        }

        // now when namespaces were declared we can resolve them
        if (processNamespaces) {
            String uri = getNamespace(prefix);
            if (uri == null) {
                if (prefix == null) { // no prefix and no uri => use default
                                     // namespace
                    uri = NO_NAMESPACE;
                } else {
                    throw new XmlPullParserException(
                            "could not determine namespace bound to element prefix " + prefix, this, null);
                }

            }
            elUri[depth] = uri;

            // String uri = getNamespace(prefix);
            // if(uri == null && prefix == null) { // no prefix and no uri =>
            // use default namespace
            // uri = "";
            // }
            // resolve attribute namespaces
            for (int i = 0; i < attributeCount; i++) {
                final String attrPrefix = attributePrefix[i];
                if (attrPrefix != null) {
                    final String attrUri = getNamespace(attrPrefix);
                    if (attrUri == null) {
                        throw new XmlPullParserException(
                                "could not determine namespace bound to attribute prefix " + attrPrefix, this, null);

                    }
                    attributeUri[i] = attrUri;
                } else {
                    attributeUri[i] = NO_NAMESPACE;
                }
            }

            // TODO
            // [ WFC: Unique Att Spec ]
            // check attribute uniqueness constraint for attributes that has
            // namespace!!!

            for (int i = 1; i < attributeCount; i++) {
                for (int j = 0; j < i; j++) {
                    if (attributeUri[j] == attributeUri[i]
                            && (allStringsInterned && attributeName[j].equals(attributeName[i])
                                    || (!allStringsInterned && attributeNameHash[j] == attributeNameHash[i]
                                            && attributeName[j].equals(attributeName[i])))

                    ) {
                        // prepare data for nice error message?
                        String attr1 = attributeName[j];
                        if (attributeUri[j] != null)
                            attr1 = attributeUri[j] + ":" + attr1;
                        String attr2 = attributeName[i];
                        if (attributeUri[i] != null)
                            attr2 = attributeUri[i] + ":" + attr2;
                        throw new XmlPullParserException("duplicated attributes " + attr1 + " and " + attr2, this, null);
                    }
                }
            }

        } else { // ! processNamespaces

            // [ WFC: Unique Att Spec ]
            // check raw attribute uniqueness constraint!!!
            for (int i = 1; i < attributeCount; i++) {
                for (int j = 0; j < i; j++) {
                    if ((allStringsInterned && attributeName[j].equals(attributeName[i])
                            || (!allStringsInterned && attributeNameHash[j] == attributeNameHash[i]
                                    && attributeName[j].equals(attributeName[i])))

                    ) {
                        // prepare data for nice error message?
                        final String attr1 = attributeName[j];
                        final String attr2 = attributeName[i];
                        throw new XmlPullParserException("duplicated attributes " + attr1 + " and " + attr2, this, null);
                    }
                }
            }
        }

        elNamespaceCount[depth] = namespaceEnd;
        posEnd = pos;
        return eventType = START_TAG;
    }

    protected char parseAttribute() throws XmlPullParserException, IOException {
        // parse attribute
        // [41] Attribute ::= Name Eq AttValue
        // [WFC: No External Entity References]
        // [WFC: No < in Attribute Values]
        final int prevPosStart = posStart + bufAbsoluteStart;
        final int nameStart = pos - 1 + bufAbsoluteStart;
        int colonPos = -1;
        char ch = buf[pos - 1];
        if (ch == ':' && processNamespaces)
            throw new XmlPullParserException(
                    "when namespaces processing enabled colon can not be at attribute name start", this, null);

        boolean startsWithXmlns = processNamespaces && ch == 'x';
        int xmlnsPos = 0;

        ch = more();
        while (isNameChar(ch)) {
            if (processNamespaces) {
                if (startsWithXmlns && xmlnsPos < 5) {
                    ++xmlnsPos;
                    if (xmlnsPos == 1) {
                        if (ch != 'm')
                            startsWithXmlns = false;
                    } else if (xmlnsPos == 2) {
                        if (ch != 'l')
                            startsWithXmlns = false;
                    } else if (xmlnsPos == 3) {
                        if (ch != 'n')
                            startsWithXmlns = false;
                    } else if (xmlnsPos == 4) {
                        if (ch != 's')
                            startsWithXmlns = false;
                    } else {
                        if (ch != ':')
                            throw new XmlPullParserException(
                                    "after xmlns in attribute name must be colon" + " when namespaces are enabled", this, null);
                        // colonPos = pos - 1 + bufAbsoluteStart;
                    }
                }
                if (ch == ':') {
                    if (colonPos != -1)
                        throw new XmlPullParserException(
                                "only one colon is allowed in attribute name" + " when namespaces are enabled", this, null);
                    colonPos = pos - 1 + bufAbsoluteStart;
                }
            }
            ch = more();
        }

        ensureAttributesCapacity(attributeCount);

        // --- start processing attributes
        String name = null;
        String prefix = null;
        // work on prefixes and namespace URI
        if (processNamespaces) {
            if (xmlnsPos < 4)
                startsWithXmlns = false;
            if (startsWithXmlns) {
                if (colonPos != -1) {
                    // prefix = attributePrefix[ attributeCount ] = null;
                    final int nameLen = pos - 2 - (colonPos - bufAbsoluteStart);
                    if (nameLen == 0) {
                        throw new XmlPullParserException(
                                "namespace prefix is required after xmlns: " + " when namespaces are enabled", this, null);
                    }
                    name = // attributeName[ attributeCount ] =
                            newString(buf, colonPos - bufAbsoluteStart + 1, nameLen);
                    // pos - 1 - (colonPos + 1 - bufAbsoluteStart)
                }
            } else {
                if (colonPos != -1) {
                    int prefixLen = colonPos - nameStart;
                    prefix = attributePrefix[attributeCount] = newString(buf, nameStart - bufAbsoluteStart, prefixLen);
                    // colonPos - (nameStart - bufAbsoluteStart));
                    int nameLen = pos - 2 - (colonPos - bufAbsoluteStart);
                    name = attributeName[attributeCount] = newString(buf, colonPos - bufAbsoluteStart + 1, nameLen);
                    // pos - 1 - (colonPos + 1 - bufAbsoluteStart));

                    // name.substring(0, colonPos-nameStart);
                } else {
                    prefix = attributePrefix[attributeCount] = null;
                    name = attributeName[attributeCount]
                            = newString(buf, nameStart - bufAbsoluteStart, pos - 1 - (nameStart - bufAbsoluteStart));
                }
                if (!allStringsInterned) {
                    attributeNameHash[attributeCount] = name.hashCode();
                }
            }

        } else {
            // retrieve name
            name = attributeName[attributeCount]
                    = newString(buf, nameStart - bufAbsoluteStart, pos - 1 - (nameStart - bufAbsoluteStart));
            //// assert name != null;
            if (!allStringsInterned) {
                attributeNameHash[attributeCount] = name.hashCode();
            }
        }

        // [25] Eq ::= S? '=' S?
        while (isS(ch)) {
            ch = more();
        } // skip additional spaces
        if (ch != '=')
            throw new XmlPullParserException("expected = after attribute name", this, null);
        ch = more();
        while (isS(ch)) {
            ch = more();
        } // skip additional spaces

        // [10] AttValue ::= '"' ([^<&"] | Reference)* '"'
        // | "'" ([^<&'] | Reference)* "'"
        final char delimit = ch;
        if (delimit != '"' && delimit != '\'')
            throw new XmlPullParserException(
                    "attribute value must start with quotation or apostrophe not " + printable(delimit), this, null);
        // parse until delimit or < and resolve Reference
        // [67] Reference ::= EntityRef | CharRef
        // int valueStart = pos + bufAbsoluteStart;

        boolean normalizedCR = false;
        usePC = false;
        pcStart = pcEnd;
        posStart = pos;

        while (true) {
            ch = more();
            if (ch == delimit) {
                break;
            }
            if (ch == '<') {
                throw new XmlPullParserException("markup not allowed inside attribute value - illegal < ", this, null);
            }
            if (ch == '&') {
                // extractEntityRef
                posEnd = pos - 1;
                if (!usePC) {
                    final boolean hadCharData = posEnd > posStart;
                    if (hadCharData) {
                        // posEnd is already set correctly!!!
                        joinPC();
                    } else {
                        usePC = true;
                        pcStart = pcEnd = 0;
                    }
                }
                // assert usePC == true;

                final char[] resolvedEntity = parseEntityRef();
                // check if replacement text can be resolved !!!
                if (resolvedEntity == null) {
                    if (entityRefName == null) {
                        entityRefName = newString(buf, posStart, posEnd - posStart);
                    }
                    throw new XmlPullParserException(
                            "could not resolve entity named '" + printable(entityRefName) + "'", this, null);
                }
                // write into PC replacement text - do merge for replacement
                // text!!!!
                for (int i = 0; i < resolvedEntity.length; i++) {
                    if (pcEnd >= pc.length)
                        ensurePC(pcEnd);
                    pc[pcEnd++] = resolvedEntity[i];
                }
            } else if (ch == '\t' || ch == '\n' || ch == '\r') {
                // do attribute value normalization
                // as described in http://www.w3.org/TR/REC-xml#AVNormalize
                // TODO add test for it form spec ...
                // handle EOL normalization ...
                if (!usePC) {
                    posEnd = pos - 1;
                    if (posEnd > posStart) {
                        joinPC();
                    } else {
                        usePC = true;
                        pcEnd = pcStart = 0;
                    }
                }
                // assert usePC == true;
                if (pcEnd >= pc.length)
                    ensurePC(pcEnd);
                if (ch != '\n' || !normalizedCR) {
                    pc[pcEnd++] = ' '; // '\n';
                }

            } else {
                if (usePC) {
                    if (pcEnd >= pc.length)
                        ensurePC(pcEnd);
                    pc[pcEnd++] = ch;
                }
            }
            normalizedCR = ch == '\r';
        }

        if (processNamespaces && startsWithXmlns) {
            String ns = null;
            if (!usePC) {
                ns = newStringIntern(buf, posStart, pos - 1 - posStart);
            } else {
                ns = newStringIntern(pc, pcStart, pcEnd - pcStart);
            }
            ensureNamespacesCapacity(namespaceEnd);
            int prefixHash = -1;
            if (colonPos != -1) {
                if (ns.isEmpty()) {
                    throw new XmlPullParserException(
                            "non-default namespace can not be declared to be empty string", this, null);
                }
                // declare new namespace
                namespacePrefix[namespaceEnd] = name;
                if (!allStringsInterned) {
                    prefixHash = namespacePrefixHash[namespaceEnd] = name.hashCode();
                }
            } else {
                // declare new default namespace ...
                namespacePrefix[namespaceEnd] = null; // ""; //null; //TODO
                                                     // check FIXME Alek
                if (!allStringsInterned) {
                    prefixHash = namespacePrefixHash[namespaceEnd] = -1;
                }
            }
            namespaceUri[namespaceEnd] = ns;

            // detect duplicate namespace declarations!!!
            final int startNs = elNamespaceCount[depth - 1];
            for (int i = namespaceEnd - 1; i >= startNs; --i) {
                if (((allStringsInterned || name == null) && namespacePrefix[i] == name)
                        || (!allStringsInterned && name != null && namespacePrefixHash[i] == prefixHash
                                && name.equals(namespacePrefix[i]))) {
                    final String s = name == null ? "default" : "'" + name + "'";
                    throw new XmlPullParserException("duplicated namespace declaration for " + s + " prefix", this, null);
                }
            }

            ++namespaceEnd;

        } else {
            if (!usePC) {
                attributeValue[attributeCount] = new String(buf, posStart, pos - 1 - posStart);
            } else {
                attributeValue[attributeCount] = new String(pc, pcStart, pcEnd - pcStart);
            }
            ++attributeCount;
        }
        posStart = prevPosStart - bufAbsoluteStart;
        return ch;
    }

    protected char[] charRefOneCharBuf = new char[1];

    protected char[] parseEntityRef() throws XmlPullParserException, IOException {
        // entity reference
        // http://www.w3.org/TR/2000/REC-xml-20001006#NT-Reference
        // [67] Reference ::= EntityRef | CharRef

        // ASSUMPTION just after &
        entityRefName = null;
        posStart = pos;
        char ch = more();
        if (ch == '#') {
            // parse character reference
            char charRef = 0;
            ch = more();
            if (ch == 'x') {
                // encoded in hex
                while (true) {
                    ch = more();
                    if (ch >= '0' && ch <= '9') {
                        charRef = (char) (charRef * 16 + (ch - '0'));
                    } else if (ch >= 'a' && ch <= 'f') {
                        charRef = (char) (charRef * 16 + (ch - ('a' - 10)));
                    } else if (ch >= 'A' && ch <= 'F') {
                        charRef = (char) (charRef * 16 + (ch - ('A' - 10)));
                    } else if (ch == ';') {
                        break;
                    } else {
                        throw new XmlPullParserException(
                                "character reference (with hex value) may not contain " + printable(ch), this, null);
                    }
                }
            } else {
                // encoded in decimal
                while (true) {
                    if (ch >= '0' && ch <= '9') {
                        charRef = (char) (charRef * 10 + (ch - '0'));
                    } else if (ch == ';') {
                        break;
                    } else {
                        throw new XmlPullParserException(
                                "character reference (with decimal value) may not contain " + printable(ch), this, null);
                    }
                    ch = more();
                }
            }
            posEnd = pos - 1;
            charRefOneCharBuf[0] = charRef;
            if (tokenize) {
                text = newString(charRefOneCharBuf, 0, 1);
            }
            return charRefOneCharBuf;
        } else {
            // [68] EntityRef ::= '&' Name ';'
            // scan name until ;
            if (!isNameStartChar(ch)) {
                throw new XmlPullParserException(
                        "entity reference names can not start with character '" + printable(ch) + "'", this, null);
            }
            while (true) {
                ch = more();
                if (ch == ';') {
                    break;
                }
                if (!isNameChar(ch)) {
                    throw new XmlPullParserException(
                            "entity reference name can not contain character " + printable(ch) + "'", this, null);
                }
            }
            posEnd = pos - 1;
            // determine what name maps to
            final int len = posEnd - posStart;
            if (len == 2 && buf[posStart] == 'l' && buf[posStart + 1] == 't') {
                if (tokenize) {
                    text = "<";
                }
                charRefOneCharBuf[0] = '<';
                return charRefOneCharBuf;
                // if(paramPC || isParserTokenizing) {
                // if(pcEnd >= pc.length) ensurePC();
                // pc[pcEnd++] = '<';
                // }
            } else if (len == 3 && buf[posStart] == 'a' && buf[posStart + 1] == 'm' && buf[posStart + 2] == 'p') {
                if (tokenize) {
                    text = "&";
                }
                charRefOneCharBuf[0] = '&';
                return charRefOneCharBuf;
            } else if (len == 2 && buf[posStart] == 'g' && buf[posStart + 1] == 't') {
                if (tokenize) {
                    text = ">";
                }
                charRefOneCharBuf[0] = '>';
                return charRefOneCharBuf;
            } else if (len == 4 && buf[posStart] == 'a' && buf[posStart + 1] == 'p' && buf[posStart + 2] == 'o'
                    && buf[posStart + 3] == 's') {
                if (tokenize) {
                    text = "'";
                }
                charRefOneCharBuf[0] = '\'';
                return charRefOneCharBuf;
            } else if (len == 4 && buf[posStart] == 'q' && buf[posStart + 1] == 'u' && buf[posStart + 2] == 'o'
                    && buf[posStart + 3] == 't') {
                if (tokenize) {
                    text = "\"";
                }
                charRefOneCharBuf[0] = '"';
                return charRefOneCharBuf;
            } else {
                final char[] result = lookuEntityReplacement(len);
                if (result != null) {
                    return result;
                }
            }
            if (tokenize)
                text = null;
            return null;
        }
    }

    protected char[] lookuEntityReplacement(int entitNameLen) throws XmlPullParserException, IOException {
        if (!allStringsInterned) {
            final int hash = fastHash(buf, posStart, posEnd - posStart);
            LOOP: for (int i = entityEnd - 1; i >= 0; --i) {
                if (hash == entityNameHash[i] && entitNameLen == entityNameBuf[i].length) {
                    final char[] entityBuf = entityNameBuf[i];
                    for (int j = 0; j < entitNameLen; j++) {
                        if (buf[posStart + j] != entityBuf[j])
                            continue LOOP;
                    }
                    if (tokenize)
                        text = entityReplacement[i];
                    return entityReplacementBuf[i];
                }
            }
        } else {
            entityRefName = newString(buf, posStart, posEnd - posStart);
            for (int i = entityEnd - 1; i >= 0; --i) {
                // take advantage that interning for newStirng is enforced
                if (entityRefName == entityName[i]) {
                    if (tokenize)
                        text = entityReplacement[i];
                    return entityReplacementBuf[i];
                }
            }
        }
        return null;
    }

    protected void parseComment() throws XmlPullParserException, IOException {
        // implements XML 1.0 Section 2.5 Comments

        // ASSUMPTION: seen <!-
        char ch = more();
        if (ch != '-')
            throw new XmlPullParserException("expected <!-- for comment start", this, null);
        if (tokenize)
            posStart = pos;

        final int curLine = lineNumber;
        final int curColumn = columnNumber;
        try {
            final boolean normalizeIgnorableWS = tokenize && !roundtripSupported;
            boolean normalizedCR = false;

            boolean seenDash = false;
            boolean seenDashDash = false;
            while (true) {
                // scan until it hits -->
                ch = more();
                if (seenDashDash && ch != '>') {
                    throw new XmlPullParserException(
                            "in comment after two dashes (--) next character must be >" + " not " + printable(ch), this, null);
                }
                if (ch == '-') {
                    if (!seenDash) {
                        seenDash = true;
                    } else {
                        seenDashDash = true;
                        seenDash = false;
                    }
                } else if (ch == '>') {
                    if (seenDashDash) {
                        break; // found end sequence!!!!
                    } else {
                        seenDashDash = false;
                    }
                    seenDash = false;
                } else {
                    seenDash = false;
                }
                if (normalizeIgnorableWS) {
                    if (ch == '\r') {
                        normalizedCR = true;
                        // posEnd = pos -1;
                        // joinPC();
                        // posEnd is already set
                        if (!usePC) {
                            posEnd = pos - 1;
                            if (posEnd > posStart) {
                                joinPC();
                            } else {
                                usePC = true;
                                pcStart = pcEnd = 0;
                            }
                        }
                        // assert usePC == true;
                        if (pcEnd >= pc.length)
                            ensurePC(pcEnd);
                        pc[pcEnd++] = '\n';
                    } else if (ch == '\n') {
                        if (!normalizedCR && usePC) {
                            if (pcEnd >= pc.length)
                                ensurePC(pcEnd);
                            pc[pcEnd++] = '\n';
                        }
                        normalizedCR = false;
                    } else {
                        if (usePC) {
                            if (pcEnd >= pc.length)
                                ensurePC(pcEnd);
                            pc[pcEnd++] = ch;
                        }
                        normalizedCR = false;
                    }
                }
            }

        } catch (EOFException ex) {
            // detect EOF and create meaningful error ...
            throw new XmlPullParserException(
                    "comment started on line " + curLine + " and column " + curColumn + " was not closed", this, ex);
        }
        if (tokenize) {
            posEnd = pos - 3;
            if (usePC) {
                pcEnd -= 2;
            }
        }
    }

    protected boolean parsePI() throws XmlPullParserException, IOException {
        // implements XML 1.0 Section 2.6 Processing Instructions

        // [16] PI ::= '<?' PITarget (S (Char* - (Char* '?>' Char*)))? '?>'
        // [17] PITarget ::= Name - (('X' | 'x') ('M' | 'm') ('L' | 'l'))
        // ASSUMPTION: seen <?
        if (tokenize)
            posStart = pos;
        final int curLine = lineNumber;
        final int curColumn = columnNumber;
        int piTargetStart = pos + bufAbsoluteStart;
        int piTargetEnd = -1;
        final boolean normalizeIgnorableWS = tokenize && !roundtripSupported;
        boolean normalizedCR = false;

        try {
            boolean seenQ = false;
            char ch = more();
            if (isS(ch)) {
                throw new XmlPullParserException(
                        "processing instruction PITarget must be exactly after <? and not white space character", this, null);
            }
            while (true) {
                // scan until it hits ?>
                // ch = more();

                if (ch == '?') {
                    seenQ = true;
                } else if (ch == '>') {
                    if (seenQ) {
                        break; // found end sequence!!!!
                    }
                    seenQ = false;
                } else {
                    if (piTargetEnd == -1 && isS(ch)) {
                        piTargetEnd = pos - 1 + bufAbsoluteStart;

                        // [17] PITarget ::= Name - (('X' | 'x') ('M' | 'm')
                        // ('L' | 'l'))
                        if ((piTargetEnd - piTargetStart) == 3) {
                            if ((buf[piTargetStart] == 'x' || buf[piTargetStart] == 'X')
                                    && (buf[piTargetStart + 1] == 'm' || buf[piTargetStart + 1] == 'M')
                                    && (buf[piTargetStart + 2] == 'l' || buf[piTargetStart + 2] == 'L')) {
                                if (piTargetStart > 3) { // <?xml is allowed as
                                                        // first characters in
                                                        // input ...
                                    throw new XmlPullParserException(
                                            "processing instruction can not have PITarget with reserveld xml name", this, null);
                                } else {
                                    if (buf[piTargetStart] != 'x' && buf[piTargetStart + 1] != 'm'
                                            && buf[piTargetStart + 2] != 'l') {
                                        throw new XmlPullParserException("XMLDecl must have xml name in lowercase", this, null);
                                    }
                                }
                                parseXmlDecl(ch);
                                if (tokenize)
                                    posEnd = pos - 2;
                                final int off = piTargetStart - bufAbsoluteStart + 3;
                                final int len = pos - 2 - off;
                                xmlDeclContent = newString(buf, off, len);
                                return false;
                            }
                        }
                    }
                    seenQ = false;
                }
                if (normalizeIgnorableWS) {
                    if (ch == '\r') {
                        normalizedCR = true;
                        // posEnd = pos -1;
                        // joinPC();
                        // posEnd is already set
                        if (!usePC) {
                            posEnd = pos - 1;
                            if (posEnd > posStart) {
                                joinPC();
                            } else {
                                usePC = true;
                                pcStart = pcEnd = 0;
                            }
                        }
                        // assert usePC == true;
                        if (pcEnd >= pc.length)
                            ensurePC(pcEnd);
                        pc[pcEnd++] = '\n';
                    } else if (ch == '\n') {
                        if (!normalizedCR && usePC) {
                            if (pcEnd >= pc.length)
                                ensurePC(pcEnd);
                            pc[pcEnd++] = '\n';
                        }
                        normalizedCR = false;
                    } else {
                        if (usePC) {
                            if (pcEnd >= pc.length)
                                ensurePC(pcEnd);
                            pc[pcEnd++] = ch;
                        }
                        normalizedCR = false;
                    }
                }
                ch = more();
            }
        } catch (EOFException ex) {
            // detect EOF and create meaningful error ...
            throw new XmlPullParserException(
                    "processing instruction started on line " + curLine + " and column " + curColumn + " was not closed", this,
                    ex);
        }
        if (piTargetEnd == -1) {
            piTargetEnd = pos - 2 + bufAbsoluteStart;
            // throw new XmlPullParserException(
            // "processing instruction must have PITarget name", this, null);
        }
        piTargetStart -= bufAbsoluteStart;
        piTargetEnd -= bufAbsoluteStart;
        if (tokenize) {
            posEnd = pos - 2;
            if (normalizeIgnorableWS) {
                --pcEnd;
            }
        }
        return true;
    }

    // protected final static char[] VERSION = {'v','e','r','s','i','o','n'};
    // protected final static char[] NCODING = {'n','c','o','d','i','n','g'};
    // protected final static char[] TANDALONE =
    // {'t','a','n','d','a','l','o','n','e'};
    // protected final static char[] YES = {'y','e','s'};
    // protected final static char[] NO = {'n','o'};

    protected static final char[] VERSION = "version".toCharArray();
    protected static final char[] NCODING = "ncoding".toCharArray();
    protected static final char[] TANDALONE = "tandalone".toCharArray();
    protected static final char[] YES = "yes".toCharArray();
    protected static final char[] NO = "no".toCharArray();

    protected void parseXmlDecl(char ch) throws XmlPullParserException, IOException {
        // [23] XMLDecl ::= '<?xml' VersionInfo EncodingDecl? SDDecl? S? '?>'

        // first make sure that relative positions will stay OK
        preventBufferCompaction = true;
        bufStart = 0; // necessary to keep pos unchanged during expansion!

        // --- parse VersionInfo

        // [24] VersionInfo ::= S 'version' Eq ("'" VersionNum "'" | '"'
        // VersionNum '"')
        // parse is positioned just on first S past <?xml
        ch = skipS(ch);
        ch = requireInput(ch, VERSION);
        // [25] Eq ::= S? '=' S?
        ch = skipS(ch);
        if (ch != '=') {
            throw new XmlPullParserException("expected equals sign (=) after version and not " + printable(ch), this, null);
        }
        ch = more();
        ch = skipS(ch);
        if (ch != '\'' && ch != '"') {
            throw new XmlPullParserException(
                    "expected apostrophe (') or quotation mark (\") after version and not " + printable(ch), this, null);
        }
        final char quotChar = ch;
        // int versionStart = pos + bufAbsoluteStart; // required if
        // preventBufferCompaction==false
        final int versionStart = pos;
        ch = more();
        // [26] VersionNum ::= ([a-zA-Z0-9_.:] | '-')+
        while (ch != quotChar) {
            if ((ch < 'a' || ch > 'z') && (ch < 'A' || ch > 'Z') && (ch < '0' || ch > '9') && ch != '_' && ch != '.'
                    && ch != ':' && ch != '-') {
                throw new XmlPullParserException(
                        "<?xml version value expected to be in ([a-zA-Z0-9_.:] | '-')" + " not " + printable(ch), this, null);
            }
            ch = more();
        }
        final int versionEnd = pos - 1;
        parseXmlDeclWithVersion(versionStart, versionEnd);
        preventBufferCompaction = false; // alow again buffer commpaction - pos
                                        // MAY chnage
    }
    // protected String xmlDeclVersion;

    protected void parseXmlDeclWithVersion(int versionStart, int versionEnd) throws XmlPullParserException, IOException {
        // check version is "1.0"
        if ((versionEnd - versionStart != 3) || buf[versionStart] != '1' || buf[versionStart + 1] != '.'
                || buf[versionStart + 2] != '0') {
            throw new XmlPullParserException(
                    "only 1.0 is supported as <?xml version not '"
                                             + printable(new String(buf, versionStart, versionEnd - versionStart)) + "'",
                    this,
                    null);
        }
        xmlDeclVersion = newString(buf, versionStart, versionEnd - versionStart);

        // [80] EncodingDecl ::= S 'encoding' Eq ('"' EncName '"' | "'" EncName
        // "'" )
        char ch = more();
        ch = skipS(ch);
        if (ch == 'e') {
            ch = more();
            ch = requireInput(ch, NCODING);
            ch = skipS(ch);
            if (ch != '=') {
                throw new XmlPullParserException(
                        "expected equals sign (=) after encoding and not " + printable(ch), this, null);
            }
            ch = more();
            ch = skipS(ch);
            if (ch != '\'' && ch != '"') {
                throw new XmlPullParserException(
                        "expected apostrophe (') or quotation mark (\") after encoding and not " + printable(ch), this, null);
            }
            final char quotChar = ch;
            final int encodingStart = pos;
            ch = more();
            // [81] EncName ::= [A-Za-z] ([A-Za-z0-9._] | '-')*
            if ((ch < 'a' || ch > 'z') && (ch < 'A' || ch > 'Z')) {
                throw new XmlPullParserException(
                        "<?xml encoding name expected to start with [A-Za-z]" + " not " + printable(ch), this, null);
            }
            ch = more();
            while (ch != quotChar) {
                if ((ch < 'a' || ch > 'z') && (ch < 'A' || ch > 'Z') && (ch < '0' || ch > '9') && ch != '.' && ch != '_'
                        && ch != '-') {
                    throw new XmlPullParserException(
                            "<?xml encoding value expected to be in ([A-Za-z0-9._] | '-')" + " not " + printable(ch), this,
                            null);
                }
                ch = more();
            }
            final int encodingEnd = pos - 1;

            // TODO reconcile with setInput encodingName
            inputEncoding = newString(buf, encodingStart, encodingEnd - encodingStart);
            ch = more();
        }

        ch = skipS(ch);
        // [32] SDDecl ::= S 'standalone' Eq (("'" ('yes' | 'no') "'") | ('"'
        // ('yes' | 'no') '"'))
        if (ch == 's') {
            ch = more();
            ch = requireInput(ch, TANDALONE);
            ch = skipS(ch);
            if (ch != '=') {
                throw new XmlPullParserException(
                        "expected equals sign (=) after standalone and not " + printable(ch), this, null);
            }
            ch = more();
            ch = skipS(ch);
            if (ch != '\'' && ch != '"') {
                throw new XmlPullParserException(
                        "expected apostrophe (') or quotation mark (\") after encoding and not " + printable(ch), this, null);
            }
            char quotChar = ch;
            int standaloneStart = pos;
            ch = more();
            if (ch == 'y') {
                ch = requireInput(ch, YES);
                // Boolean standalone = new Boolean(true);
                xmlDeclStandalone = Boolean.TRUE;
            } else if (ch == 'n') {
                ch = requireInput(ch, NO);
                // Boolean standalone = new Boolean(false);
                xmlDeclStandalone = Boolean.FALSE;
            } else {
                throw new XmlPullParserException(
                        "expected 'yes' or 'no' after standalone and not " + printable(ch), this, null);
            }
            if (ch != quotChar) {
                throw new XmlPullParserException(
                        "expected " + quotChar + " after standalone value not " + printable(ch), this, null);
            }
            ch = more();
        }

        ch = skipS(ch);
        if (ch != '?') {
            throw new XmlPullParserException("expected ?> as last part of <?xml not " + printable(ch), this, null);
        }
        ch = more();
        if (ch != '>') {
            throw new XmlPullParserException("expected ?> as last part of <?xml not " + printable(ch), this, null);
        }

        // NOTE: this code is broken as for some types of input streams (URLConnection ...)
        // it is not possible to do more than once new InputStreamReader(inputStream)
        // as it somehow detects it and closes undelrying inout stram (b.....d!)
        // In future one will need better low level byte-by-byte reading of prolog and then doing InputStream ...
        // for more details see http://www.extreme.indiana.edu/bugzilla/show_bug.cgi?id=135
        // //reset input stream
        //        if ((this.inputEncoding != oldEncoding) && (this.inputStream != null)) {
        //            if ((this.inputEncoding != null) && (!this.inputEncoding.equalsIgnoreCase(oldEncoding))) {
        //                //              //there is need to reparse input to set location OK
        //                //              reset();
        //                this.reader = new InputStreamReader(this.inputStream, this.inputEncoding);
        //                //              //skip <?xml
        //                //              for (int i = 0; i < 5; i++){
        //                //                  ch=more();
        //                //              }
        //                //              parseXmlDecl(ch);
        //            }
        //        }
    }

    protected void parseDocdecl() throws XmlPullParserException, IOException {
        // ASSUMPTION: seen <!D
        char ch = more();
        if (ch != 'O')
            throw new XmlPullParserException("expected <!DOCTYPE", this, null);
        ch = more();
        if (ch != 'C')
            throw new XmlPullParserException("expected <!DOCTYPE", this, null);
        ch = more();
        if (ch != 'T')
            throw new XmlPullParserException("expected <!DOCTYPE", this, null);
        ch = more();
        if (ch != 'Y')
            throw new XmlPullParserException("expected <!DOCTYPE", this, null);
        ch = more();
        if (ch != 'P')
            throw new XmlPullParserException("expected <!DOCTYPE", this, null);
        ch = more();
        if (ch != 'E')
            throw new XmlPullParserException("expected <!DOCTYPE", this, null);
        posStart = pos;
        // do simple and crude scanning for end of doctype

        // [28] doctypedecl ::= '<!DOCTYPE' S Name (S ExternalID)? S? ('['
        // (markupdecl | DeclSep)* ']' S?)? '>'
        int bracketLevel = 0;
        final boolean normalizeIgnorableWS = tokenize && !roundtripSupported;
        boolean normalizedCR = false;
        while (true) {
            ch = more();
            if (ch == '[')
                ++bracketLevel;
            if (ch == ']')
                --bracketLevel;
            if (ch == '>' && bracketLevel == 0)
                break;
            if (normalizeIgnorableWS) {
                if (ch == '\r') {
                    normalizedCR = true;
                    // posEnd = pos -1;
                    // joinPC();
                    // posEnd is alreadys set
                    if (!usePC) {
                        posEnd = pos - 1;
                        if (posEnd > posStart) {
                            joinPC();
                        } else {
                            usePC = true;
                            pcStart = pcEnd = 0;
                        }
                    }
                    // assert usePC == true;
                    if (pcEnd >= pc.length)
                        ensurePC(pcEnd);
                    pc[pcEnd++] = '\n';
                } else if (ch == '\n') {
                    if (!normalizedCR && usePC) {
                        if (pcEnd >= pc.length)
                            ensurePC(pcEnd);
                        pc[pcEnd++] = '\n';
                    }
                    normalizedCR = false;
                } else {
                    if (usePC) {
                        if (pcEnd >= pc.length)
                            ensurePC(pcEnd);
                        pc[pcEnd++] = ch;
                    }
                    normalizedCR = false;
                }
            }

        }
        posEnd = pos - 1;
    }

    protected void parseCDSect(boolean hadCharData) throws XmlPullParserException, IOException {
        // implements XML 1.0 Section 2.7 CDATA Sections

        // [18] CDSect ::= CDStart CData CDEnd
        // [19] CDStart ::= '<![CDATA['
        // [20] CData ::= (Char* - (Char* ']]>' Char*))
        // [21] CDEnd ::= ']]>'

        // ASSUMPTION: seen <![
        char ch = more();
        if (ch != 'C')
            throw new XmlPullParserException("expected <[CDATA[ for comment start", this, null);
        ch = more();
        if (ch != 'D')
            throw new XmlPullParserException("expected <[CDATA[ for comment start", this, null);
        ch = more();
        if (ch != 'A')
            throw new XmlPullParserException("expected <[CDATA[ for comment start", this, null);
        ch = more();
        if (ch != 'T')
            throw new XmlPullParserException("expected <[CDATA[ for comment start", this, null);
        ch = more();
        if (ch != 'A')
            throw new XmlPullParserException("expected <[CDATA[ for comment start", this, null);
        ch = more();
        if (ch != '[')
            throw new XmlPullParserException("expected <![CDATA[ for comment start", this, null);

        // if(tokenize) {
        final int cdStart = pos + bufAbsoluteStart;
        final int curLine = lineNumber;
        final int curColumn = columnNumber;
        final boolean normalizeInput = !tokenize || !roundtripSupported;
        try {
            if (normalizeInput) {
                if (hadCharData) {
                    if (!usePC) {
                        // posEnd is correct already!!!
                        if (posEnd > posStart) {
                            joinPC();
                        } else {
                            usePC = true;
                            pcStart = pcEnd = 0;
                        }
                    }
                }
            }
            boolean seenBracket = false;
            boolean seenBracketBracket = false;
            boolean normalizedCR = false;
            while (true) {
                // scan until it hits "]]>"
                ch = more();
                if (ch == ']') {
                    if (!seenBracket) {
                        seenBracket = true;
                    } else {
                        seenBracketBracket = true;
                        // seenBracket = false;
                    }
                } else if (ch == '>') {
                    if (seenBracket && seenBracketBracket) {
                        break; // found end sequence!!!!
                    } else {
                        seenBracketBracket = false;
                    }
                    seenBracket = false;
                } else {
                    if (seenBracket) {
                        seenBracket = false;
                    }
                }
                if (normalizeInput) {
                    // deal with normalization issues ...
                    if (ch == '\r') {
                        normalizedCR = true;
                        posStart = cdStart - bufAbsoluteStart;
                        posEnd = pos - 1; // posEnd is alreadys set
                        if (!usePC) {
                            if (posEnd > posStart) {
                                joinPC();
                            } else {
                                usePC = true;
                                pcStart = pcEnd = 0;
                            }
                        }
                        // assert usePC == true;
                        if (pcEnd >= pc.length)
                            ensurePC(pcEnd);
                        pc[pcEnd++] = '\n';
                    } else if (ch == '\n') {
                        if (!normalizedCR && usePC) {
                            if (pcEnd >= pc.length)
                                ensurePC(pcEnd);
                            pc[pcEnd++] = '\n';
                        }
                        normalizedCR = false;
                    } else {
                        if (usePC) {
                            if (pcEnd >= pc.length)
                                ensurePC(pcEnd);
                            pc[pcEnd++] = ch;
                        }
                        normalizedCR = false;
                    }
                }
            }
        } catch (EOFException ex) {
            // detect EOF and create meaningful error ...
            throw new XmlPullParserException(
                    "CDATA section started on line " + curLine + " and column " + curColumn + " was not closed", this, ex);
        }
        if (normalizeInput) {
            if (usePC) {
                pcEnd = pcEnd - 2;
            }
        }
        posStart = cdStart - bufAbsoluteStart;
        posEnd = pos - 3;
    }

    protected void fillBuf() throws IOException, XmlPullParserException {
        if (reader == null)
            throw new XmlPullParserException("reader must be set before parsing is started");

        // see if we are in compaction area
        if (bufEnd > bufSoftLimit) {

            // expand buffer it makes sense!!!!
            boolean compact = bufStart > bufSoftLimit;
            boolean expand = false;
            if (preventBufferCompaction) {
                compact = false;
                expand = true;
            } else if (!compact) {
                // freeSpace
                if (bufStart < buf.length / 2) {
                    // less then half buffer available forcompactin --> expand
                    // instead!!!
                    expand = true;
                } else {
                    // at least half of buffer can be reclaimed --> worthwhile
                    // effort!!!
                    compact = true;
                }
            }

            // if buffer almost full then compact it
            if (compact) {
                // TODO: look on trashing
                // //assert bufStart > 0
                System.arraycopy(buf, bufStart, buf, 0, bufEnd - bufStart);
                if (TRACE_SIZING)
                    System.out.println("TRACE_SIZING fillBuf() compacting " + bufStart + " bufEnd=" + bufEnd + " pos=" + pos
                                       + " posStart=" + posStart + " posEnd=" + posEnd
                                       + " buf first 100 chars:"
                                       + new String(buf, bufStart, bufEnd - bufStart < 100 ? bufEnd - bufStart : 100));

            } else if (expand) {
                final int newSize = 2 * buf.length;
                final char newBuf[] = new char[newSize];
                if (TRACE_SIZING)
                    System.out.println("TRACE_SIZING fillBuf() " + buf.length + " => " + newSize);
                System.arraycopy(buf, bufStart, newBuf, 0, bufEnd - bufStart);
                buf = newBuf;
                if (bufLoadFactor > 0) {
                    // bufSoftLimit = ( bufLoadFactor * buf.length ) /100;
                    bufSoftLimit = (int) ((((long) bufLoadFactor) * buf.length) / 100);
                }

            } else {
                throw new XmlPullParserException("internal error in fillBuffer()");
            }
            bufEnd -= bufStart;
            pos -= bufStart;
            posStart -= bufStart;
            posEnd -= bufStart;
            bufAbsoluteStart += bufStart;
            bufStart = 0;
            if (TRACE_SIZING)
                System.out.println("TRACE_SIZING fillBuf() after bufEnd=" + bufEnd + " pos=" + pos + " posStart=" + posStart
                                   + " posEnd=" + posEnd + " buf first 100 chars:"
                                   + new String(buf, 0, bufEnd < 100 ? bufEnd : 100));
        }
        // at least one character must be read or error
        final int len = buf.length - bufEnd > READ_CHUNK_SIZE ? READ_CHUNK_SIZE : buf.length - bufEnd;
        final int ret = reader.read(buf, bufEnd, len);
        if (ret > 0) {
            bufEnd += ret;
            if (TRACE_SIZING)
                System.out.println("TRACE_SIZING fillBuf() after filling in buffer" + " buf first 100 chars:"
                                   + new String(buf, 0, bufEnd < 100 ? bufEnd : 100));

            return;
        }
        if (ret == -1) {
            if (bufAbsoluteStart == 0 && pos == 0) {
                throw new EOFException("input contained no data");
            } else {
                if (seenRoot && depth == 0) { // inside parsing epilog!!!
                    reachedEnd = true;
                    return;
                } else {
                    StringBuilder expectedTagStack = new StringBuilder();
                    if (depth > 0) {
                        // final char[] cbuf = elRawName[depth];
                        // final String startname = new String(cbuf, 0,
                        // elRawNameEnd[depth]);
                        expectedTagStack.append(" - expected end tag");
                        if (depth > 1) {
                            expectedTagStack.append("s"); // more than one end
                                                         // tag
                        }
                        expectedTagStack.append(" ");
                        for (int i = depth; i > 0; i--) {
                            String tagName = new String(elRawName[i], 0, elRawNameEnd[i]);
                            expectedTagStack.append("</").append(tagName).append('>');
                        }
                        expectedTagStack.append(" to close");
                        for (int i = depth; i > 0; i--) {
                            if (i != depth) {
                                expectedTagStack.append(" and"); // more than
                                                                // one end tag
                            }
                            String tagName = new String(elRawName[i], 0, elRawNameEnd[i]);
                            expectedTagStack.append(" start tag <").append(tagName).append('>');
                            expectedTagStack.append(" from line ").append(elRawNameLine[i]);
                        }
                        expectedTagStack.append(", parser stopped on");
                    }
                    throw new EOFException("no more data available" + expectedTagStack.toString() + getPositionDescription());
                }
            }
        } else {
            throw new IOException("error reading input, returned " + ret);
        }
    }

    protected char more() throws IOException, XmlPullParserException {
        if (pos >= bufEnd) {
            fillBuf();
            // this return value should be ignonored as it is used in epilog
            // parsing ...
            if (reachedEnd)
                return (char) -1;
        }
        final char ch = buf[pos++];
        // line/columnNumber
        if (ch == '\n') {
            ++lineNumber;
            columnNumber = 1;
        } else {
            ++columnNumber;
        }
        // System.out.print(ch);
        return ch;
    }

    // /**
    // * This function returns position of parser in XML input stream
    // * (how many <b>characters</b> were processed.
    // * <p><b>NOTE:</b> this logical position and not byte offset as encodings
    // * such as UTF8 may use more than one byte to encode one character.
    // */
    // public int getCurrentInputPosition() {
    // return pos + bufAbsoluteStart;
    // }

    protected void ensurePC(int end) {
        // assert end >= pc.length;
        final int newSize = end > READ_CHUNK_SIZE ? 2 * end : 2 * READ_CHUNK_SIZE;
        final char[] newPC = new char[newSize];
        if (TRACE_SIZING)
            System.out.println("TRACE_SIZING ensurePC() " + pc.length + " ==> " + newSize + " end=" + end);
        System.arraycopy(pc, 0, newPC, 0, pcEnd);
        pc = newPC;
        // assert end < pc.length;
    }

    protected void joinPC() {
        // assert usePC == false;
        // assert posEnd > posStart;
        final int len = posEnd - posStart;
        final int newEnd = pcEnd + len + 1;
        if (newEnd >= pc.length)
            ensurePC(newEnd); // add 1 for extra space for one char
        // assert newEnd < pc.length;
        System.arraycopy(buf, posStart, pc, pcEnd, len);
        pcEnd += len;
        usePC = true;

    }

    protected char requireInput(char ch, char[] input) throws XmlPullParserException, IOException {
        for (int i = 0; i < input.length; i++) {
            if (ch != input[i]) {
                throw new XmlPullParserException(
                        "expected " + printable(input[i]) + " in " + new String(input) + " and not " + printable(ch), this,
                        null);
            }
            ch = more();
        }
        return ch;
    }

    protected char requireNextS() throws XmlPullParserException, IOException {
        final char ch = more();
        if (!isS(ch)) {
            throw new XmlPullParserException("white space is required and not " + printable(ch), this, null);
        }
        return skipS(ch);
    }

    protected char skipS(char ch) throws XmlPullParserException, IOException {
        while (isS(ch)) {
            ch = more();
        } // skip additional spaces
        return ch;
    }

    // nameStart / name lookup tables based on XML 1.1
    // http://www.w3.org/TR/2001/WD-xml11-20011213/
    protected static final int LOOKUP_MAX = 0x400;
    protected static final char LOOKUP_MAX_CHAR = (char) LOOKUP_MAX;
    // protected static int lookupNameStartChar[] = new int[ LOOKUP_MAX_CHAR /
    // 32 ];
    // protected static int lookupNameChar[] = new int[ LOOKUP_MAX_CHAR / 32 ];
    protected static boolean[] lookupNameStartChar = new boolean[LOOKUP_MAX];
    protected static boolean[] lookupNameChar = new boolean[LOOKUP_MAX];

    private static void setName(char ch)
    // { lookupNameChar[ (int)ch / 32 ] |= (1 << (ch % 32)); }
    {
        lookupNameChar[ch] = true;
    }

    private static void setNameStart(char ch)
    // { lookupNameStartChar[ (int)ch / 32 ] |= (1 << (ch % 32)); setName(ch); }
    {
        lookupNameStartChar[ch] = true;
        setName(ch);
    }

    static {
        setNameStart(':');
        for (char ch = 'A'; ch <= 'Z'; ++ch)
            setNameStart(ch);
        setNameStart('_');
        for (char ch = 'a'; ch <= 'z'; ++ch)
            setNameStart(ch);
        for (char ch = '\u00c0'; ch <= '\u02FF'; ++ch)
            setNameStart(ch);
        for (char ch = '\u0370'; ch <= '\u037d'; ++ch)
            setNameStart(ch);
        for (char ch = '\u037f'; ch < '\u0400'; ++ch)
            setNameStart(ch);

        setName('-');
        setName('.');
        for (char ch = '0'; ch <= '9'; ++ch)
            setName(ch);
        setName('\u00b7');
        for (char ch = '\u0300'; ch <= '\u036f'; ++ch)
            setName(ch);
    }

    // private final static boolean isNameStartChar(char ch) {
    protected boolean isNameStartChar(char ch) {
        return (ch < LOOKUP_MAX_CHAR && lookupNameStartChar[ch]) || (ch >= LOOKUP_MAX_CHAR && ch <= '\u2027')
                || (ch >= '\u202A' && ch <= '\u218F')
                || (ch >= '\u2800' && ch <= '\uFFEF');

        // if(ch < LOOKUP_MAX_CHAR) return lookupNameStartChar[ ch ];
        // else return ch <= '\u2027'
        // || (ch >= '\u202A' && ch <= '\u218F')
        // || (ch >= '\u2800' && ch <= '\uFFEF')
        // ;
        // return false;
        // return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || ch ==
        // ':'
        // || (ch >= '0' && ch <= '9');
        // if(ch < LOOKUP_MAX_CHAR) return (lookupNameStartChar[ (int)ch / 32 ]
        // & (1 << (ch % 32))) != 0;
        // if(ch <= '\u2027') return true;
        // //[#x202A-#x218F]
        // if(ch < '\u202A') return false;
        // if(ch <= '\u218F') return true;
        // // added pairts [#x2800-#xD7FF] | [#xE000-#xFDCF] | [#xFDE0-#xFFEF] |
        // [#x10000-#x10FFFF]
        // if(ch < '\u2800') return false;
        // if(ch <= '\uFFEF') return true;
        // return false;

        // else return (supportXml11 && ( (ch < '\u2027') || (ch > '\u2029' &&
        // ch < '\u2200') ...
    }

    // private final static boolean isNameChar(char ch) {
    protected boolean isNameChar(char ch) {
        // return isNameStartChar(ch);

        // if(ch < LOOKUP_MAX_CHAR) return (lookupNameChar[ (int)ch / 32 ] & (1
        // << (ch % 32))) != 0;

        return (ch < LOOKUP_MAX_CHAR && lookupNameChar[ch]) || (ch >= LOOKUP_MAX_CHAR && ch <= '\u2027')
                || (ch >= '\u202A' && ch <= '\u218F')
                || (ch >= '\u2800' && ch <= '\uFFEF');
        // return false;
        // return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || ch ==
        // ':'
        // || (ch >= '0' && ch <= '9');
        // if(ch < LOOKUP_MAX_CHAR) return (lookupNameStartChar[ (int)ch / 32 ]
        // & (1 << (ch % 32))) != 0;

        // else return
        // else if(ch <= '\u2027') return true;
        // //[#x202A-#x218F]
        // else if(ch < '\u202A') return false;
        // else if(ch <= '\u218F') return true;
        // // added pairts [#x2800-#xD7FF] | [#xE000-#xFDCF] | [#xFDE0-#xFFEF] |
        // [#x10000-#x10FFFF]
        // else if(ch < '\u2800') return false;
        // else if(ch <= '\uFFEF') return true;
        // else return false;
    }

    protected boolean isS(char ch) {
        return (ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t');
        // || (supportXml11 && (ch == '\u0085' || ch == '\u2028');
    }

    // protected boolean isChar(char ch) { return (ch < '\uD800' || ch >
    // '\uDFFF')
    // ch != '\u0000' ch < '\uFFFE'

    // protected char printable(char ch) { return ch; }
    protected String printable(char ch) {
        if (ch == '\n') {
            return "\\n";
        } else if (ch == '\r') {
            return "\\r";
        } else if (ch == '\t') {
            return "\\t";
        } else if (ch == '\'') {
            return "\\'";
        }
        if (ch > 127 || ch < 32) {
            return "\\u" + Integer.toHexString((int) ch);
        }
        return String.valueOf(ch);
    }

    protected String printable(String s) {
        if (s == null)
            return null;
        final int sLen = s.length();
        StringBuilder buf = new StringBuilder(sLen + 10);
        for (int i = 0; i < sLen; ++i) {
            buf.append(printable(s.charAt(i)));
        }
        s = buf.toString();
        return s;
    }
}

/*
 * Indiana University Extreme! Lab Software License, Version 1.2 Copyright (C)
 * 2003 The Trustees of Indiana University. All rights reserved. Redistribution
 * and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met: 1) All
 * redistributions of source code must retain the above copyright notice, the
 * list of authors in the original source code, this list of conditions and the
 * disclaimer listed in this license; 2) All redistributions in binary form must
 * reproduce the above copyright notice, this list of conditions and the
 * disclaimer listed in this license in the documentation and/or other materials
 * provided with the distribution; 3) Any documentation included with all
 * redistributions must include the following acknowledgement: "This product
 * includes software developed by the Indiana University Extreme! Lab. For
 * further information please visit http://www.extreme.indiana.edu/"
 * Alternatively, this acknowledgment may appear in the software itself, and
 * wherever such third-party acknowledgments normally appear. 4) The name
 * "Indiana University" or "Indiana University Extreme! Lab" shall not be used
 * to endorse or promote products derived from this software without prior
 * written permission from Indiana University. For written permission, please
 * contact http://www.extreme.indiana.edu/. 5) Products derived from this
 * software may not use "Indiana University" name nor may "Indiana University"
 * appear in their name, without prior written permission of the Indiana
 * University. Indiana University provides no reassurances that the source code
 * provided does not infringe the patent or any other intellectual property
 * rights of any other entity. Indiana University disclaims any liability to any
 * recipient for claims brought by any other entity based on infringement of
 * intellectual property rights or otherwise. LICENSEE UNDERSTANDS THAT SOFTWARE
 * IS PROVIDED "AS IS" FOR WHICH NO WARRANTIES AS TO CAPABILITIES OR ACCURACY
 * ARE MADE. INDIANA UNIVERSITY GIVES NO WARRANTIES AND MAKES NO REPRESENTATION
 * THAT SOFTWARE IS FREE OF INFRINGEMENT OF THIRD PARTY PATENT, COPYRIGHT, OR
 * OTHER PROPRIETARY RIGHTS. INDIANA UNIVERSITY MAKES NO WARRANTIES THAT
 * SOFTWARE IS FREE FROM "BUGS", "VIRUSES", "TROJAN HORSES", "TRAP
 * DOORS", "WORMS", OR OTHER HARMFUL CODE. LICENSEE ASSUMES THE ENTIRE RISK AS
 * TO THE PERFORMANCE OF SOFTWARE AND/OR ASSOCIATED MATERIALS, AND TO THE
 * PERFORMANCE AND VALIDITY OF INFORMATION GENERATED USING SOFTWARE.
 */
