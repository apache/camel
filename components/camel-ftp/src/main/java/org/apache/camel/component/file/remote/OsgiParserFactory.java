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
package org.apache.camel.component.file.remote;

import java.util.Locale;
import java.util.regex.Pattern;

import org.apache.camel.spi.ClassResolver;
import org.apache.commons.net.ftp.Configurable;
import org.apache.commons.net.ftp.FTPClientConfig;
import org.apache.commons.net.ftp.FTPFileEntryParser;
import org.apache.commons.net.ftp.parser.CompositeFileEntryParser;
import org.apache.commons.net.ftp.parser.DefaultFTPFileEntryParserFactory;
import org.apache.commons.net.ftp.parser.MVSFTPEntryParser;
import org.apache.commons.net.ftp.parser.MacOsPeterFTPEntryParser;
import org.apache.commons.net.ftp.parser.NTFTPEntryParser;
import org.apache.commons.net.ftp.parser.NetwareFTPEntryParser;
import org.apache.commons.net.ftp.parser.OS2FTPEntryParser;
import org.apache.commons.net.ftp.parser.OS400FTPEntryParser;
import org.apache.commons.net.ftp.parser.ParserInitializationException;
import org.apache.commons.net.ftp.parser.UnixFTPEntryParser;
import org.apache.commons.net.ftp.parser.VMSVersioningFTPEntryParser;

/**
 * OsgiParserFactory commons-net DefaultFTPFileEntryParserFactory uses
 * Class.forName, and fails to load custom ParserFactories in OSGI. This class
 * is an alternative ParserFactory that can be used when Camel is used in an
 * OSGI environment.
 */
public class OsgiParserFactory extends DefaultFTPFileEntryParserFactory {
    // Match a plain Java Identifier
    private static final String JAVA_IDENTIFIER = "\\p{javaJavaIdentifierStart}(\\p{javaJavaIdentifierPart})*";
    // Match a qualified name, e.g. a.b.c.Name - but don't allow the default
    // package as that would allow "VMS"/"UNIX" etc.
    private static final String JAVA_QUALIFIED_NAME = "(" + JAVA_IDENTIFIER + "\\.)+" + JAVA_IDENTIFIER;
    // Create the pattern, as it will be reused many times
    private static final Pattern JAVA_QUALIFIED_NAME_PATTERN = Pattern.compile(JAVA_QUALIFIED_NAME);

    private ClassResolver ocr;

    public OsgiParserFactory(ClassResolver ocr) {
        this.ocr = ocr;
    }

    /**
     * setClassResolver sets a class resolver which can be used instead of
     * Class.forName for class resolution.
     * 
     * @param ocr Class Resolver
     */
    public void setClassResolver(ClassResolver ocr) {
        this.ocr = ocr;
    }

    @Override
    public FTPFileEntryParser createFileEntryParser(String key) {
        if (key == null) {
            throw new ParserInitializationException("Parser key cannot be null");
        }
        return createFileEntryParser(key, null);
    }

    @Override
    public FTPFileEntryParser createFileEntryParser(FTPClientConfig config) throws ParserInitializationException {
        String key = config.getServerSystemKey();
        return createFileEntryParser(key, config);
    }

    private FTPFileEntryParser createFileEntryParser(String key, FTPClientConfig config) {
        FTPFileEntryParser parser = null;

        // Is the key a possible class name?
        if (JAVA_QUALIFIED_NAME_PATTERN.matcher(key).matches()) {
            Class<?> parserClass = ocr.resolveClass(key);
            try {
                parser = (FTPFileEntryParser)parserClass.newInstance();
            } catch (ClassCastException e) {
                throw new ParserInitializationException(parserClass.getName() + " does not implement the interface " + "org.apache.commons.net.ftp.FTPFileEntryParser.", e);
            } catch (Exception | ExceptionInInitializerError e) {
                throw new ParserInitializationException("Error initializing parser", e);
            }
        }
        if (parser == null) {
            String ukey = key.toUpperCase(Locale.ENGLISH);
            if (ukey.contains("UNIX")) {
                parser = new UnixFTPEntryParser(config);
            } else if (ukey.contains("LINUX")) {
                parser = new UnixFTPEntryParser(config);
            } else if (ukey.contains("VMS")) {
                parser = new VMSVersioningFTPEntryParser(config);
            } else if (ukey.contains("WINDOWS")) {
                parser = createNTFTPEntryParser(config);
            } else if (ukey.contains("WIN32")) {
                parser = createNTFTPEntryParser(config);
            } else if (ukey.contains("OS/2")) {
                parser = new OS2FTPEntryParser(config);
            } else if ((ukey.contains("OS/400")) || (ukey.contains("AS/400"))) {
                parser = createOS400FTPEntryParser(config);
            } else if (ukey.contains("MVS")) {
                parser = new MVSFTPEntryParser();
            } else if (ukey.contains("NETWARE")) {
                parser = new NetwareFTPEntryParser(config);
            } else if (ukey.contains("MACOS PETER")) {
                parser = new MacOsPeterFTPEntryParser(config);
            } else if (ukey.contains("TYPE: L8")) {
                parser = new UnixFTPEntryParser(config);
            } else {
                throw new ParserInitializationException("Unknown parser type: " + key);
            }
        }

        if (parser instanceof Configurable) {
            ((Configurable)parser).configure(config);
        }

        return parser;
    }

    /**
     * Creates an NT FTP parser: if the config exists, and the system key equals
     * {@link FTPClientConfig#SYST_NT} then a plain {@link NTFTPEntryParser} is
     * used, otherwise a composite of {@link NTFTPEntryParser} and
     * {@link UnixFTPEntryParser} is used.
     * 
     * @param config the config to use, may be {@code null}
     * @return the parser
     */
    private FTPFileEntryParser createNTFTPEntryParser(FTPClientConfig config) {
        if (config != null && FTPClientConfig.SYST_NT.equals(config.getServerSystemKey())) {
            return new NTFTPEntryParser(config);
        } else {
            return new CompositeFileEntryParser(new FTPFileEntryParser[] {new NTFTPEntryParser(config), new UnixFTPEntryParser(config)});
        }
    }

    /**
     * Creates an OS400 FTP parser: if the config exists, and the system key
     * equals {@link FTPClientConfig#SYST_OS400} then a plain
     * {@link OS400FTPEntryParser} is used, otherwise a composite of
     * {@link OS400FTPEntryParser} and {@link UnixFTPEntryParser} is used.
     * 
     * @param config the config to use, may be {@code null}
     * @return the parser
     */
    private FTPFileEntryParser createOS400FTPEntryParser(FTPClientConfig config) {
        if (config != null && FTPClientConfig.SYST_OS400.equals(config.getServerSystemKey())) {
            return new OS400FTPEntryParser(config);
        } else {
            return new CompositeFileEntryParser(new FTPFileEntryParser[] {new OS400FTPEntryParser(config), new UnixFTPEntryParser(config)});
        }
    }
}
