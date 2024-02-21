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
package org.apache.camel.processor.errorhandler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Message;
import org.apache.camel.Navigate;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.processor.PooledExchangeTask;
import org.apache.camel.processor.PooledExchangeTaskFactory;
import org.apache.camel.processor.PooledTaskFactory;
import org.apache.camel.processor.PrototypeTaskFactory;
import org.apache.camel.spi.AsyncProcessorAwaitManager;
import org.apache.camel.spi.CamelLogger;
import org.apache.camel.spi.ErrorHandlerRedeliveryCustomizer;
import org.apache.camel.spi.ExchangeFormatter;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.ReactiveExecutor;
import org.apache.camel.spi.ShutdownPrepared;
import org.apache.camel.spi.ShutdownStrategy;
import org.apache.camel.support.AsyncCallbackToCompletableFutureAdapter;
import org.apache.camel.support.AsyncProcessorConverterHelper;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.EventHelper;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base redeliverable error handler that also supports a final dead letter queue in case all redelivery attempts fail.
 * <p/>
 * This implementation should contain all the error handling logic and the sub classes should only configure it
 * according to what they support.
 */
public abstract class RedeliveryErrorHandler extends ErrorHandlerSupport
        implements ErrorHandlerRedeliveryCustomizer, AsyncProcessor, ShutdownPrepared, Navigate<Processor> {

    private static final Logger LOG = LoggerFactory.getLogger(RedeliveryErrorHandler.class);

    // factory
    protected PooledExchangeTaskFactory taskFactory;

    // state
    protected final AtomicInteger redeliverySleepCounter = new AtomicInteger();
    protected ScheduledExecutorService executorService;
    protected volatile boolean preparingShutdown;

    // output
    protected Processor output;
    protected AsyncProcessor outputAsync;

    // configuration
    protected final CamelContext camelContext;
    protected final ReactiveExecutor reactiveExecutor;
    protected final AsyncProcessorAwaitManager awaitManager;
    protected final ShutdownStrategy shutdownStrategy;
    protected final Processor deadLetter;
    protected final String deadLetterUri;
    protected final boolean deadLetterHandleNewException;
    protected final Processor redeliveryProcessor;
    protected final RedeliveryPolicy redeliveryPolicy;
    protected final Predicate retryWhilePolicy;
    protected final CamelLogger logger;
    protected final boolean useOriginalMessagePolicy;
    protected final boolean useOriginalBodyPolicy;
    protected boolean redeliveryEnabled;
    protected boolean simpleTask;
    protected final ExchangeFormatter exchangeFormatter;
    protected final boolean customExchangeFormatter;
    protected final Processor onPrepareProcessor;
    protected final Processor onExceptionProcessor;

    public RedeliveryErrorHandler(CamelContext camelContext, Processor output, CamelLogger logger,
                                  Processor redeliveryProcessor, RedeliveryPolicy redeliveryPolicy, Processor deadLetter,
                                  String deadLetterUri, boolean deadLetterHandleNewException, boolean useOriginalMessagePolicy,
                                  boolean useOriginalBodyPolicy,
                                  Predicate retryWhile, ScheduledExecutorService executorService, Processor onPrepareProcessor,
                                  Processor onExceptionProcessor) {

        ObjectHelper.notNull(camelContext, "CamelContext", this);
        ObjectHelper.notNull(redeliveryPolicy, "RedeliveryPolicy", this);

        this.camelContext = camelContext;
        this.reactiveExecutor = camelContext.getCamelContextExtension().getReactiveExecutor();
        this.awaitManager = PluginHelper.getAsyncProcessorAwaitManager(camelContext);
        this.shutdownStrategy = camelContext.getShutdownStrategy();
        this.redeliveryProcessor = redeliveryProcessor;
        this.deadLetter = deadLetter;
        this.output = output;
        this.outputAsync = AsyncProcessorConverterHelper.convert(output);
        this.redeliveryPolicy = redeliveryPolicy;
        this.logger = logger;
        this.deadLetterUri = deadLetterUri;
        this.deadLetterHandleNewException = deadLetterHandleNewException;
        this.useOriginalMessagePolicy = useOriginalMessagePolicy;
        this.useOriginalBodyPolicy = useOriginalBodyPolicy;
        this.retryWhilePolicy = retryWhile;
        this.executorService = executorService;
        this.onPrepareProcessor = onPrepareProcessor;
        this.onExceptionProcessor = onExceptionProcessor;

        if (ObjectHelper.isNotEmpty(redeliveryPolicy.getExchangeFormatterRef())) {
            ExchangeFormatter formatter = camelContext.getRegistry()
                    .lookupByNameAndType(redeliveryPolicy.getExchangeFormatterRef(), ExchangeFormatter.class);
            if (formatter != null) {
                this.exchangeFormatter = formatter;
                this.customExchangeFormatter = true;
            } else {
                throw new IllegalArgumentException(
                        "Cannot find the exchangeFormatter by using reference id "
                                                   + redeliveryPolicy.getExchangeFormatterRef());
            }
        } else {
            this.customExchangeFormatter = false;
            this.exchangeFormatter = DEFAULT_EXCHANGE_FORMATTER;
            try {
                Integer maxChars = CamelContextHelper.parseInteger(camelContext,
                        camelContext.getGlobalOption(Exchange.LOG_DEBUG_BODY_MAX_CHARS));
                if (maxChars != null) {
                    DEFAULT_EXCHANGE_FORMATTER.setMaxChars(maxChars);
                }
            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        }

        ExceptionPolicyStrategy customExceptionPolicy
                = CamelContextHelper.findSingleByType(camelContext, ExceptionPolicyStrategy.class);
        if (customExceptionPolicy != null) {
            exceptionPolicy = customExceptionPolicy;
        }
    }

    RedeliveryErrorHandler(Logger log) {
        // used for eager loading
        camelContext = null;
        reactiveExecutor = null;
        awaitManager = null;
        shutdownStrategy = null;
        deadLetter = null;
        deadLetterUri = null;
        deadLetterHandleNewException = false;
        redeliveryProcessor = null;
        redeliveryPolicy = null;
        retryWhilePolicy = null;
        logger = null;
        useOriginalMessagePolicy = false;
        useOriginalBodyPolicy = false;
        redeliveryEnabled = false;
        simpleTask = false;
        exchangeFormatter = null;
        customExchangeFormatter = false;
        onPrepareProcessor = null;
        onExceptionProcessor = null;
        log.trace("Loaded {}", RedeliveryErrorHandler.class.getName());
    }

    @Override
    public void process(Exchange exchange) {
        if (output == null) {
            // no output then just return
            return;
        }
        awaitManager.process(this, exchange);
    }

    /**
     * Process the exchange using redelivery error handling.
     */
    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        try {
            // Create the redelivery task object for this exchange (optimize to only create task can do redelivery or not)
            Runnable task = taskFactory.acquire(exchange, callback);

            // Run it
            if (exchange.isTransacted()) {
                reactiveExecutor.scheduleQueue(task);
            } else {
                reactiveExecutor.scheduleMain(task);
            }
            return false;
        } catch (Exception e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }
    }

    @Override
    public CompletableFuture<Exchange> processAsync(Exchange exchange) {
        AsyncCallbackToCompletableFutureAdapter<Exchange> callback = new AsyncCallbackToCompletableFutureAdapter<>(exchange);
        process(exchange, callback);
        return callback.getFuture();
    }

    @Override
    public void changeOutput(Processor output) {
        this.output = output;
        this.outputAsync = AsyncProcessorConverterHelper.convert(output);
    }

    @Override
    public boolean supportTransacted() {
        return false;
    }

    @Override
    public boolean hasNext() {
        return output != null;
    }

    @Override
    public List<Processor> next() {
        if (!hasNext()) {
            return null;
        }
        List<Processor> answer = new ArrayList<>(1);
        answer.add(output);
        return answer;
    }

    protected boolean isRunAllowedOnPreparingShutdown() {
        return false;
    }

    @Override
    public void prepareShutdown(boolean suspendOnly, boolean forced) {
        // prepare for shutdown, eg do not allow redelivery if configured
        LOG.trace("Prepare shutdown on error handler: {}", this);
        preparingShutdown = true;
    }

    /**
     * <p>
     * Determines the redelivery delay time by first inspecting the Message header {@link Exchange#REDELIVERY_DELAY} and
     * if not present, defaulting to {@link RedeliveryPolicy#calculateRedeliveryDelay(long, int)}
     * </p>
     *
     * <p>
     * In order to prevent manipulation of the RedeliveryData state, the values of
     * {@link RedeliveryTask#redeliveryDelay} and {@link RedeliveryTask#redeliveryCounter} are copied in.
     * </p>
     *
     * @param  exchange          The current exchange in question.
     * @param  redeliveryPolicy  The RedeliveryPolicy to use in the calculation.
     * @param  redeliveryDelay   The default redelivery delay from RedeliveryData
     * @param  redeliveryCounter The redeliveryCounter
     * @return                   The time to wait before the next redelivery.
     */
    protected long determineRedeliveryDelay(
            Exchange exchange, RedeliveryPolicy redeliveryPolicy, long redeliveryDelay, int redeliveryCounter) {
        Message message = exchange.getIn();
        Long delay = message.getHeader(Exchange.REDELIVERY_DELAY, Long.class);
        if (delay == null) {
            delay = redeliveryPolicy.calculateRedeliveryDelay(redeliveryDelay, redeliveryCounter);
            LOG.debug("Redelivery delay calculated as {}", delay);
        } else {
            LOG.debug("Redelivery delay is {} from Message Header [{}]", delay, Exchange.REDELIVERY_DELAY);
        }
        return delay;
    }

    /**
     * Performs a defensive copy of the exchange if needed
     *
     * @param  exchange the exchange
     * @return          the defensive copy, or <tt>null</tt> if not needed (redelivery is not enabled).
     */
    protected Exchange defensiveCopyExchangeIfNeeded(Exchange exchange) {
        // only do a defensive copy if redelivery is enabled
        if (redeliveryEnabled) {
            return ExchangeHelper.createCopy(exchange, true);
        } else {
            return null;
        }
    }

    /**
     * Strategy to determine if the exchange is done so we can continue
     */
    protected boolean isDone(Exchange exchange) {
        if (exchange.getExchangeExtension().isInterrupted()) {
            // mark the exchange to stop continue routing when interrupted
            // as we do not want to continue routing (for example a task has been cancelled)
            if (LOG.isTraceEnabled()) {
                LOG.trace("Is exchangeId: {} interrupted? true", exchange.getExchangeId());
            }
            exchange.setRouteStop(true);
            return true;
        }

        // only done if the exchange hasn't failed
        // and it has not been handled by the failure processor
        // or we are exhausted
        boolean answer = exchange.getException() == null
                || ExchangeHelper.isFailureHandled(exchange)
                || exchange.getExchangeExtension().isRedeliveryExhausted();

        if (LOG.isTraceEnabled()) {
            LOG.trace("Is exchangeId: {} done? {}", exchange.getExchangeId(), answer);
        }
        return answer;
    }

    @Override
    public Processor getOutput() {
        return output;
    }

    /**
     * Returns the dead letter that message exchanges will be sent to if the redelivery attempts fail
     */
    public Processor getDeadLetter() {
        return deadLetter;
    }

    public String getDeadLetterUri() {
        return deadLetterUri;
    }

    public boolean isUseOriginalMessagePolicy() {
        return useOriginalMessagePolicy;
    }

    public boolean isUseOriginalBodyPolicy() {
        return useOriginalBodyPolicy;
    }

    public boolean isDeadLetterHandleNewException() {
        return deadLetterHandleNewException;
    }

    public RedeliveryPolicy getRedeliveryPolicy() {
        return redeliveryPolicy;
    }

    public CamelLogger getLogger() {
        return logger;
    }

    protected Predicate getDefaultHandledPredicate() {
        // Default is not not handle errors
        return null;
    }

    /**
     * Simple task to perform calling the processor with no redelivery support
     */
    protected class SimpleTask implements PooledExchangeTask, Runnable, AsyncCallback {
        private Exchange exchange;
        private AsyncCallback callback;
        private boolean first;

        public SimpleTask() {
        }

        public void prepare(Exchange exchange, AsyncCallback callback) {
            this.exchange = exchange;
            this.callback = callback;
            this.first = true;
        }

        @Override
        public String toString() {
            return "SimpleTask";
        }

        public void reset() {
            this.exchange = null;
            this.callback = null;
            this.first = true;
        }

        @Override
        public void done(boolean doneSync) {
            // the run method decides what to do when we are done
            run();
        }

        /**
         * Processing logic.
         */
        @Override
        public void run() {
            // can we still run
            if (shutdownStrategy.isForceShutdown() || isStoppingOrStopped()) {
                runNotAllowed();
                return;
            }

            if (exchange.getExchangeExtension().isInterrupted()) {
                runInterrupted();
                return;
            }

            if (isFailedOrBridged(exchange)) {
                // previous processing cause an exception
                handlePreviousFailure();
            } else if (first) {
                // first time call the target processor
                handleFirst();
            } else {
                // we are done so continue callback
                AsyncCallback cb = callback;
                taskFactory.release(this);
                reactiveExecutor.schedule(cb);
            }
        }

        private static boolean isFailedOrBridged(Exchange exchange) {
            // only new failure if the exchange has exception
            // and it has not been handled by the failure processor before
            // or not exhausted
            final boolean failure = exchange.getException() != null
                    && !exchange.getExchangeExtension().isRedeliveryExhausted()
                    && !ExchangeHelper.isFailureHandled(exchange);
            // error handled bridged
            final boolean bridge = ExchangeHelper.isErrorHandlerBridge(exchange);

            return failure || bridge;
        }

        private void handleFirst() {
            first = false;
            outputAsync.process(exchange, this);
        }

        private void handlePreviousFailure() {
            handleException();
            onExceptionOccurred();
            prepareExchangeAfterFailure(exchange);
            // we do not support redelivery so continue callback
            AsyncCallback cb = callback;
            taskFactory.release(this);
            reactiveExecutor.schedule(cb);
        }

        private void runInterrupted() {
            // mark the exchange to stop continue routing when interrupted
            // as we do not want to continue routing (for example a task has been cancelled)
            if (LOG.isTraceEnabled()) {
                LOG.trace("Is exchangeId: {} interrupted? true", exchange.getExchangeId());
            }
            exchange.setRouteStop(true);
            // we should not continue routing so call callback
            AsyncCallback cb = callback;
            taskFactory.release(this);
            cb.done(false);
        }

        private void runNotAllowed() {
            LOG.trace("Run not allowed, will reject executing exchange: {}", exchange);
            if (exchange.getException() == null) {
                exchange.setException(new RejectedExecutionException());
            }
            AsyncCallback cb = callback;
            taskFactory.release(this);
            cb.done(false);
        }

        protected void handleException() {
            Exception e = exchange.getException();
            // e is never null

            Throwable previous = exchange.getProperty(ExchangePropertyKey.EXCEPTION_CAUGHT, Throwable.class);
            if (previous != null && previous != e) {
                // a 2nd exception was thrown while handling a previous exception
                // so we need to add the previous as suppressed by the new exception
                // see also FatalFallbackErrorHandler
                Throwable[] suppressed = e.getSuppressed();
                boolean found = false;
                for (Throwable t : suppressed) {
                    if (t == previous) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    // okay before adding suppression then we must be sure its not referring to same method
                    // which otherwise can lead to add the same exception over and over again
                    StackTraceElement[] ste1 = e.getStackTrace();
                    StackTraceElement[] ste2 = previous.getStackTrace();
                    boolean same = false;
                    if (ste1 != null && ste2 != null && ste1.length > 0 && ste2.length > 0) {
                        same = ste1[0].getClassName().equals(ste2[0].getClassName())
                                && ste1[0].getLineNumber() == ste2[0].getLineNumber();
                    }
                    if (!same) {
                        e.addSuppressed(previous);
                    }
                }
            }

            // store the original caused exception in a property, so we can restore it later
            exchange.setProperty(ExchangePropertyKey.EXCEPTION_CAUGHT, e);
        }

        /**
         * Gives an optional configured OnExceptionOccurred processor a chance to process just after an exception was
         * thrown while processing the Exchange. This allows to execute the processor at the same time the exception was
         * thrown.
         */
        protected void onExceptionOccurred() {
            if (onExceptionProcessor == null) {
                return;
            }

            // run this synchronously as its just a Processor
            try {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("OnExceptionOccurred processor {} is processing Exchange: {} due exception occurred",
                            onExceptionProcessor, exchange);
                }
                onExceptionProcessor.process(exchange);
            } catch (Exception e) {
                // we dont not want new exception to override existing, so log it as a WARN
                LOG.warn("Error during processing OnExceptionOccurred. This exception is ignored.", e);
            }
            LOG.trace("OnExceptionOccurred processor done");
        }

        protected void prepareExchangeAfterFailure(final Exchange exchange) {
            // we could not process the exchange so we let the failure processor handled it
            ExchangeHelper.setFailureHandled(exchange);

            // honor if already set a handling
            boolean alreadySet = exchange.getExchangeExtension().isErrorHandlerHandledSet();
            if (alreadySet) {
                boolean handled = exchange.getExchangeExtension().isErrorHandlerHandled();
                LOG.trace("This exchange has already been marked for handling: {}", handled);
                if (!handled) {
                    // exception not handled, put exception back in the exchange
                    exchange.setException(exchange.getProperty(ExchangePropertyKey.EXCEPTION_CAUGHT, Exception.class));
                    // and put failure endpoint back as well
                    exchange.setProperty(ExchangePropertyKey.FAILURE_ENDPOINT,
                            exchange.getProperty(ExchangePropertyKey.TO_ENDPOINT));
                }
                return;
            }

            // not handled by default
            prepareExchangeAfterFailureNotHandled(exchange);
        }

        private void prepareExchangeAfterFailureNotHandled(Exchange exchange) {
            LOG.trace("This exchange is not handled or continued so its marked as failed: {}", exchange);
            // exception not handled, put exception back in the exchange
            exchange.getExchangeExtension().setErrorHandlerHandled(false);
            exchange.setException(exchange.getProperty(ExchangePropertyKey.EXCEPTION_CAUGHT, Exception.class));
            // and put failure endpoint back as well
            exchange.setProperty(ExchangePropertyKey.FAILURE_ENDPOINT, exchange.getProperty(ExchangePropertyKey.TO_ENDPOINT));
            // and store the route id, so we know in which route we failed
            Route rc = ExchangeHelper.getRoute(exchange);
            if (rc != null) {
                exchange.setProperty(ExchangePropertyKey.FAILURE_ROUTE_ID, rc.getRouteId());
            }

            // create log message
            String msg = "Failed delivery for " + ExchangeHelper.logIds(exchange);
            msg = msg + ". Exhausted after delivery attempt: 1 caught: " + exchange.getException();

            // log that we failed delivery as we are exhausted
            logFailedDelivery(exchange, msg, null);
        }

        private void logFailedDelivery(Exchange exchange, String message, Throwable e) {
            if (logger == null) {
                return;
            }

            if (e == null) {
                e = exchange.getProperty(ExchangePropertyKey.EXCEPTION_CAUGHT, Exception.class);
            }

            if (exchange.isRollbackOnly() || exchange.isRollbackOnlyLast()) {
                String msg = "Rollback " + ExchangeHelper.logIds(exchange);
                Throwable cause = exchange.getException() != null
                        ? exchange.getException() : exchange.getProperty(ExchangePropertyKey.EXCEPTION_CAUGHT, Throwable.class);
                if (cause != null) {
                    msg = msg + " due: " + cause.getMessage();
                }

                // should we include message history
                if (redeliveryPolicy.isLogExhaustedMessageHistory()) {
                    // only use the exchange formatter if we should log exhausted message body (and if using a custom formatter then always use it)
                    ExchangeFormatter formatter = customExchangeFormatter
                            ? exchangeFormatter
                            : (redeliveryPolicy.isLogExhaustedMessageBody() || camelContext.isLogExhaustedMessageBody()
                                    ? exchangeFormatter : null);
                    String routeStackTrace = MessageHelper.dumpMessageHistoryStacktrace(exchange, formatter, false);
                    if (routeStackTrace != null) {
                        msg = msg + "\n" + routeStackTrace;
                    }
                }

                if (logger.getLevel() == LoggingLevel.ERROR) {
                    // log intended rollback on maximum WARN level (not ERROR)
                    logger.log(msg, LoggingLevel.WARN);
                } else {
                    // otherwise use the desired logging level
                    logger.log(msg);
                }
            } else {
                String msg = message;
                // should we include message history
                if (redeliveryPolicy.isLogExhaustedMessageHistory()) {
                    // only use the exchange formatter if we should log exhausted message body (and if using a custom formatter then always use it)
                    ExchangeFormatter formatter = customExchangeFormatter
                            ? exchangeFormatter
                            : (redeliveryPolicy.isLogExhaustedMessageBody() || camelContext.isLogExhaustedMessageBody()
                                    ? exchangeFormatter : null);
                    String routeStackTrace = MessageHelper.dumpMessageHistoryStacktrace(exchange, formatter,
                            e != null && redeliveryPolicy.isLogStackTrace());
                    if (routeStackTrace != null) {
                        msg = msg + "\n" + routeStackTrace;
                    }
                }

                if (e != null && redeliveryPolicy.isLogStackTrace()) {
                    logger.log(msg, e);
                } else {
                    logger.log(msg);
                }
            }
        }
    }

    /**
     * Task to perform calling the processor and handling redelivery if it fails (more advanced than ProcessTask)
     */
    protected class RedeliveryTask implements PooledExchangeTask, Runnable {
        // state
        private Exchange original;
        private Exchange exchange;
        private AsyncCallback callback;
        private int redeliveryCounter;
        private long redeliveryDelay;

        // default behavior which can be overloaded on a per exception basis
        private Predicate retryWhilePredicate;
        private RedeliveryPolicy currentRedeliveryPolicy;
        private Processor failureProcessor;
        private Processor onRedeliveryProcessor;
        private Processor onExceptionProcessor;
        private Predicate handledPredicate;
        private Predicate continuedPredicate;
        private boolean useOriginalInMessage;
        private boolean useOriginalInBody;

        public RedeliveryTask() {
        }

        @Override
        public String toString() {
            return "RedeliveryTask";
        }

        @Override
        public void prepare(Exchange exchange, AsyncCallback callback) {
            this.retryWhilePredicate = retryWhilePolicy;
            this.currentRedeliveryPolicy = redeliveryPolicy;
            this.handledPredicate = getDefaultHandledPredicate();
            this.useOriginalInMessage = useOriginalMessagePolicy;
            this.useOriginalInBody = useOriginalBodyPolicy;
            this.onRedeliveryProcessor = redeliveryProcessor;
            this.onExceptionProcessor = RedeliveryErrorHandler.this.onExceptionProcessor;
            // do a defensive copy of the original Exchange, which is needed for redelivery so we can ensure the
            // original Exchange is being redelivered, and not a mutated Exchange
            this.original = redeliveryEnabled ? defensiveCopyExchangeIfNeeded(exchange) : null;
            this.exchange = exchange;
            this.callback = callback;
        }

        @Override
        public void reset() {
            this.retryWhilePredicate = null;
            this.currentRedeliveryPolicy = null;
            this.handledPredicate = null;
            this.continuedPredicate = null;
            this.useOriginalInMessage = false;
            this.useOriginalInBody = false;
            this.onRedeliveryProcessor = null;
            this.onExceptionProcessor = null;
            this.original = null;
            this.exchange = null;
            this.callback = null;
            this.redeliveryCounter = 0;
            this.redeliveryDelay = 0;
        }

        /**
         * Processing and redelivery logic.
         */
        @Override
        public void run() {
            // can we still run
            if (!isRunAllowed()) {
                LOG.trace("Run not allowed, will reject executing exchange: {}", exchange);
                if (exchange.getException() == null) {
                    exchange.setException(new RejectedExecutionException());
                }
                AsyncCallback cb = callback;
                taskFactory.release(this);
                cb.done(false);
                return;
            }

            try {
                doRun();
            } catch (Exception e) {
                // unexpected exception during running so set exception and trigger callback
                // (do not do taskFactory.release as that happens later)
                exchange.setException(e);
                callback.done(false);
            }
        }

        private void doRun() throws Exception {
            // did previous processing cause an exception?
            if (exchange.getException() != null) {
                handleException();
                onExceptionOccurred();
            }

            // compute if we are exhausted or cannot redeliver
            boolean redeliverAllowed = redeliveryCounter == 0 || isRedeliveryAllowed();
            boolean exhausted = false;
            if (redeliverAllowed) {
                // we can redeliver but check if we are exhausted first (optimized to only check when needed)
                exhausted = exchange.getExchangeExtension().isRedeliveryExhausted() || exchange.isRollbackOnly();
                if (!exhausted && redeliveryCounter > 0) {
                    // its a potential redelivery so determine if we should redeliver or not
                    redeliverAllowed
                            = currentRedeliveryPolicy.shouldRedeliver(exchange, redeliveryCounter, retryWhilePredicate);
                }
            }
            // if we are exhausted or redelivery is not allowed, then deliver to failure processor (eg such as DLC)
            if (!redeliverAllowed || exhausted) {
                Processor target = failureProcessor != null ? failureProcessor : deadLetter;
                // we should always invoke the deliverToFailureProcessor as it prepares, logs and does a fair
                // bit of work for exhausted exchanges (its only the target processor which may be null if handled by a savepoint)
                boolean isDeadLetterChannel = isDeadLetterChannel() && target == deadLetter;
                deliverToFailureProcessor(target, isDeadLetterChannel, exchange);
                // we are breaking out
            } else if (redeliveryCounter > 0) {
                // calculate the redelivery delay
                redeliveryDelay
                        = determineRedeliveryDelay(exchange, currentRedeliveryPolicy, redeliveryDelay, redeliveryCounter);

                if (redeliveryDelay > 0) {
                    // okay there is a delay so create a scheduled task to have it executed in the future

                    if (currentRedeliveryPolicy.isAsyncDelayedRedelivery() && !exchange.isTransacted()) {

                        // we are doing a redelivery then a thread pool must be configured (see the doStart method)
                        ObjectHelper.notNull(executorService,
                                "Redelivery is enabled but ExecutorService has not been configured.", this);

                        // schedule the redelivery task
                        if (LOG.isTraceEnabled()) {
                            LOG.trace("Scheduling redelivery task to run in {} millis for exchangeId: {}", redeliveryDelay,
                                    exchange.getExchangeId());
                        }
                        executorService.schedule(() -> reactiveExecutor.schedule(this::redeliver), redeliveryDelay,
                                TimeUnit.MILLISECONDS);

                    } else {
                        // async delayed redelivery was disabled or we are transacted so we must be synchronous
                        // as the transaction manager requires to execute in the same thread context
                        try {
                            // we are doing synchronous redelivery and use thread sleep, so we keep track using a counter how many are sleeping
                            redeliverySleepCounter.incrementAndGet();
                            boolean complete = sleep();
                            redeliverySleepCounter.decrementAndGet();
                            if (!complete) {
                                // the task was rejected
                                exchange.setException(new RejectedExecutionException("Redelivery not allowed while stopping"));
                                // mark the exchange as redelivery exhausted so the failure processor / dead letter channel can process the exchange
                                exchange.getExchangeExtension().setRedeliveryExhausted(true);
                                // jump to start of loop which then detects that we are failed and exhausted
                                reactiveExecutor.schedule(this);
                            } else {
                                reactiveExecutor.schedule(this::redeliver);
                            }
                        } catch (InterruptedException e) {
                            redeliverySleepCounter.decrementAndGet();
                            // we was interrupted so break out
                            exchange.setException(e);
                            // mark the exchange to stop continue routing when interrupted
                            // as we do not want to continue routing (for example a task has been cancelled)
                            exchange.setRouteStop(true);
                            reactiveExecutor.schedule(callback);

                            Thread.currentThread().interrupt();
                        }
                    }
                } else {
                    // execute the task immediately
                    reactiveExecutor.schedule(this::redeliver);
                }
            } else {
                // Simple delivery
                outputAsync.process(exchange, doneSync -> {
                    // only continue with callback if we are done
                    if (isDone(exchange)) {
                        AsyncCallback cb = callback;
                        taskFactory.release(this);
                        reactiveExecutor.schedule(cb);
                    } else {
                        // error occurred so loop back around and call ourselves
                        reactiveExecutor.schedule(this);
                    }
                });
            }
        }

        protected boolean isRunAllowed() {
            // if camel context is forcing a shutdown then do not allow running
            if (shutdownStrategy.isForceShutdown()) {
                return false;
            }

            // redelivery policy can control if redelivery is allowed during stopping/shutdown
            // but this only applies during a redelivery (counter must > 0)
            if (redeliveryCounter > 0) {
                if (currentRedeliveryPolicy.allowRedeliveryWhileStopping) {
                    return true;
                } else if (preparingShutdown) {
                    // we are preparing for shutdown, now determine if we can still run
                    return isRunAllowedOnPreparingShutdown();
                }
            }

            // we cannot run if we are stopping/stopped
            return !isStoppingOrStopped();
        }

        protected boolean isRedeliveryAllowed() {
            // redelivery policy can control if redelivery is allowed during stopping/shutdown
            // but this only applies during a redelivery (this method is only invoked when counter > 0)
            boolean stopping = isStoppingOrStopped();
            if (!preparingShutdown && !stopping) {
                // we are not preparing to shutdown and are not stopping so we can redeliver
                return true;
            } else {
                // we are stopping or preparing to shutdown, so see policy
                return currentRedeliveryPolicy.allowRedeliveryWhileStopping;
            }
        }

        protected void redeliver() {
            // prepare for redelivery
            prepareExchangeForRedelivery();

            // letting onRedeliver be executed at first
            deliverToOnRedeliveryProcessor();

            if (exchange.isRouteStop()) {
                // the on redelivery can mark that the exchange should stop and therefore not perform a redelivery
                // and if so then we are done so continue callback
                AsyncCallback cb = callback;
                taskFactory.release(this);
                reactiveExecutor.schedule(cb);
                return;
            }

            if (LOG.isTraceEnabled()) {
                LOG.trace("Redelivering exchangeId: {} -> {} for Exchange: {}", exchange.getExchangeId(), outputAsync,
                        exchange);
            }

            // emmit event we are doing redelivery
            if (camelContext.getCamelContextExtension().isEventNotificationApplicable()) {
                EventHelper.notifyExchangeRedelivery(exchange.getContext(), exchange, redeliveryCounter);
            }

            // process the exchange (also redelivery)
            outputAsync.process(exchange, doneSync -> {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Redelivering exchangeId: {}", exchange.getExchangeId());
                }

                // only process if the exchange hasn't failed
                // and it has not been handled by the error processor
                if (isDone(exchange)) {
                    AsyncCallback cb = callback;
                    taskFactory.release(this);
                    reactiveExecutor.schedule(cb);
                } else {
                    // error occurred so loop back around which we do by invoking the processAsyncErrorHandler
                    reactiveExecutor.schedule(this);
                }
            });
        }

        protected void prepareExchangeForContinue(Exchange exchange, boolean isDeadLetterChannel) {
            Exception caught = exchange.getException();
            if (caught != null) {
                // we continue so clear any exceptions
                exchange.setException(null);
            }
            // clear rollback flags
            exchange.setRollbackOnly(false);
            // reset cached streams so they can be read again
            MessageHelper.resetStreamCache(exchange.getIn());

            // its continued then remove traces of redelivery attempted and caught exception
            exchange.getIn().removeHeader(Exchange.REDELIVERED);
            exchange.getIn().removeHeader(Exchange.REDELIVERY_COUNTER);
            exchange.getIn().removeHeader(Exchange.REDELIVERY_MAX_COUNTER);
            exchange.getExchangeExtension().setFailureHandled(false);
            // keep the Exchange.EXCEPTION_CAUGHT as property so end user knows the caused exception

            // create log message
            String msg = "Failed delivery for " + ExchangeHelper.logIds(exchange);
            msg = msg + ". Exhausted after delivery attempt: " + redeliveryCounter + " caught: " + caught;
            msg = msg + ". Handled and continue routing.";

            // log that we failed but want to continue
            logFailedDelivery(false, false, false, true, isDeadLetterChannel, exchange, msg, null);
        }

        protected void prepareExchangeForRedelivery() {
            if (!redeliveryEnabled) {
                throw new IllegalStateException(
                        "Redelivery is not enabled on " + RedeliveryErrorHandler.this
                                                + ". Make sure you have configured the error handler properly.");
            }
            // there must be a defensive copy of the exchange
            ObjectHelper.notNull(this.original, "Defensive copy of Exchange is null", RedeliveryErrorHandler.this);

            // okay we will give it another go so clear the exception so we can try again
            exchange.setException(null);

            // clear rollback flags
            exchange.setRollbackOnly(false);

            // TODO: We may want to store these as state on RedeliveryData so we keep them in case end user messes with Exchange
            // and then put these on the exchange when doing a redelivery / fault processor

            // preserve these headers
            Integer redeliveryCounter = exchange.getIn().getHeader(Exchange.REDELIVERY_COUNTER, Integer.class);
            Integer redeliveryMaxCounter = exchange.getIn().getHeader(Exchange.REDELIVERY_MAX_COUNTER, Integer.class);
            Boolean redelivered = exchange.getIn().getHeader(Exchange.REDELIVERED, Boolean.class);

            // we are redelivering so copy from original back to exchange
            exchange.getIn().copyFrom(this.original.getIn());
            exchange.setOut(null);
            // reset cached streams so they can be read again
            MessageHelper.resetStreamCache(exchange.getIn());

            // put back headers
            if (redeliveryCounter != null) {
                exchange.getIn().setHeader(Exchange.REDELIVERY_COUNTER, redeliveryCounter);
            }
            if (redeliveryMaxCounter != null) {
                exchange.getIn().setHeader(Exchange.REDELIVERY_MAX_COUNTER, redeliveryMaxCounter);
            }
            if (redelivered != null) {
                exchange.getIn().setHeader(Exchange.REDELIVERED, redelivered);
            }
        }

        protected void handleException() {
            Exception e = exchange.getException();
            // e is never null

            Throwable previous = exchange.getProperty(ExchangePropertyKey.EXCEPTION_CAUGHT, Throwable.class);
            if (previous != null && previous != e) {
                // a 2nd exception was thrown while handling a previous exception
                // so we need to add the previous as suppressed by the new exception
                // see also FatalFallbackErrorHandler
                Throwable[] suppressed = e.getSuppressed();
                boolean found = false;
                for (Throwable t : suppressed) {
                    if (t == previous) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    // okay before adding suppression then we must be sure its not referring to same method
                    // which otherwise can lead to add the same exception over and over again
                    StackTraceElement[] ste1 = e.getStackTrace();
                    StackTraceElement[] ste2 = previous.getStackTrace();
                    boolean same = false;
                    if (ste1 != null && ste2 != null && ste1.length > 0 && ste2.length > 0) {
                        same = ste1[0].getClassName().equals(ste2[0].getClassName())
                                && ste1[0].getLineNumber() == ste2[0].getLineNumber();
                    }
                    if (!same) {
                        e.addSuppressed(previous);
                    }
                }
            }

            // store the original caused exception in a property, so we can restore it later
            exchange.setProperty(ExchangePropertyKey.EXCEPTION_CAUGHT, e);

            // find the error handler to use (if any)
            ExceptionPolicy exceptionPolicy = getExceptionPolicy(exchange, e);
            if (exceptionPolicy != null) {
                currentRedeliveryPolicy
                        = exceptionPolicy.createRedeliveryPolicy(exchange.getContext(), currentRedeliveryPolicy);
                handledPredicate = exceptionPolicy.getHandledPolicy();
                continuedPredicate = exceptionPolicy.getContinuedPolicy();
                retryWhilePredicate = exceptionPolicy.getRetryWhilePolicy();
                useOriginalInMessage = exceptionPolicy.isUseOriginalInMessage();
                useOriginalInBody = exceptionPolicy.isUseOriginalInBody();

                // route specific failure handler?
                Processor processor = null;
                Route rc = ExchangeHelper.getRoute(exchange);
                if (rc != null) {
                    processor = rc.getOnException(exceptionPolicy.getId());
                } else {
                    // note this should really not happen, but we have this code as a fail safe
                    // to be backwards compatible with the old behavior
                    LOG.warn(
                            "Cannot determine current route from Exchange with id: {}, will fallback and use first error handler.",
                            exchange.getExchangeId());
                }
                if (processor != null) {
                    failureProcessor = processor;
                }

                // route specific on redelivery?
                processor = exceptionPolicy.getOnRedelivery();
                if (processor != null) {
                    onRedeliveryProcessor = processor;
                }
                // route specific on exception occurred?
                processor = exceptionPolicy.getOnExceptionOccurred();
                if (processor != null) {
                    onExceptionProcessor = processor;
                }
            }

            // only log if not failure handled or not an exhausted unit of work
            if (!ExchangeHelper.isFailureHandled(exchange) && !ExchangeHelper.isUnitOfWorkExhausted(exchange)) {
                String msg = "Failed delivery for " + ExchangeHelper.logIds(exchange)
                             + ". On delivery attempt: " + redeliveryCounter + " caught: " + e;
                logFailedDelivery(true, false, false, false, isDeadLetterChannel(), exchange, msg, e);
            }

            redeliveryCounter = incrementRedeliveryCounter(exchange);
        }

        /**
         * Gives an optional configured OnExceptionOccurred processor a chance to process just after an exception was
         * thrown while processing the Exchange. This allows to execute the processor at the same time the exception was
         * thrown.
         */
        protected void onExceptionOccurred() {
            if (onExceptionProcessor == null) {
                return;
            }

            // run this synchronously as its just a Processor
            try {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("OnExceptionOccurred processor {} is processing Exchange: {} due exception occurred",
                            onExceptionProcessor, exchange);
                }
                onExceptionProcessor.process(exchange);
            } catch (Exception e) {
                // we dont not want new exception to override existing, so log it as a WARN
                LOG.warn("Error during processing OnExceptionOccurred. This exception is ignored.", e);
            }
            LOG.trace("OnExceptionOccurred processor done");
        }

        /**
         * Gives an optional configured redelivery processor a chance to process before the Exchange will be
         * redelivered. This can be used to alter the Exchange.
         */
        protected void deliverToOnRedeliveryProcessor() {
            if (onRedeliveryProcessor == null) {
                return;
            }

            if (LOG.isTraceEnabled()) {
                LOG.trace("Redelivery processor {} is processing Exchange: {} before its redelivered",
                        onRedeliveryProcessor, exchange);
            }

            // run this synchronously as its just a Processor
            try {
                onRedeliveryProcessor.process(exchange);
            } catch (Exception e) {
                exchange.setException(e);
            }
            LOG.trace("Redelivery processor done");
        }

        /**
         * All redelivery attempts failed so move the exchange to the dead letter queue
         */
        protected void deliverToFailureProcessor(
                final Processor processor, final boolean isDeadLetterChannel, final Exchange exchange) {

            // we did not success with the redelivery so now we let the failure processor handle it
            // clear exception as we let the failure processor handle it
            Exception caught = exchange.getException();
            if (caught != null) {
                exchange.setException(null);
            }

            final boolean shouldHandle = shouldHandle(exchange);
            final boolean shouldContinue = shouldContinue(exchange);
            // regard both handled or continued as being handled
            boolean handled = false;

            // always handle if dead letter channel
            boolean handleOrContinue = isDeadLetterChannel || shouldHandle || shouldContinue;
            if (handleOrContinue) {
                // its handled then remove traces of redelivery attempted
                exchange.getIn().removeHeader(Exchange.REDELIVERED);
                exchange.getIn().removeHeader(Exchange.REDELIVERY_COUNTER);
                exchange.getIn().removeHeader(Exchange.REDELIVERY_MAX_COUNTER);
                exchange.getExchangeExtension().setRedeliveryExhausted(false);

                // and remove traces of rollback only and uow exhausted markers
                exchange.setRollbackOnly(false);
                exchange.removeProperty(ExchangePropertyKey.UNIT_OF_WORK_EXHAUSTED);

                handled = true;
            } else {
                // must decrement the redelivery counter as we didn't process the redelivery but is
                // handling by the failure handler. So we must -1 to not let the counter be out-of-sync
                decrementRedeliveryCounter(exchange);
            }

            // we should allow using the failure processor if we should not continue
            // or in case of continue then the failure processor is NOT a dead letter channel
            // because you can continue and still let the failure processor do some routing
            // before continue in the main route.
            boolean allowFailureProcessor = !shouldContinue || !isDeadLetterChannel;
            final boolean fHandled = handled;

            if (allowFailureProcessor && processor != null) {

                // prepare original IN message/body if it should be moved instead of current message/body
                if (useOriginalInMessage || useOriginalInBody) {
                    Message original = ExchangeHelper.getOriginalInMessage(exchange);
                    if (useOriginalInMessage) {
                        LOG.trace("Using the original IN message instead of current");
                        exchange.setIn(original);
                    } else {
                        LOG.trace("Using the original IN message body instead of current");
                        exchange.getIn().setBody(original.getBody());
                    }
                    if (exchange.hasOut()) {
                        LOG.trace("Removing the out message to avoid some uncertain behavior");
                        exchange.setOut(null);
                    }
                }

                // reset cached streams so they can be read again
                MessageHelper.resetStreamCache(exchange.getIn());

                // store the last to endpoint as the failure endpoint
                exchange.setProperty(ExchangePropertyKey.FAILURE_ENDPOINT,
                        exchange.getProperty(ExchangePropertyKey.TO_ENDPOINT));
                // and store the route id, so we know in which route we failed
                Route rc = ExchangeHelper.getRoute(exchange);
                if (rc != null) {
                    exchange.setProperty(ExchangePropertyKey.FAILURE_ROUTE_ID, rc.getRouteId());
                }

                // invoke custom on prepare
                if (onPrepareProcessor != null) {
                    try {
                        LOG.trace("OnPrepare processor {} is processing Exchange: {}", onPrepareProcessor, exchange);
                        onPrepareProcessor.process(exchange);
                    } catch (Exception e) {
                        // a new exception was thrown during prepare
                        exchange.setException(e);
                    }
                }

                LOG.trace("Failure processor {} is processing Exchange: {}", processor, exchange);

                // fire event as we had a failure processor to handle it, which there is a event for
                final boolean deadLetterChannel = processor == deadLetter;

                if (camelContext.getCamelContextExtension().isEventNotificationApplicable()) {
                    EventHelper.notifyExchangeFailureHandling(exchange.getContext(), exchange, processor, deadLetterChannel,
                            deadLetterUri);
                }

                // the failure processor could also be asynchronous
                AsyncProcessor afp = AsyncProcessorConverterHelper.convert(processor);
                afp.process(exchange, sync -> {
                    LOG.trace("Failure processor done: {} processing Exchange: {}", processor, exchange);
                    try {
                        prepareExchangeAfterFailure(exchange, isDeadLetterChannel, shouldHandle, shouldContinue);
                        // fire event as we had a failure processor to handle it, which there is a event for
                        if (camelContext.getCamelContextExtension().isEventNotificationApplicable()) {
                            EventHelper.notifyExchangeFailureHandled(exchange.getContext(), exchange, processor,
                                    deadLetterChannel, deadLetterUri);
                        }
                    } finally {
                        // if the fault was handled asynchronously, this should be reflected in the callback as well
                        reactiveExecutor.schedule(callback);

                        // create log message
                        String msg = "Failed delivery for " + ExchangeHelper.logIds(exchange);
                        msg = msg + ". Exhausted after delivery attempt: " + redeliveryCounter + " caught: " + caught;
                        if (processor != null) {
                            if (isDeadLetterChannel && deadLetterUri != null) {
                                msg = msg + ". Handled by DeadLetterChannel: [" + URISupport.sanitizeUri(deadLetterUri) + "]";
                            } else {
                                msg = msg + ". Processed by failure processor: " + processor;
                            }
                        }

                        // log that we failed delivery as we are exhausted
                        logFailedDelivery(false, false, fHandled, false, isDeadLetterChannel, exchange, msg, null);

                        // we are done so we can release the task
                        taskFactory.release(this);
                    }
                });
            } else {
                try {
                    // store the last to endpoint as the failure endpoint
                    exchange.setProperty(ExchangePropertyKey.FAILURE_ENDPOINT,
                            exchange.getProperty(ExchangePropertyKey.TO_ENDPOINT));
                    // and store the route id, so we know in which route we failed
                    Route rc = ExchangeHelper.getRoute(exchange);
                    if (rc != null) {
                        exchange.setProperty(ExchangePropertyKey.FAILURE_ROUTE_ID, rc.getRouteId());
                    }

                    // invoke custom on prepare
                    if (onPrepareProcessor != null) {
                        try {
                            LOG.trace("OnPrepare processor {} is processing Exchange: {}", onPrepareProcessor, exchange);
                            onPrepareProcessor.process(exchange);
                        } catch (Exception e) {
                            // a new exception was thrown during prepare
                            exchange.setException(e);
                        }
                    }
                    // no processor but we need to prepare after failure as well
                    prepareExchangeAfterFailure(exchange, isDeadLetterChannel, shouldHandle, shouldContinue);
                } finally {
                    // callback we are done
                    reactiveExecutor.schedule(callback);

                    // create log message
                    String msg = "Failed delivery for " + ExchangeHelper.logIds(exchange);
                    msg = msg + ". Exhausted after delivery attempt: " + redeliveryCounter + " caught: " + caught;
                    if (processor != null) {
                        if (isDeadLetterChannel && deadLetterUri != null) {
                            msg = msg + ". Handled by DeadLetterChannel: [" + URISupport.sanitizeUri(deadLetterUri) + "]";
                        } else {
                            msg = msg + ". Processed by failure processor: " + processor;
                        }
                    }

                    // log that we failed delivery as we are exhausted
                    logFailedDelivery(false, false, fHandled, false, isDeadLetterChannel, exchange, msg, null);

                    // we are done so we can release the task
                    taskFactory.release(this);
                }
            }
        }

        protected void prepareExchangeAfterFailure(
                final Exchange exchange, final boolean isDeadLetterChannel,
                final boolean shouldHandle, final boolean shouldContinue) {

            Exception newException = exchange.getException();

            // we could not process the exchange so we let the failure processor handled it
            ExchangeHelper.setFailureHandled(exchange);

            // honor if already set a handling
            boolean alreadySet = exchange.getExchangeExtension().isErrorHandlerHandledSet();
            if (alreadySet) {
                boolean handled = exchange.getExchangeExtension().isErrorHandlerHandled();
                LOG.trace("This exchange has already been marked for handling: {}", handled);
                if (!handled) {
                    // exception not handled, put exception back in the exchange
                    exchange.setException(exchange.getProperty(ExchangePropertyKey.EXCEPTION_CAUGHT, Exception.class));
                    // and put failure endpoint back as well
                    exchange.setProperty(ExchangePropertyKey.FAILURE_ENDPOINT,
                            exchange.getProperty(ExchangePropertyKey.TO_ENDPOINT));
                }
                return;
            }

            // dead letter channel is special
            if (shouldContinue) {
                LOG.trace("This exchange is continued: {}", exchange);
                // okay we want to continue then prepare the exchange for that as well
                prepareExchangeForContinue(exchange, isDeadLetterChannel);
            } else if (shouldHandle) {
                LOG.trace("This exchange is handled so its marked as not failed: {}", exchange);
                exchange.getExchangeExtension().setErrorHandlerHandled(true);
            } else {
                // okay the redelivery policy are not explicit set to true, so we should allow to check for some
                // special situations when using dead letter channel
                if (isDeadLetterChannel) {

                    // DLC is always handling the first thrown exception,
                    // but if its a new exception then use the configured option
                    boolean handled = newException == null || deadLetterHandleNewException;

                    // when using DLC then log new exception whether its being handled or not, as otherwise it may appear as
                    // the DLC swallow new exceptions by default (which is by design to ensure the DLC always complete,
                    // to avoid causing endless poison messages that fails forever)
                    if (newException != null && currentRedeliveryPolicy.isLogNewException()) {
                        String uri = URISupport.sanitizeUri(deadLetterUri);
                        String msg = "New exception occurred during processing by the DeadLetterChannel[" + uri + "] due "
                                     + newException.getMessage();
                        if (handled) {
                            msg += ". The new exception is being handled as deadLetterHandleNewException=true.";
                        } else {
                            msg += ". The new exception is not handled as deadLetterHandleNewException=false.";
                        }
                        logFailedDelivery(false, true, handled, false, true, exchange, msg, newException);
                    }

                    if (handled) {
                        LOG.trace("This exchange is handled so its marked as not failed: {}", exchange);
                        exchange.getExchangeExtension().setErrorHandlerHandled(true);
                        return;
                    }
                }

                // not handled by default
                prepareExchangeAfterFailureNotHandled(exchange);
            }
        }

        private void prepareExchangeAfterFailureNotHandled(Exchange exchange) {
            LOG.trace("This exchange is not handled or continued so its marked as failed: {}", exchange);
            // exception not handled, put exception back in the exchange
            exchange.getExchangeExtension().setErrorHandlerHandled(false);
            exchange.setException(exchange.getProperty(ExchangePropertyKey.EXCEPTION_CAUGHT, Exception.class));
            // and put failure endpoint back as well
            exchange.setProperty(ExchangePropertyKey.FAILURE_ENDPOINT, exchange.getProperty(ExchangePropertyKey.TO_ENDPOINT));
            // and store the route id so we know in which route we failed
            String routeId = ExchangeHelper.getAtRouteId(exchange);
            if (routeId != null) {
                exchange.setProperty(ExchangePropertyKey.FAILURE_ROUTE_ID, routeId);
            }
        }

        private void logFailedDelivery(
                boolean shouldRedeliver, boolean newException, boolean handled, boolean continued, boolean isDeadLetterChannel,
                Exchange exchange, String message, Throwable e) {
            if (logger == null) {
                return;
            }

            if (!exchange.isRollbackOnly() && !exchange.isRollbackOnlyLast()) {
                if (newException && !currentRedeliveryPolicy.isLogNewException()) {
                    // do not log new exception
                    return;
                }

                // if we should not rollback, then check whether logging is enabled

                if (!newException && handled && !currentRedeliveryPolicy.isLogHandled()) {
                    // do not log handled
                    return;
                }

                if (!newException && continued && !currentRedeliveryPolicy.isLogContinued()) {
                    // do not log handled
                    return;
                }

                if (!newException && shouldRedeliver && !currentRedeliveryPolicy.isLogRetryAttempted()) {
                    // do not log retry attempts
                    return;
                }

                if (!newException && shouldRedeliver) {
                    if (currentRedeliveryPolicy.isLogRetryAttempted()) {
                        if (currentRedeliveryPolicy.getRetryAttemptedLogInterval() > 1
                                && redeliveryCounter % currentRedeliveryPolicy.getRetryAttemptedLogInterval() != 0) {
                            // do not log retry attempt because it is excluded by the retryAttemptedLogInterval
                            return;
                        }
                    } else {
                        // do not log retry attempts
                        return;
                    }
                }

                if (!newException && !shouldRedeliver && !currentRedeliveryPolicy.isLogExhausted()) {
                    // do not log exhausted
                    return;
                }
            }

            LoggingLevel newLogLevel;
            boolean logStackTrace;
            if (exchange.isRollbackOnly() || exchange.isRollbackOnlyLast()) {
                newLogLevel = currentRedeliveryPolicy.getRetriesExhaustedLogLevel();
                logStackTrace = currentRedeliveryPolicy.isLogStackTrace();
            } else if (shouldRedeliver) {
                newLogLevel = currentRedeliveryPolicy.getRetryAttemptedLogLevel();
                logStackTrace = currentRedeliveryPolicy.isLogRetryStackTrace();
            } else {
                newLogLevel = currentRedeliveryPolicy.getRetriesExhaustedLogLevel();
                logStackTrace = currentRedeliveryPolicy.isLogStackTrace();
            }
            if (e == null) {
                e = exchange.getProperty(ExchangePropertyKey.EXCEPTION_CAUGHT, Exception.class);
            }

            if (newException) {
                // log at most WARN level
                if (newLogLevel == LoggingLevel.ERROR) {
                    newLogLevel = LoggingLevel.WARN;
                }
                String msg = message;
                if (msg == null) {
                    msg = "New exception " + ExchangeHelper.logIds(exchange);
                    // special for logging the new exception
                    if (e != null) {
                        msg = msg + " due: " + e.getMessage();
                    }
                }

                if (e != null && logStackTrace) {
                    logger.log(msg, e, newLogLevel);
                } else {
                    logger.log(msg, newLogLevel);
                }
            } else if (exchange.isRollbackOnly() || exchange.isRollbackOnlyLast()) {
                String msg = "Rollback " + ExchangeHelper.logIds(exchange);
                Throwable cause = exchange.getException() != null
                        ? exchange.getException() : exchange.getProperty(ExchangePropertyKey.EXCEPTION_CAUGHT, Throwable.class);
                if (cause != null) {
                    msg = msg + " due: " + cause.getMessage();
                }

                // should we include message history
                if (!shouldRedeliver && currentRedeliveryPolicy.isLogExhaustedMessageHistory()) {
                    // only use the exchange formatter if we should log exhausted message body (and if using a custom formatter then always use it)
                    ExchangeFormatter formatter = customExchangeFormatter
                            ? exchangeFormatter
                            : (currentRedeliveryPolicy.isLogExhaustedMessageBody() || camelContext.isLogExhaustedMessageBody()
                                    ? exchangeFormatter : null);
                    String routeStackTrace = MessageHelper.dumpMessageHistoryStacktrace(exchange, formatter, false);
                    if (routeStackTrace != null) {
                        msg = msg + "\n" + routeStackTrace;
                    }
                }

                if (newLogLevel == LoggingLevel.ERROR) {
                    // log intended rollback on maximum WARN level (not ERROR)
                    logger.log(msg, LoggingLevel.WARN);
                } else {
                    // otherwise use the desired logging level
                    logger.log(msg, newLogLevel);
                }
            } else {
                String msg = message;
                // should we include message history
                if (!shouldRedeliver && currentRedeliveryPolicy.isLogExhaustedMessageHistory()) {
                    // only use the exchange formatter if we should log exhausted message body (and if using a custom formatter then always use it)
                    ExchangeFormatter formatter = customExchangeFormatter
                            ? exchangeFormatter
                            : (currentRedeliveryPolicy.isLogExhaustedMessageBody() || camelContext.isLogExhaustedMessageBody()
                                    ? exchangeFormatter : null);
                    String routeStackTrace
                            = MessageHelper.dumpMessageHistoryStacktrace(exchange, formatter, e != null && logStackTrace);
                    if (routeStackTrace != null) {
                        msg = msg + "\n" + routeStackTrace;
                    }
                }

                if (e != null && logStackTrace) {
                    logger.log(msg, e, newLogLevel);
                } else {
                    logger.log(msg, newLogLevel);
                }
            }
        }

        /**
         * Determines whether or not to continue if we are exhausted.
         *
         * @param  exchange the current exchange
         * @return          <tt>true</tt> to continue, or <tt>false</tt> to exhaust.
         */
        private boolean shouldContinue(Exchange exchange) {
            if (continuedPredicate != null) {
                return continuedPredicate.matches(exchange);
            }
            // do not continue by default
            return false;
        }

        /**
         * Determines whether or not to handle if we are exhausted.
         *
         * @param  exchange the current exchange
         * @return          <tt>true</tt> to handle, or <tt>false</tt> to exhaust.
         */
        private boolean shouldHandle(Exchange exchange) {
            if (handledPredicate != null) {
                return handledPredicate.matches(exchange);
            }
            // do not handle by default
            return false;
        }

        /**
         * Increments the redelivery counter and adds the redelivered flag if the message has been redelivered
         */
        private int incrementRedeliveryCounter(Exchange exchange) {
            Message in = exchange.getIn();
            Integer counter = in.getHeader(Exchange.REDELIVERY_COUNTER, Integer.class);
            int next = counter != null ? counter + 1 : 1;
            in.setHeader(Exchange.REDELIVERY_COUNTER, next);
            in.setHeader(Exchange.REDELIVERED, Boolean.TRUE);
            // if maximum redeliveries is used, then provide that information as well
            if (currentRedeliveryPolicy.getMaximumRedeliveries() > 0) {
                in.setHeader(Exchange.REDELIVERY_MAX_COUNTER, currentRedeliveryPolicy.getMaximumRedeliveries());
            }
            return next;
        }

        /**
         * Method for sleeping during redelivery attempts.
         * <p/>
         * This task is for the synchronous blocking. If using async delayed then a scheduled thread pool is used for
         * sleeping and trigger redeliveries.
         */
        public boolean sleep() throws InterruptedException {
            // for small delays then just sleep
            if (redeliveryDelay < 1000) {
                currentRedeliveryPolicy.sleep(redeliveryDelay);
                return true;
            }

            StopWatch watch = new StopWatch();

            LOG.debug("Sleeping for: {} millis until attempting redelivery", redeliveryDelay);
            while (watch.taken() < redeliveryDelay) {
                // sleep using 1 sec interval

                long delta = redeliveryDelay - watch.taken();
                long max = Math.min(1000, delta);
                if (max > 0) {
                    LOG.trace("Sleeping for: {} millis until waking up for re-check", max);
                    Thread.sleep(max);
                }

                // are we preparing for shutdown then only do redelivery if allowed
                if (preparingShutdown && !currentRedeliveryPolicy.isAllowRedeliveryWhileStopping()) {
                    LOG.debug("Rejected redelivery while stopping");
                    return false;
                }
            }

            return true;
        }
    }

    /**
     * Prepares the redelivery counter and boolean flag for the failure handle processor
     */
    private void decrementRedeliveryCounter(Exchange exchange) {
        Message in = exchange.getIn();
        Integer counter = in.getHeader(Exchange.REDELIVERY_COUNTER, Integer.class);
        if (counter != null) {
            int prev = counter - 1;
            in.setHeader(Exchange.REDELIVERY_COUNTER, prev);
            // set boolean flag according to counter
            in.setHeader(Exchange.REDELIVERED, prev > 0 ? Boolean.TRUE : Boolean.FALSE);
        } else {
            // not redelivered
            in.setHeader(Exchange.REDELIVERY_COUNTER, 0);
            in.setHeader(Exchange.REDELIVERED, Boolean.FALSE);
        }
    }

    @Override
    public boolean determineIfRedeliveryIsEnabled() throws Exception {
        // determine if redeliver is enabled either on error handler
        if (getRedeliveryPolicy().getMaximumRedeliveries() != 0) {
            // must check for != 0 as (-1 means redeliver forever)
            return true;
        }
        if (retryWhilePolicy != null) {
            return true;
        }

        // or on the exception policies
        if (exceptionPolicies != null && !exceptionPolicies.isEmpty()) {
            // walk them to see if any of them have a maximum redeliveries > 0 or retry until set
            for (ExceptionPolicy def : exceptionPolicies.values()) {
                if (def.determineIfRedeliveryIsEnabled(camelContext)) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    protected void doStart() throws Exception {
        // determine if redeliver is enabled or not
        redeliveryEnabled = determineIfRedeliveryIsEnabled();
        if (LOG.isTraceEnabled()) {
            LOG.trace("Redelivery enabled: {} on error handler: {}", redeliveryEnabled, this);
        }

        // we only need thread pool if redelivery is enabled
        if (redeliveryEnabled) {
            if (executorService == null) {
                // use default shared executor service
                executorService = PluginHelper.getErrorHandlerExecutorService(camelContext);
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("Using ExecutorService: {} for redeliveries on error handler: {}", executorService, this);
            }
        }

        // reset flag when starting
        preparingShutdown = false;
        redeliverySleepCounter.set(0);

        // calculate if we can use simple task or not
        // if we need redelivery and other things then we cannot)
        // however if we dont then its less memory overhead (and a bit less cpu) of using the simple task
        simpleTask = deadLetter == null && !redeliveryEnabled && (exceptionPolicies == null || exceptionPolicies.isEmpty())
                && onPrepareProcessor == null;

        boolean pooled = camelContext.getCamelContextExtension().getExchangeFactory().isPooled();
        if (pooled) {
            String id = output instanceof IdAware ? ((IdAware) output).getId() : output.toString();
            taskFactory = new PooledTaskFactory(id) {
                @Override
                public PooledExchangeTask create(Exchange exchange, AsyncCallback callback) {
                    return simpleTask ? new SimpleTask() : new RedeliveryTask();
                }
            };
            int capacity = camelContext.getCamelContextExtension().getExchangeFactory().getCapacity();
            taskFactory.setCapacity(capacity);
        } else {
            taskFactory = new PrototypeTaskFactory() {
                @Override
                public PooledExchangeTask create(Exchange exchange, AsyncCallback callback) {
                    return simpleTask ? new SimpleTask() : new RedeliveryTask();
                }
            };
        }
        LOG.trace("Using TaskFactory: {}", taskFactory);

        ServiceHelper.startService(taskFactory, output, outputAsync, deadLetter);
    }

    @Override
    protected void doShutdown() throws Exception {
        ServiceHelper.stopAndShutdownServices(deadLetter, output, outputAsync, taskFactory);
    }

}
