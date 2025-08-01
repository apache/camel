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
package org.apache.camel.cli.connector;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Expression;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.Route;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.builder.ModelRoutesBuilder;
import org.apache.camel.console.DevConsole;
import org.apache.camel.console.DevConsoleRegistry;
import org.apache.camel.model.HasExpressionType;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.ProcessorDefinitionHelper;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.spi.CliConnector;
import org.apache.camel.spi.CliConnectorFactory;
import org.apache.camel.spi.ContextReloadStrategy;
import org.apache.camel.spi.EndpointUriFactory;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.ResourceLoader;
import org.apache.camel.spi.ResourceReloadStrategy;
import org.apache.camel.spi.RoutesLoader;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.concurrent.ThreadHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CLI Connector for local management of Camel integrations from Camel JBang.
 */
public class LocalCliConnector extends ServiceSupport implements CliConnector, CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(LocalCliConnector.class);

    private static final int BODY_MAX_CHARS = 128 * 1024;

    private final CliConnectorFactory cliConnectorFactory;
    private CamelContext camelContext;
    private int delay = 1000;
    private long counter;
    private String platform;
    private String platformVersion;
    private String mainClass;
    private final AtomicBoolean terminating = new AtomicBoolean();
    private ScheduledExecutorService executor;
    private volatile ExecutorService terminateExecutor;
    private ProducerTemplate producer;
    private ConsumerTemplate consumer;
    private File lockFile;
    private File statusFile;
    private File actionFile;
    private File outputFile;
    private File traceFile;
    private long traceFilePos;   // keep track of trace offset
    private File debugFile;
    private File receiveFile;
    private long receiveFilePos; // keep track of receive offset
    private byte[] lastSource;
    private ExpressionDefinition lastSourceExpression;

    public LocalCliConnector(CliConnectorFactory cliConnectorFactory) {
        this.cliConnectorFactory = cliConnectorFactory;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    protected void doStart() throws Exception {
        terminating.set(false);

        // what platform are we running
        mainClass = cliConnectorFactory.getRuntimeStartClass();
        if (mainClass == null) {
            mainClass = camelContext.getGlobalOption("CamelMainClass");
        }
        platform = cliConnectorFactory.getRuntime();
        if (platform == null) {
            // use camel context name to guess platform if not specified
            String sn = camelContext.getClass().getSimpleName().toLowerCase(Locale.ROOT);
            if (sn.contains("boot")) {
                platform = "Spring Boot";
            } else if (sn.contains("spring")) {
                platform = "Spring";
            } else if (sn.contains("quarkus")) {
                platform = "Quarkus";
            } else if (sn.contains("osgi")) {
                platform = "Karaf";
            } else if (sn.contains("cdi")) {
                platform = "CDI";
            } else if (camelContext.getName().equals("CamelJBang")) {
                platform = "JBang";
            } else {
                platform = "Camel";
            }
        }
        platformVersion = cliConnectorFactory.getRuntimeVersion();
        producer = camelContext.createProducerTemplate();
        consumer = camelContext.createConsumerTemplate();

        // create thread from JDK so it is not managed by Camel because we want the pool to be independent when
        // camel is being stopped which otherwise can lead to stopping the thread pool while the task is running
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            String threadName = ThreadHelper.resolveThreadName(null, "LocalCliConnector");
            return new Thread(r, threadName);
        });

        lockFile = createLockFile(getPid());
        if (lockFile != null) {
            statusFile = createLockFile(lockFile.getName() + "-status.json");
            actionFile = createLockFile(lockFile.getName() + "-action.json");
            outputFile = createLockFile(lockFile.getName() + "-output.json");
            traceFile = createLockFile(lockFile.getName() + "-trace.json");
            debugFile = createLockFile(lockFile.getName() + "-debug.json");
            receiveFile = createLockFile(lockFile.getName() + "-receive.json");
            executor.scheduleWithFixedDelay(this::task, 0, delay, TimeUnit.MILLISECONDS);
            LOG.info("Camel JBang CLI enabled");
        } else {
            LOG.warn("Cannot create PID file: {}. This integration cannot be managed by Camel JBang CLI.", getPid());
        }
    }

    @Override
    public void sigterm() {
        // we are terminating
        terminating.set(true);

        // spawn a thread that terminates, so we can keep this thread to update status
        terminateExecutor = Executors.newSingleThreadExecutor(r -> {
            String threadName = ThreadHelper.resolveThreadName(null, "Terminate JVM task");
            return new Thread(r, threadName);
        });
        terminateExecutor.submit(new Runnable() {
            @Override
            public void run() {
                LOG.info("Camel JBang terminating JVM");
                try {
                    camelContext.stop();
                } finally {
                    ServiceHelper.stopAndShutdownService(this);
                }
            }
        });
    }

    protected void task() {
        if (!lockFile.exists() && terminating.compareAndSet(false, true)) {
            // if the lock file is deleted then trigger termination
            sigterm();
            return;
        }
        if (!statusFile.exists()) {
            return;
        }

        actionTask();
        statusTask();
        // only run this every 2nd time as gathering this data has more overhead
        // and are only needed when doing tracing/debugging/receive
        if (++counter % 2 == 0) {
            traceTask();
        }
    }

    protected void actionTask() {
        String action = null;
        try {
            JsonObject root = loadAction();
            if (root == null || root.isEmpty()) {
                return;
            }

            if (LOG.isDebugEnabled()) {
                LOG.debug("Action: {}", root);
            }

            action = root.getString("action");
            if ("route".equals(action)) {
                doActionRouteTask(root);
            } else if ("logger".equals(action)) {
                doActionLoggerTask(root);
            } else if ("gc".equals(action)) {
                System.gc();
            } else if ("reload".equals(action)) {
                doActionReloadTask();
            } else if ("debug".equals(action)) {
                doActionDebugTask(root);
            } else if ("reset-stats".equals(action)) {
                doActionResetStatsTask();
            } else if ("thread-dump".equals(action)) {
                doActionThreadDumpTask();
            } else if ("top-processors".equals(action)) {
                doActionTopProcessorsTask();
            } else if ("source".equals(action)) {
                doActionSourceTask(root);
            } else if ("route-dump".equals(action)) {
                doActionRouteDumpTask(root);
            } else if ("route-controller".equals(action)) {
                doActionRouteControllerTask(root);
            } else if ("startup-recorder".equals(action)) {
                doActionStartupRecorder();
            } else if ("stub".equals(action)) {
                doActionStubTask(root);
            } else if ("send".equals(action)) {
                doActionSendTask(root);
            } else if ("transform".equals(action)) {
                doActionTransformTask(root);
            } else if ("bean".equals(action)) {
                doActionBeanTask(root);
            } else if ("kafka".equals(action)) {
                doActionKafkaTask();
            } else if ("trace".equals(action)) {
                doActionTraceTask(root);
            } else if ("browse".equals(action)) {
                doActionBrowseTask(root);
            } else if ("receive".equals(action)) {
                doActionReceiveTask(root);
            }
        } catch (Exception e) {
            // ignore
            LOG.warn("Error executing action: {} due to: {}. This exception is ignored.", action != null ? action : actionFile,
                    e.getMessage(),
                    e);
        } finally {
            // action done so delete file
            FileUtil.deleteFile(actionFile);
        }
    }

    private void doActionTransformTask(JsonObject root) throws Exception {
        StopWatch watch = new StopWatch();
        long timestamp = System.currentTimeMillis();
        String source = root.getString("source");
        String language = root.getString("language");
        String component = root.getString("component");
        String dataformat = root.getString("dataformat");
        String template = Jsoner.unescape(root.getStringOrDefault("template", ""));
        if (component == null && template.startsWith("file:")) {
            template = "resource:" + template;
        }
        String body = Jsoner.unescape(root.getString("body"));
        InputStream is = null;
        Object b = body;
        if (body.startsWith("file:")) {
            File file = new File(body.substring(5));
            is = new FileInputStream(file);
            b = IOHelper.loadText(is);
        }
        final Object inputBody = b;
        Map<String, Object> map = null;
        Collection<JsonObject> headers = root.getCollection("headers");
        if (headers != null) {
            map = new LinkedHashMap<>();
            for (JsonObject jo : headers) {
                map.put(jo.getString("key"), jo.getString("value"));
            }
        }
        final Map<String, Object> inputHeaders = map;
        Map<String, Object> map2 = null;
        Collection<JsonObject> options = root.getCollection("options");
        if (options != null) {
            map2 = new LinkedHashMap<>();
            for (JsonObject jo : options) {
                map2.put(jo.getString("key"), jo.getString("value"));
            }
        }
        final Map<String, Object> inputOptions = map2;
        Exchange out = camelContext.getCamelContextExtension().getExchangeFactory().create(false);
        try {
            if (source != null) {
                Integer sourceLine = LoggerHelper.extractSourceLocationLineNumber(source);
                String sourceId = LoggerHelper.extractSourceLocationId(source);
                source = LoggerHelper.stripSourceLocationLineNumber(source);
                LOG.debug("Source: {} line: {} id: {}", source, sourceLine, sourceId);

                boolean update = true;
                File f = new File(source);
                if (f.isFile() && f.exists()) {
                    byte[] data = Files.readAllBytes(f.toPath());
                    if (Arrays.equals(lastSource, data)) {
                        LOG.debug("Source file: {} is not updated since last", source);
                        update = false;
                    }
                    lastSource = data;
                }
                if (update) {
                    if (sourceLine != null) {
                        LOG.info("Transforming from source: {}:{}", source, sourceLine);
                    } else if (sourceId != null) {
                        LOG.info("Transforming from source: {}:{}", source, sourceId);
                    } else {
                        LOG.info("Transforming from source: {}", source);
                    }

                    // load route definition
                    if (!source.startsWith("file:")) {
                        source = "file:" + source;
                    }
                    // load the source via routes loader, and find the builders, which we can use to get to the model
                    Resource res = camelContext.getCamelContextExtension().getContextPlugin(ResourceLoader.class)
                            .resolveResource(source);
                    RoutesLoader loader = camelContext.getCamelContextExtension().getContextPlugin(RoutesLoader.class);
                    Collection<RoutesBuilder> builders = loader.findRoutesBuilders(res);
                    for (RoutesBuilder builder : builders) {
                        // use the model as we just want to find the EIP with the inlined expression
                        ModelRoutesBuilder mrb = (ModelRoutesBuilder) builder;
                        // must prepare model before we can access them
                        mrb.prepareModel(camelContext);
                        // find the EIP with the inlined expression to use
                        ExpressionDefinition found = null;
                        for (RouteDefinition rd : mrb.getRoutes().getRoutes()) {
                            Collection<ProcessorDefinition> defs
                                    = ProcessorDefinitionHelper.filterTypeInOutputs(rd.getOutputs(),
                                            ProcessorDefinition.class);
                            for (ProcessorDefinition p : defs) {
                                if (p instanceof HasExpressionType et) {
                                    ExpressionDefinition def = et.getExpressionType();
                                    if (def != null) {
                                        if (sourceLine != null) {
                                            if (p.getLineNumber() == -1 || p.getLineNumber() <= sourceLine) {
                                                found = def;
                                            }
                                        } else if (sourceId != null) {
                                            if (sourceId.equals(p.getId()) || sourceId.equals(def.getId())) {
                                                found = def;
                                            }
                                        } else {
                                            found = def;
                                        }
                                    }
                                }
                            }
                            if (found != null) {
                                lastSourceExpression = found;
                            }
                        }
                    }
                }
                if (lastSourceExpression != null) {
                    // create dummy exchange with
                    out.setPattern(ExchangePattern.InOut);
                    out.getMessage().setBody(inputBody);
                    if (inputHeaders != null) {
                        out.getMessage().setHeaders(inputHeaders);
                    }
                    String result = lastSourceExpression.evaluate(out, String.class);
                    out.getMessage().setBody(result);
                }
            } else if (component != null) {
                // transform via component
                out.setPattern(ExchangePattern.InOut);
                out.getMessage().setBody(inputBody);
                if (inputHeaders != null) {
                    out.getMessage().setHeaders(inputHeaders);
                }
                String uri = component + ":" + template;
                // must disable any kind of content cache on the component, so template is always reloaded
                EndpointUriFactory euf = camelContext.getCamelContextExtension().getEndpointUriFactory(component);
                if (euf.propertyNames().contains("contentCache")) {
                    uri = uri + "?contentCache=false";
                }
                if (inputOptions != null) {
                    uri = URISupport.appendParametersToURI(uri, inputOptions);
                }
                out = producer.send(uri, out);
            } else if (dataformat != null) {
                // transform via dataformat
                out.setPattern(ExchangePattern.InOut);
                out.getMessage().setBody(inputBody);
                if (inputHeaders != null) {
                    out.getMessage().setHeaders(inputHeaders);
                }
                String uri = "dataformat:" + dataformat + ":unmarshal";
                if (inputOptions != null) {
                    uri = URISupport.appendParametersToURI(uri, inputOptions);
                }
                out = producer.send(uri, out);
            } else {
                // transform via language
                Language lan = camelContext.resolveLanguage(language);
                Expression exp = lan.createExpression(template);
                // configure expression if options provided
                if (inputOptions != null) {
                    PropertyBindingSupport.build()
                            .withCamelContext(camelContext).withTarget(exp).withProperties(inputOptions).bind();
                }
                exp.init(camelContext);
                // create dummy exchange with
                out.setPattern(ExchangePattern.InOut);
                out.getMessage().setBody(inputBody);
                if (inputHeaders != null) {
                    out.getMessage().setHeaders(inputHeaders);
                }
                String result = exp.evaluate(out, String.class);
                out.getMessage().setBody(result);
            }
            IOHelper.close(is);
        } catch (Exception e) {
            out.setException(e);
        }
        LOG.trace("Updating output file: {}", outputFile);
        if (out.getException() != null) {
            JsonObject jo = new JsonObject();
            if (language != null) {
                jo.put("language", language);
            }
            if (source != null) {
                jo.put("source", source);
            }
            jo.put("exchangeId", out.getExchangeId());
            jo.put("timestamp", timestamp);
            jo.put("elapsed", watch.taken());
            jo.put("status", "failed");
            // avoid double wrap
            jo.put("exception",
                    MessageHelper.dumpExceptionAsJSonObject(out.getException()).getMap("exception"));
            IOHelper.writeText(jo.toJson(), outputFile);
        } else {
            JsonObject jo = new JsonObject();
            if (language != null) {
                jo.put("language", language);
            }
            if (source != null) {
                jo.put("source", source);
            }
            jo.put("exchangeId", out.getExchangeId());
            jo.put("timestamp", timestamp);
            jo.put("elapsed", watch.taken());
            jo.put("status", "success");
            // avoid double wrap
            jo.put("message", MessageHelper.dumpAsJSonObject(out.getMessage(), true, true, true, true, true, true,
                    BODY_MAX_CHARS).getMap("message"));
            IOHelper.writeText(jo.toJson(), outputFile);
        }
        camelContext.getCamelContextExtension().getExchangeFactory().release(out);
    }

    private void doActionSendTask(JsonObject root) throws Exception {
        DevConsole dc = camelContext.getCamelContextExtension().getContextPlugin(DevConsoleRegistry.class)
                .resolveById("send");
        if (dc != null) {

            // wait for camel to be ready
            StopWatch watch = new StopWatch();
            while (!camelContext.isStarted() && watch.taken() < 5000) {
                Thread.sleep(100);
            }

            JsonObject json;
            String endpoint = root.getString("endpoint");
            String body = root.getString("body");
            String exchangePattern = root.getString("exchangePattern");
            String poll = root.getString("poll");
            String pollTimeout = root.getString("pollTimeout");
            final Map<String, Object> args = new LinkedHashMap<>();
            if (endpoint != null) {
                args.put("endpoint", endpoint);
            }
            if (body != null) {
                args.put("body", body);
            }
            if (exchangePattern != null) {
                args.put("exchangePattern", exchangePattern);
            }
            if (poll != null) {
                args.put("poll", poll);
            }
            if (pollTimeout != null) {
                args.put("pollTimeout", pollTimeout);
            }
            Collection<JsonObject> headers = root.getCollection("headers");
            if (headers != null) {
                for (JsonObject jo : headers) {
                    args.put(jo.getString("key"), jo.getString("value"));
                }
            }
            json = (JsonObject) dc.call(DevConsole.MediaType.JSON, args);
            LOG.trace("Updating output file: {}", outputFile);
            IOHelper.writeText(json.toJson(), outputFile);
        } else {
            IOHelper.writeText("{}", outputFile);
        }
    }

    private void doActionStubTask(JsonObject root) throws Exception {
        String filter = root.getString("filter");
        String limit = root.getString("limit");
        String browse = root.getString("browse");

        DevConsole dc = camelContext.getCamelContextExtension().getContextPlugin(DevConsoleRegistry.class)
                .resolveById("stub");
        if (dc != null) {
            JsonObject json = (JsonObject) dc.call(DevConsole.MediaType.JSON,
                    Map.of("filter", filter, "limit", limit, "browse", browse));
            LOG.trace("Updating output file: {}", outputFile);
            IOHelper.writeText(json.toJson(), outputFile);
        } else {
            IOHelper.writeText("{}", outputFile);
        }
    }

    private void doActionStartupRecorder() throws Exception {
        DevConsole dc = camelContext.getCamelContextExtension().getContextPlugin(DevConsoleRegistry.class)
                .resolveById("startup-recorder");
        if (dc != null) {
            JsonObject json = (JsonObject) dc.call(DevConsole.MediaType.JSON);
            LOG.trace("Updating output file: {}", outputFile);
            IOHelper.writeText(json.toJson(), outputFile);
        } else {
            IOHelper.writeText("{}", outputFile);
        }
    }

    private void doActionRouteControllerTask(JsonObject root) throws Exception {
        DevConsole dc = camelContext.getCamelContextExtension().getContextPlugin(DevConsoleRegistry.class)
                .resolveById("route-controller");
        if (dc != null) {
            String stacktrace = root.getString("stacktrace");
            JsonObject json = (JsonObject) dc.call(DevConsole.MediaType.JSON, Map.of("stacktrace", stacktrace));
            LOG.trace("Updating output file: {}", outputFile);
            IOHelper.writeText(json.toJson(), outputFile);
        } else {
            IOHelper.writeText("{}", outputFile);
        }
    }

    private void doActionRouteDumpTask(JsonObject root) throws Exception {
        DevConsole dc = camelContext.getCamelContextExtension().getContextPlugin(DevConsoleRegistry.class)
                .resolveById("route-dump");
        if (dc != null) {
            String filter = root.getString("filter");
            String format = root.getString("format");
            String uriAsParameters = root.getString("uriAsParameters");
            JsonObject json
                    = (JsonObject) dc.call(DevConsole.MediaType.JSON,
                            Map.of("filter", filter, "format", format, "uriAsParameters", uriAsParameters));
            LOG.trace("Updating output file: {}", outputFile);
            IOHelper.writeText(json.toJson(), outputFile);
        } else {
            IOHelper.writeText("{}", outputFile);
        }
    }

    private void doActionSourceTask(JsonObject root) throws Exception {
        DevConsole dc = camelContext.getCamelContextExtension().getContextPlugin(DevConsoleRegistry.class)
                .resolveById("source");
        if (dc != null) {
            String filter = root.getString("filter");
            JsonObject json = (JsonObject) dc.call(DevConsole.MediaType.JSON, Map.of("filter", filter));
            LOG.trace("Updating output file: {}", outputFile);
            IOHelper.writeText(json.toJson(), outputFile);
        } else {
            IOHelper.writeText("{}", outputFile);
        }
    }

    private void doActionTopProcessorsTask() throws IOException {
        DevConsole dc
                = camelContext.getCamelContextExtension().getContextPlugin(DevConsoleRegistry.class).resolveById("top");
        if (dc != null) {
            JsonObject json = (JsonObject) dc.call(DevConsole.MediaType.JSON, Map.of(Exchange.HTTP_PATH, "/*"));
            LOG.trace("Updating output file: {}", outputFile);
            IOHelper.writeText(json.toJson(), outputFile);
        } else {
            IOHelper.writeText("{}", outputFile);
        }
    }

    private void doActionThreadDumpTask() throws IOException {
        DevConsole dc = camelContext.getCamelContextExtension().getContextPlugin(DevConsoleRegistry.class)
                .resolveById("thread");
        if (dc != null) {
            JsonObject json = (JsonObject) dc.call(DevConsole.MediaType.JSON, Map.of("stackTrace", "true"));
            LOG.trace("Updating output file: {}", outputFile);
            IOHelper.writeText(json.toJson(), outputFile);
        } else {
            IOHelper.writeText("{}", outputFile);
        }
    }

    private void doActionKafkaTask() throws IOException {
        DevConsole dc = camelContext.getCamelContextExtension().getContextPlugin(DevConsoleRegistry.class)
                .resolveById("kafka");
        if (dc != null) {
            JsonObject json = (JsonObject) dc.call(DevConsole.MediaType.JSON, Map.of("committed", "true"));
            LOG.trace("Updating output file: {}", outputFile);
            IOHelper.writeText(json.toJson(), outputFile);
        } else {
            IOHelper.writeText("{}", outputFile);
        }
    }

    private void doActionTraceTask(JsonObject root) throws IOException {
        DevConsole dc = camelContext.getCamelContextExtension().getContextPlugin(DevConsoleRegistry.class)
                .resolveById("trace");
        if (dc != null) {
            String enabled = root.getString("enabled");
            JsonObject json;
            if (enabled != null) {
                json = (JsonObject) dc.call(DevConsole.MediaType.JSON, Map.of("enabled", enabled));
            } else {
                json = (JsonObject) dc.call(DevConsole.MediaType.JSON);
            }
            LOG.trace("Updating output file: {}", outputFile);
            IOHelper.writeText(json.toJson(), outputFile);
        } else {
            IOHelper.writeText("{}", outputFile);
        }
    }

    private void doActionBrowseTask(JsonObject root) throws IOException {
        DevConsole dc = camelContext.getCamelContextExtension().getContextPlugin(DevConsoleRegistry.class)
                .resolveById("browse");
        if (dc != null) {
            Map<String, Object> map = new HashMap<>();
            map.put("filter", root.getString("filter"));
            map.put("limit", root.getString("limit"));
            map.put("tail", root.getString("tail"));
            map.put("freshSize", root.getString("freshSize"));
            map.put("dump", root.getString("dump"));
            map.put("includeBody", root.getString("includeBody"));
            String bodyMaxChars = root.getString("bodyMaxChars");
            if (bodyMaxChars != null) {
                map.put("bodyMaxChars", bodyMaxChars);
            }
            JsonObject json
                    = (JsonObject) dc.call(DevConsole.MediaType.JSON, map);
            LOG.trace("Updating output file: {}", outputFile);
            IOHelper.writeText(json.toJson(), outputFile);
        } else {
            IOHelper.writeText("{}", outputFile);
        }
    }

    private void doActionReceiveTask(JsonObject root) throws Exception {
        DevConsole dc = camelContext.getCamelContextExtension().getContextPlugin(DevConsoleRegistry.class)
                .resolveById("receive");
        if (dc != null) {

            // wait for camel to be ready
            StopWatch watch = new StopWatch();
            while (!camelContext.isStarted() && watch.taken() < 5000) {
                Thread.sleep(100);
            }

            JsonObject json;
            String endpoint = root.getString("endpoint");
            if (endpoint != null) {
                json = (JsonObject) dc.call(DevConsole.MediaType.JSON, Map.of("enabled", "true", "endpoint", endpoint));
            } else {
                json = (JsonObject) dc.call(DevConsole.MediaType.JSON, Map.of("enabled", "false"));
            }
            LOG.trace("Updating output file: {}", outputFile);
            IOHelper.writeText(json.toJson(), outputFile);
        } else {
            IOHelper.writeText("{}", outputFile);
        }
    }

    private void doActionBeanTask(JsonObject root) throws IOException {
        String filter = root.getStringOrDefault("filter", "");
        String properties = root.getStringOrDefault("properties", "true");
        String nulls = root.getStringOrDefault("nulls", "true");
        String internal = root.getStringOrDefault("internal", "false");

        Map<String, Object> options = Map.of("filter", filter, "properties", properties, "nulls", nulls, "internal", internal);

        JsonObject answer = new JsonObject();
        DevConsole dc1 = camelContext.getCamelContextExtension().getContextPlugin(DevConsoleRegistry.class)
                .resolveById("bean");
        DevConsole dc2 = camelContext.getCamelContextExtension().getContextPlugin(DevConsoleRegistry.class)
                .resolveById("bean-model");
        if (dc1 != null) {
            JsonObject json = (JsonObject) dc1.call(DevConsole.MediaType.JSON, options);
            answer.put("beans", json.getMap("beans"));
        }
        if (dc2 != null) {
            JsonObject json = (JsonObject) dc2.call(DevConsole.MediaType.JSON, options);
            answer.put("bean-models", json.getMap("beans"));
        }
        LOG.trace("Updating output file: {}", outputFile);
        IOHelper.writeText(answer.toJson(), outputFile);
    }

    private void doActionResetStatsTask() throws Exception {
        ManagedCamelContext mcc = camelContext.getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);
        if (mcc != null) {
            mcc.getManagedCamelContext().reset(true);
        }
    }

    private void doActionDebugTask(JsonObject root) throws Exception {
        DevConsole dc = camelContext.getCamelContextExtension().getContextPlugin(DevConsoleRegistry.class)
                .resolveById("debug");
        if (dc != null) {
            String cmd = root.getStringOrDefault("command", "");
            String bp = root.getStringOrDefault("breakpoint", "");
            String history = root.getStringOrDefault("history", "false");
            JsonObject json = (JsonObject) dc.call(DevConsole.MediaType.JSON,
                    Map.of("command", cmd, "breakpoint", bp, "history", history));
            LOG.trace("Updating output file: {}", outputFile);
            IOHelper.writeText(json.toJson(), outputFile);
        } else {
            IOHelper.writeText("{}", outputFile);
        }
    }

    private void doActionReloadTask() {
        ContextReloadStrategy cr = camelContext.hasService(ContextReloadStrategy.class);
        if (cr != null) {
            cr.onReload("Camel JBang");
        } else {
            ResourceReloadStrategy rr = camelContext.hasService(ResourceReloadStrategy.class);
            if (rr != null) {
                rr.onReload("Camel JBang");
            }
        }
    }

    private void doActionLoggerTask(JsonObject root) {
        try {
            String command = root.getString("command");
            if ("set-logging-level".equals(command)) {
                String logger = root.getString("logger-name");
                String level = root.getString("logging-level");
                LoggerHelper.changeLoggingLevel(logger, level);
            }
        } catch (Exception e) {
            LOG.warn("Error changing logging level due to {}. This exception is ignored.", e.getMessage(), e);
        }
    }

    private void doActionRouteTask(JsonObject root) {
        // id is a pattern
        String[] patterns = root.getString("id").split(",");
        boolean all = patterns.length == 1 && "*".equals(patterns[0]);
        boolean group = root.getBooleanOrDefault("group", false);
        List<String> ids;
        if (all) {
            ids = List.of("*");
        } else {
            // find matching IDs
            ids = camelContext.getRoutes()
                    .stream().map(r -> group ? r.getGroup() : r.getRouteId())
                    .filter(Objects::nonNull)
                    .filter(name -> {
                        for (String p : patterns) {
                            if (PatternHelper.matchPattern(name, p)) {
                                return true;
                            }
                        }
                        return false;
                    })
                    .distinct()
                    .toList();
        }
        for (String id : ids) {
            String command = root.getString("command");
            try {
                if ("start".equals(command)) {
                    if ("*".equals(id)) {
                        camelContext.getRouteController().startAllRoutes();
                    } else if (group) {
                        camelContext.getRouteController().startRouteGroup(id);
                    } else {
                        camelContext.getRouteController().startRoute(id);
                    }
                } else if ("stop".equals(command)) {
                    if ("*".equals(id)) {
                        camelContext.getRouteController().stopAllRoutes();
                    } else if (group) {
                        camelContext.getRouteController().stopRouteGroup(id);
                    } else {
                        camelContext.getRouteController().stopRoute(id);
                    }
                } else if ("suspend".equals(command)) {
                    if ("*".equals(id)) {
                        for (Route r : camelContext.getRoutes()) {
                            camelContext.getRouteController().suspendRoute(r.getRouteId());
                        }
                    } else if (group) {
                        // we do not have suspend for a group
                        camelContext.getRouteController().stopRouteGroup(id);
                    } else {
                        camelContext.getRouteController().suspendRoute(id);
                    }
                } else if ("resume".equals(command)) {
                    if ("*".equals(id)) {
                        for (Route r : camelContext.getRoutes()) {
                            camelContext.getRouteController().resumeRoute(r.getRouteId());
                        }
                    } else if (group) {
                        // we do not have resume for a group
                        camelContext.getRouteController().startRouteGroup(id);
                    } else {
                        camelContext.getRouteController().resumeRoute(id);
                    }
                }
            } catch (Exception e) {
                LOG.warn("Error {} route: {} due to: {}. This exception is ignored.", command, id, e.getMessage(), e);
            }
        }
    }

    JsonObject loadAction() {
        try {
            if (actionFile != null && actionFile.exists()) {
                FileInputStream fis = new FileInputStream(actionFile);
                String text = IOHelper.loadText(fis);
                IOHelper.close(fis);
                if (!text.isEmpty()) {
                    return (JsonObject) Jsoner.deserialize(text);
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    protected void statusTask() {
        try {
            // even during termination then collect status as we want to see status changes during stopping
            JsonObject root = new JsonObject();

            // what runtime are in use
            JsonObject rc = new JsonObject();
            String dir = new File(".").getAbsolutePath();
            dir = FileUtil.onlyPath(dir);
            rc.put("pid", ProcessHandle.current().pid());
            rc.put("directory", dir);
            ProcessHandle.current().info().user().ifPresent(u -> rc.put("user", u));
            rc.put("platform", platform);
            if (platformVersion != null) {
                rc.put("platformVersion", platformVersion);
            }
            if (mainClass != null) {
                rc.put("mainClass", mainClass);
            }
            RuntimeMXBean mb = ManagementFactory.getRuntimeMXBean();
            if (mb != null) {
                rc.put("javaVersion", mb.getVmVersion());
            }
            root.put("runtime", rc);

            DevConsoleRegistry dcr = camelContext.getCamelContextExtension().getContextPlugin(DevConsoleRegistry.class);
            if (dcr != null) {
                // collect details via console
                DevConsole dc = dcr.resolveById("context");
                DevConsole dc2 = dcr.resolveById("route");
                if (dc != null && dc2 != null) {
                    JsonObject json = (JsonObject) dc.call(DevConsole.MediaType.JSON);
                    JsonObject json2 = (JsonObject) dc2.call(DevConsole.MediaType.JSON, Map.of("processors", "true"));
                    if (json != null && json2 != null) {
                        root.put("context", json);
                        root.put("routes", json2.get("routes"));
                    }
                }
                DevConsole dc2b = dcr.resolveById("route-group");
                if (dc2b != null) {
                    JsonObject json = (JsonObject) dc2b.call(DevConsole.MediaType.JSON);
                    if (json != null) {
                        root.put("routeGroups", json.get("routeGroups"));
                    }
                }
                DevConsole dc3 = dcr.resolveById("endpoint");
                if (dc3 != null) {
                    JsonObject json = (JsonObject) dc3.call(DevConsole.MediaType.JSON);
                    if (json != null && !json.isEmpty()) {
                        root.put("endpoints", json);
                    }
                }
                DevConsole dc4 = dcr.resolveById("health");
                if (dc4 != null) {
                    // include full details in health checks
                    JsonObject json = (JsonObject) dc4.call(DevConsole.MediaType.JSON, Map.of("exposureLevel", "full"));
                    if (json != null && !json.isEmpty()) {
                        root.put("healthChecks", json);
                    }
                }
                DevConsole dc5 = dcr.resolveById("event");
                if (dc5 != null) {
                    JsonObject json = (JsonObject) dc5.call(DevConsole.MediaType.JSON);
                    if (json != null && !json.isEmpty()) {
                        root.put("events", json);
                    }
                }
                DevConsole dc6 = dcr.resolveById("log");
                if (dc6 != null) {
                    JsonObject json = (JsonObject) dc6.call(DevConsole.MediaType.JSON);
                    if (json != null && !json.isEmpty()) {
                        root.put("logger", json);
                    }
                }
                DevConsole dc7 = dcr.resolveById("inflight");
                if (dc7 != null) {
                    JsonObject json = (JsonObject) dc7.call(DevConsole.MediaType.JSON);
                    if (json != null && !json.isEmpty()) {
                        root.put("inflight", json);
                    }
                }
                DevConsole dc8 = dcr.resolveById("blocked");
                if (dc8 != null) {
                    JsonObject json = (JsonObject) dc8.call(DevConsole.MediaType.JSON);
                    if (json != null && !json.isEmpty()) {
                        root.put("blocked", json);
                    }
                }
                DevConsole dc9 = dcr.resolveById("micrometer");
                if (dc9 != null) {
                    JsonObject json = (JsonObject) dc9.call(DevConsole.MediaType.JSON);
                    if (json != null && !json.isEmpty()) {
                        root.put("micrometer", json);
                    }
                }
                DevConsole dc10 = dcr.resolveById("resilience4j");
                if (dc10 != null) {
                    JsonObject json = (JsonObject) dc10.call(DevConsole.MediaType.JSON);
                    if (json != null && !json.isEmpty()) {
                        root.put("resilience4j", json);
                    }
                }
                DevConsole dc11 = dcr.resolveById("fault-tolerance");
                if (dc11 != null) {
                    JsonObject json = (JsonObject) dc11.call(DevConsole.MediaType.JSON);
                    if (json != null && !json.isEmpty()) {
                        root.put("fault-tolerance", json);
                    }
                }
                DevConsole dc12 = dcr.resolveById("circuit-breaker");
                if (dc12 != null) {
                    JsonObject json = (JsonObject) dc12.call(DevConsole.MediaType.JSON);
                    if (json != null && !json.isEmpty()) {
                        root.put("circuit-breaker", json);
                    }
                }
                DevConsole dc13 = dcr.resolveById("trace");
                if (dc13 != null) {
                    JsonObject json = (JsonObject) dc13.call(DevConsole.MediaType.JSON);
                    if (json != null && !json.isEmpty()) {
                        root.put("trace", json);
                    }
                }
                DevConsole dc14 = dcr.resolveById("consumer");
                if (dc14 != null) {
                    JsonObject json = (JsonObject) dc14.call(DevConsole.MediaType.JSON);
                    if (json != null && !json.isEmpty()) {
                        root.put("consumers", json);
                    }
                }
                DevConsole dc15 = dcr.resolveById("variables");
                if (dc15 != null) {
                    JsonObject json = (JsonObject) dc15.call(DevConsole.MediaType.JSON);
                    if (json != null && !json.isEmpty()) {
                        root.put("variables", json);
                    }
                }
                DevConsole dc16 = dcr.resolveById("transformers");
                if (dc16 != null) {
                    JsonObject json = (JsonObject) dc16.call(DevConsole.MediaType.JSON);
                    if (json != null && !json.isEmpty()) {
                        root.put("transformers", json);
                    }
                }
                DevConsole dc17 = dcr.resolveById("service");
                if (dc17 != null) {
                    JsonObject json = (JsonObject) dc17.call(DevConsole.MediaType.JSON);
                    if (json != null && !json.isEmpty()) {
                        root.put("services", json);
                    }
                }
                DevConsole dc18 = dcr.resolveById("platform-http");
                if (dc18 != null) {
                    JsonObject json = (JsonObject) dc18.call(DevConsole.MediaType.JSON);
                    if (json != null && !json.isEmpty()) {
                        root.put("platform-http", json);
                    }
                }
                DevConsole dc19 = dcr.resolveById("rest");
                if (dc19 != null) {
                    JsonObject json = (JsonObject) dc19.call(DevConsole.MediaType.JSON);
                    if (json != null && !json.isEmpty()) {
                        root.put("rests", json);
                    }
                }
                DevConsole dc20 = dcr.resolveById("kafka");
                if (dc20 != null) {
                    JsonObject json = (JsonObject) dc20.call(DevConsole.MediaType.JSON);
                    if (json != null && !json.isEmpty()) {
                        root.put("kafka", json);
                    }
                }
                DevConsole dc21 = dcr.resolveById("properties");
                if (dc21 != null) {
                    JsonObject json = (JsonObject) dc21.call(DevConsole.MediaType.JSON);
                    if (json != null && !json.isEmpty()) {
                        root.put("properties", json);
                    }
                }
                DevConsole dc22 = dcr.resolveById("main-configuration");
                if (dc22 != null) {
                    JsonObject json = (JsonObject) dc22.call(DevConsole.MediaType.JSON);
                    if (json != null && !json.isEmpty()) {
                        root.put("main-configuration", json);
                    }
                }
                DevConsole dc23 = dcr.resolveById("receive");
                if (dc23 != null) {
                    JsonObject json = (JsonObject) dc23.call(DevConsole.MediaType.JSON);
                    if (json != null && !json.isEmpty()) {
                        root.put("receive", json);
                    }
                }
                DevConsole dc24 = dcr.resolveById("internal-tasks");
                if (dc24 != null) {
                    JsonObject json = (JsonObject) dc24.call(DevConsole.MediaType.JSON);
                    if (json != null && !json.isEmpty()) {
                        root.put("internal-tasks", json);
                    }
                }
                DevConsole dc25 = dcr.resolveById("groovy");
                if (dc25 != null) {
                    JsonObject json = (JsonObject) dc25.call(DevConsole.MediaType.JSON);
                    if (json != null && !json.isEmpty()) {
                        root.put("groovy", json);
                    }
                }
            }
            // various details
            JsonObject mem = collectMemory();
            if (mem != null) {
                root.put("memory", mem);
            }
            JsonObject cl = collectClassLoading();
            if (cl != null) {
                root.put("classLoading", cl);
            }
            JsonObject threads = collectThreads();
            if (threads != null) {
                root.put("threads", threads);
            }
            JsonObject gc = collectGC();
            if (gc != null) {
                root.put("gc", gc);
            }
            JsonObject vaults = collectVaults();
            if (!vaults.isEmpty()) {
                root.put("vaults", vaults);
            }
            LOG.trace("Updating status file: {}", statusFile);
            IOHelper.writeText(root.toJson(), statusFile);
        } catch (Exception e) {
            // ignore
            LOG.trace("Error updating status file: {} due to: {}. This exception is ignored.",
                    statusFile, e.getMessage(), e);
        }
    }

    protected void traceTask() {
        try {
            DevConsole dc12 = camelContext.getCamelContextExtension().getContextPlugin(DevConsoleRegistry.class)
                    .resolveById("trace");
            if (dc12 != null) {
                JsonObject json = (JsonObject) dc12.call(DevConsole.MediaType.JSON, Map.of("dump", "true"));
                JsonArray arr = json.getCollection("traces");
                // filter based on last uid
                if (traceFilePos > 0) {
                    arr.removeIf(r -> {
                        JsonObject jo = (JsonObject) r;
                        return jo.getLong("uid") <= traceFilePos;
                    });
                }
                if (arr != null && !arr.isEmpty()) {
                    // store traces in a special file
                    LOG.trace("Updating trace file: {}", traceFile);
                    String data = json.toJson() + System.lineSeparator();
                    IOHelper.appendText(data, traceFile);
                    json = arr.getMap(arr.size() - 1);
                    traceFilePos = json.getLong("uid");
                }
            }
        } catch (Exception e) {
            // ignore
            LOG.trace("Error updating trace file: {} due to: {}. This exception is ignored.",
                    traceFile, e.getMessage(), e);
        }
        try {
            DevConsole dc13 = camelContext.getCamelContextExtension().getContextPlugin(DevConsoleRegistry.class)
                    .resolveById("debug");
            if (dc13 != null) {
                JsonObject json = (JsonObject) dc13.call(DevConsole.MediaType.JSON);
                // store debugs in a special file
                LOG.trace("Updating debug file: {}", debugFile);
                String data = json.toJson() + System.lineSeparator();
                IOHelper.writeText(data, debugFile);
            }
        } catch (Exception e) {
            // ignore
            LOG.trace("Error updating debug file: {} due to: {}. This exception is ignored.",
                    debugFile, e.getMessage(), e);
        }
        try {
            DevConsole dc14 = camelContext.getCamelContextExtension().getContextPlugin(DevConsoleRegistry.class)
                    .resolveById("receive");
            if (dc14 != null) {
                JsonObject json = (JsonObject) dc14.call(DevConsole.MediaType.JSON, Map.of("dump", "true"));
                JsonArray arr = json.getCollection("messages");
                // filter based on last uid
                if (receiveFilePos > 0) {
                    arr.removeIf(r -> {
                        JsonObject jo = (JsonObject) r;
                        return jo.getLong("uid") <= receiveFilePos;
                    });
                }
                if (arr != null && !arr.isEmpty()) {
                    // store messages in a special file
                    LOG.trace("Updating receive file: {}", receiveFile);
                    String data = json.toJson() + System.lineSeparator();
                    IOHelper.appendText(data, receiveFile);
                    json = arr.getMap(arr.size() - 1);
                    receiveFilePos = json.getLong("uid");
                }
            }
        } catch (Exception e) {
            // ignore
            LOG.trace("Error updating receive file: {} due to: {}. This exception is ignored.",
                    receiveFile, e.getMessage(), e);
        }
    }

    private JsonObject collectMemory() {
        MemoryMXBean mb = ManagementFactory.getMemoryMXBean();
        if (mb != null) {
            JsonObject root = new JsonObject();
            root.put("heapMemoryUsed", mb.getHeapMemoryUsage().getUsed());
            root.put("heapMemoryCommitted", mb.getHeapMemoryUsage().getCommitted());
            root.put("heapMemoryMax", mb.getHeapMemoryUsage().getMax());
            root.put("nonHeapMemoryUsed", mb.getNonHeapMemoryUsage().getUsed());
            root.put("nonHeapMemoryCommitted", mb.getNonHeapMemoryUsage().getCommitted());
            return root;
        }
        return null;
    }

    private JsonObject collectClassLoading() {
        ClassLoadingMXBean cb = ManagementFactory.getClassLoadingMXBean();
        if (cb != null) {
            JsonObject root = new JsonObject();
            root.put("loadedClassCount", cb.getLoadedClassCount());
            root.put("unloadedClassCount", cb.getUnloadedClassCount());
            root.put("totalLoadedClassCount", cb.getTotalLoadedClassCount());
            return root;
        }
        return null;
    }

    private JsonObject collectThreads() {
        ThreadMXBean tb = ManagementFactory.getThreadMXBean();
        if (tb != null) {
            JsonObject root = new JsonObject();
            root.put("threadCount", tb.getThreadCount());
            root.put("peakThreadCount", tb.getPeakThreadCount());
            return root;
        }
        return null;
    }

    private JsonObject collectGC() {
        List<GarbageCollectorMXBean> gcs = ManagementFactory.getGarbageCollectorMXBeans();
        if (gcs != null && !gcs.isEmpty()) {
            JsonObject root = new JsonObject();
            long count = 0;
            long time = 0;
            for (GarbageCollectorMXBean gc : gcs) {
                count += gc.getCollectionCount();
                time += gc.getCollectionTime();
            }
            root.put("collectionCount", count);
            root.put("collectionTime", time);
            return root;
        }
        return null;
    }

    private JsonObject collectVaults() {
        JsonObject root = new JsonObject();
        // aws-secrets is optional
        Optional<DevConsole> dcAws = PluginHelper.getDevConsoleResolver(camelContext).lookupDevConsole("aws-secrets");
        if (dcAws.isPresent()) {
            JsonObject json = (JsonObject) dcAws.get().call(DevConsole.MediaType.JSON);
            if (json != null) {
                root.put("aws-secrets", json);
            }
        }
        // gcp-secrets is optional
        Optional<DevConsole> dcGcp = PluginHelper.getDevConsoleResolver(camelContext).lookupDevConsole("gcp-secrets");
        if (dcGcp.isPresent()) {
            JsonObject json = (JsonObject) dcGcp.get().call(DevConsole.MediaType.JSON);
            if (json != null) {
                root.put("gcp-secrets", json);
            }
        }
        // azure-secrets is optional
        Optional<DevConsole> dcAzure = PluginHelper.getDevConsoleResolver(camelContext).lookupDevConsole("azure-secrets");
        if (dcAzure.isPresent()) {
            JsonObject json = (JsonObject) dcAzure.get().call(DevConsole.MediaType.JSON);
            if (json != null) {
                root.put("azure-secrets", json);
            }
        }
        // kubernetes-secrets is optional
        Optional<DevConsole> dcKubernetes
                = PluginHelper.getDevConsoleResolver(camelContext).lookupDevConsole("kubernetes-secrets");
        if (dcKubernetes.isPresent()) {
            JsonObject json = (JsonObject) dcKubernetes.get().call(DevConsole.MediaType.JSON);
            if (json != null) {
                root.put("kubernetes-secrets", json);
            }
        }

        // hashicorp-secrets is optional
        Optional<DevConsole> dcHashicorp
                = PluginHelper.getDevConsoleResolver(camelContext).lookupDevConsole("hashicorp-secrets");
        if (dcHashicorp.isPresent()) {
            JsonObject json = (JsonObject) dcHashicorp.get().call(DevConsole.MediaType.JSON);
            if (json != null) {
                root.put("hashicorp-secrets", json);
            }
        }

        // kubernetes-configmaps is optional
        Optional<DevConsole> cmcKubernetes
                = PluginHelper.getDevConsoleResolver(camelContext).lookupDevConsole("kubernetes-configmaps");
        if (cmcKubernetes.isPresent()) {
            JsonObject json = (JsonObject) cmcKubernetes.get().call(DevConsole.MediaType.JSON);
            if (json != null) {
                root.put("kubernetes-configmaps", json);
            }
        }
        return root;
    }

    @Override
    protected void doStop() throws Exception {
        // cleanup
        if (lockFile != null) {
            FileUtil.deleteFile(lockFile);
        }
        if (statusFile != null) {
            FileUtil.deleteFile(statusFile);
        }
        if (actionFile != null) {
            FileUtil.deleteFile(actionFile);
        }
        if (outputFile != null) {
            FileUtil.deleteFile(outputFile);
        }
        if (traceFile != null) {
            FileUtil.deleteFile(traceFile);
        }
        if (debugFile != null) {
            FileUtil.deleteFile(debugFile);
        }
        if (receiveFile != null) {
            FileUtil.deleteFile(receiveFile);
        }
        if (executor != null) {
            camelContext.getExecutorServiceManager().shutdown(executor);
            executor = null;
        }
        ServiceHelper.stopService(producer, consumer);
    }

    private static String getPid() {
        return String.valueOf(ProcessHandle.current().pid());
    }

    private static File createLockFile(String name) {
        File answer = null;
        if (name != null) {
            File dir = new File(System.getProperty("user.home"), ".camel");
            try {
                dir.mkdirs();
                answer = new File(dir, name);
                if (!answer.exists()) {
                    answer.createNewFile();
                }
                answer.deleteOnExit();
            } catch (Exception e) {
                answer = null;
            }
        }
        return answer;
    }

}
