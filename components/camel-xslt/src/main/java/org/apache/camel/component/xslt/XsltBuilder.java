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
package org.apache.camel.component.xslt;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.URIResolver;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;

import org.xml.sax.EntityResolver;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.support.builder.xml.XMLConverterHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.util.ObjectHelper.notNull;

/**
 * Creates a <a href="http://camel.apache.org/processor.html">Processor</a> which performs an XSLT transformation of the
 * IN message body.
 * <p/>
 * Will by default output the result as a String. You can choose which kind of output you want using the
 * <tt>outputXXX</tt> methods.
 */
public class XsltBuilder implements Processor {

    protected static final Logger LOG = LoggerFactory.getLogger(XsltBuilder.class);
    private Map<String, Object> parameters = new HashMap<>();
    private Templates template;
    private volatile BlockingQueue<Transformer> transformers;
    private volatile SourceHandlerFactory sourceHandlerFactory;
    private ResultHandlerFactory resultHandlerFactory = new StringResultHandlerFactory();
    private boolean failOnNullBody = true;
    private URIResolver uriResolver;
    private boolean deleteOutputFile;
    private ErrorListener errorListener;
    private EntityResolver entityResolver;
    private XsltMessageLogger xsltMessageLogger;

    private final XMLConverterHelper converter = new XMLConverterHelper();
    private final Object sourceHandlerFactoryLock = new Object();

    public XsltBuilder() {
    }

    public XsltBuilder(Templates templates) {
        this.template = templates;
    }

    @Override
    public String toString() {
        return "XSLT[" + template + "]";
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        notNull(getTemplate(), "template");

        if (isDeleteOutputFile()) {
            // add on completion, so we can delete the file when the Exchange is done
            String fileName = ExchangeHelper.getMandatoryHeader(exchange, XsltConstants.XSLT_FILE_NAME, String.class);
            exchange.getExchangeExtension().addOnCompletion(new XsltBuilderOnCompletion(fileName));
        }

        Transformer transformer = getTransformer();
        configureTransformer(transformer, exchange);

        ResultHandler resultHandler = resultHandlerFactory.createResult(exchange);
        Result result = resultHandler.getResult();
        // let's copy the headers before we invoke the transform in case they modify them
        Message out = exchange.getOut();
        out.copyFrom(exchange.getIn());

        // the underlying input stream, which we need to close to avoid locking files or other resources
        InputStream is = null;
        try {
            Source source = getSourceHandlerFactory().getSource(exchange);

            source = prepareSource(source);

            if (source instanceof SAXSource) {
                tryAddEntityResolver((SAXSource) source);
            }

            LOG.trace("Using {} as source", source);
            transformer.transform(source, result);
            LOG.trace("Transform complete with result {}", result);
            resultHandler.setBody(out);
        } finally {
            releaseTransformer(transformer);
            // IOHelper can handle if null
            IOHelper.close(is);
        }
    }

    /**
     * Allows to prepare the source before transforming.
     */
    protected Source prepareSource(Source source) {
        return source;
    }

    // Builder methods
    // -------------------------------------------------------------------------

    /**
     * Creates an XSLT processor using the given templates instance
     */
    public static XsltBuilder xslt(Templates templates) {
        return new XsltBuilder(templates);
    }

    /**
     * Creates an XSLT processor using the given XSLT source
     */
    public static XsltBuilder xslt(Source xslt) throws TransformerConfigurationException {
        notNull(xslt, "xslt");
        XsltBuilder answer = new XsltBuilder();
        answer.setTransformerSource(xslt);
        return answer;
    }

    /**
     * Creates an XSLT processor using the given XSLT source
     */
    public static XsltBuilder xslt(File xslt) throws TransformerConfigurationException {
        notNull(xslt, "xslt");
        return xslt(new StreamSource(xslt));
    }

    /**
     * Creates an XSLT processor using the given XSLT source
     */
    public static XsltBuilder xslt(URL xslt) throws TransformerConfigurationException, IOException {
        notNull(xslt, "xslt");
        return xslt(xslt.openStream());
    }

    /**
     * Creates an XSLT processor using the given XSLT source
     */
    public static XsltBuilder xslt(InputStream xslt) throws TransformerConfigurationException {
        notNull(xslt, "xslt");
        return xslt(new StreamSource(xslt));
    }

    /**
     * Sets the output as being a byte[]
     */
    public XsltBuilder outputBytes() {
        setResultHandlerFactory(new StreamResultHandlerFactory());
        return this;
    }

    /**
     * Sets the output as being a String
     */
    public XsltBuilder outputString() {
        setResultHandlerFactory(new StringResultHandlerFactory());
        return this;
    }

    /**
     * Sets the output as being a DOM
     */
    public XsltBuilder outputDOM() {
        setResultHandlerFactory(new DomResultHandlerFactory());
        return this;
    }

    /**
     * Sets the output as being a File where the filename must be provided in the {@link Exchange#XSLT_FILE_NAME}
     * header.
     */
    public XsltBuilder outputFile() {
        setResultHandlerFactory(new FileResultHandlerFactory());
        return this;
    }

    /**
     * Should the output file be deleted when the {@link Exchange} is done.
     * <p/>
     * This option should only be used if you use {@link #outputFile()} as well.
     */
    public XsltBuilder deleteOutputFile() {
        this.deleteOutputFile = true;
        return this;
    }

    public XsltBuilder parameter(String name, Object value) {
        parameters.put(name, value);
        return this;
    }

    /**
     * Sets a custom URI resolver to be used
     */
    public XsltBuilder uriResolver(URIResolver uriResolver) {
        setUriResolver(uriResolver);
        return this;
    }

    /**
     * Used for caching {@link Transformer}s.
     * <p/>
     * By default no caching is in use.
     *
     * @param numberToCache the maximum number of transformers to cache
     */
    public XsltBuilder transformerCacheSize(int numberToCache) {
        if (numberToCache > 0) {
            transformers = new ArrayBlockingQueue<>(numberToCache);
        } else {
            transformers = null;
        }
        return this;
    }

    /**
     * Uses a custom {@link javax.xml.transform.ErrorListener}.
     */
    public XsltBuilder errorListener(ErrorListener errorListener) {
        setErrorListener(errorListener);
        return this;
    }

    public XsltBuilder xsltMessageLogger(XsltMessageLogger xsltMessageLogger) {
        setXsltMessageLogger(xsltMessageLogger);
        return this;
    }

    // Properties
    // -------------------------------------------------------------------------

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public void setTemplate(Templates template) {
        this.template = template;
        if (transformers != null) {
            transformers.clear();
        }
    }

    public Templates getTemplate() {
        return template;
    }

    public boolean isFailOnNullBody() {
        return failOnNullBody;
    }

    public void setFailOnNullBody(boolean failOnNullBody) {
        this.failOnNullBody = failOnNullBody;
    }

    public SourceHandlerFactory getSourceHandlerFactory() {
        if (this.sourceHandlerFactory == null) {
            synchronized (this.sourceHandlerFactoryLock) {
                if (this.sourceHandlerFactory == null) {
                    final XmlSourceHandlerFactoryImpl xmlSourceHandlerFactory = createXmlSourceHandlerFactoryImpl();
                    xmlSourceHandlerFactory.setFailOnNullBody(isFailOnNullBody());
                    this.sourceHandlerFactory = xmlSourceHandlerFactory;
                }
            }
        }

        return this.sourceHandlerFactory;
    }

    protected XmlSourceHandlerFactoryImpl createXmlSourceHandlerFactoryImpl() {
        return new XmlSourceHandlerFactoryImpl();
    }

    public void setSourceHandlerFactory(SourceHandlerFactory sourceHandlerFactory) {
        this.sourceHandlerFactory = sourceHandlerFactory;
    }

    public ResultHandlerFactory getResultHandlerFactory() {
        return resultHandlerFactory;
    }

    public void setResultHandlerFactory(ResultHandlerFactory resultHandlerFactory) {
        this.resultHandlerFactory = resultHandlerFactory;
    }

    protected Templates createTemplates(TransformerFactory factory, Source source) throws TransformerConfigurationException {
        return factory.newTemplates(source);
    }

    /**
     * Sets the XSLT transformer from a Source
     *
     * @param  source                            the source
     * @throws TransformerConfigurationException is thrown if creating a XSLT transformer failed.
     */
    public void setTransformerSource(Source source) throws TransformerConfigurationException {
        TransformerFactory factory = converter.getTransformerFactory();
        if (errorListener != null) {
            factory.setErrorListener(errorListener);
        } else {
            // use a logger error listener so users can see from the logs what the error may be
            factory.setErrorListener(new XsltErrorListener());
        }
        if (getUriResolver() != null) {
            factory.setURIResolver(getUriResolver());
        }

        // Check that the call to createTemplates() returns a valid template instance.
        // In case of a xslt parse error, it will return null, and we should stop the
        // deployment and raise an exception as the route will not be setup properly.
        Templates templates = createTemplates(factory, source);
        if (templates != null) {
            setTemplate(templates);
        } else {
            throw new TransformerConfigurationException(
                    "Error creating XSLT template. "
                                                        + "This is most likely be caused by a XML parse error. "
                                                        + "Please verify your XSLT file configured.");
        }
    }

    /**
     * Sets the XSLT transformer from a File
     */
    public void setTransformerFile(File xslt) throws TransformerConfigurationException {
        setTransformerSource(new StreamSource(xslt));
    }

    /**
     * Sets the XSLT transformer from a URL
     */
    public void setTransformerURL(URL url) throws TransformerConfigurationException, IOException {
        notNull(url, "url");
        setTransformerInputStream(url.openStream());
    }

    /**
     * Sets the XSLT transformer from the given input stream
     */
    public void setTransformerInputStream(InputStream in) throws TransformerConfigurationException {
        notNull(in, "InputStream");
        setTransformerSource(new StreamSource(in));
    }

    public URIResolver getUriResolver() {
        return uriResolver;
    }

    public void setUriResolver(URIResolver uriResolver) {
        this.uriResolver = uriResolver;
    }

    public void setEntityResolver(EntityResolver entityResolver) {
        this.entityResolver = entityResolver;
    }

    public boolean isDeleteOutputFile() {
        return deleteOutputFile;
    }

    public void setDeleteOutputFile(boolean deleteOutputFile) {
        this.deleteOutputFile = deleteOutputFile;
    }

    public ErrorListener getErrorListener() {
        return errorListener;
    }

    public void setErrorListener(ErrorListener errorListener) {
        this.errorListener = errorListener;
    }

    // Implementation methods
    // -------------------------------------------------------------------------
    public void setTransformerFactory(TransformerFactory transformerFactory) {
        this.converter.setTransformerFactory(transformerFactory);
    }

    public XsltMessageLogger getXsltMessageLogger() {
        return xsltMessageLogger;
    }

    public void setXsltMessageLogger(XsltMessageLogger xsltMessageLogger) {
        this.xsltMessageLogger = xsltMessageLogger;
    }

    private void releaseTransformer(Transformer transformer) {
        if (transformers != null) {
            transformer.reset();
            boolean result = transformers.offer(transformer);
            if (!result) {
                LOG.error("failed to offer() a transform");
            }
        }
    }

    private Transformer getTransformer() throws Exception {
        Transformer t = null;
        if (transformers != null) {
            t = transformers.poll();
        }
        if (t == null) {
            t = createTransformer();
        }
        return t;
    }

    protected Transformer createTransformer() throws Exception {
        return getTemplate().newTransformer();
    }

    private void tryAddEntityResolver(SAXSource source) {
        //expecting source to have not null XMLReader
        if (this.entityResolver != null && source != null) {
            source.getXMLReader().setEntityResolver(this.entityResolver);
        }
    }

    /**
     * Configures the transformer with exchange specific parameters
     */
    protected void configureTransformer(Transformer transformer, Exchange exchange) throws Exception {
        if (uriResolver == null) {
            uriResolver = new XsltUriResolver(exchange.getContext(), null);
        }
        transformer.setURIResolver(uriResolver);
        if (errorListener == null) {
            // set our error listener, so we can capture errors and report them back on the exchange
            transformer.setErrorListener(new DefaultTransformErrorHandler(exchange));
        } else {
            // use custom error listener
            transformer.setErrorListener(errorListener);
        }

        transformer.clearParameters();
        addParameters(transformer, exchange.getProperties());
        addParameters(transformer, exchange.getIn().getHeaders());
        addParameters(transformer, getParameters());
        transformer.setParameter("exchange", exchange);
        transformer.setParameter("in", exchange.getIn());
        transformer.setParameter("out", exchange.getOut());
    }

    protected void addParameters(Transformer transformer, Map<String, Object> map) {
        Set<Map.Entry<String, Object>> propertyEntries = map.entrySet();
        for (Map.Entry<String, Object> entry : propertyEntries) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value != null) {
                LOG.trace("Transformer set parameter {} -> {}", key, value);
                transformer.setParameter(key, value);
            }
        }
    }

    private static final class XsltBuilderOnCompletion extends SynchronizationAdapter {
        private final String fileName;

        private XsltBuilderOnCompletion(String fileName) {
            this.fileName = fileName;
        }

        @Override
        public void onDone(Exchange exchange) {
            FileUtil.deleteFile(new File(fileName));
        }

        @Override
        public String toString() {
            return "XsltBuilderOnCompletion";
        }
    }

}
