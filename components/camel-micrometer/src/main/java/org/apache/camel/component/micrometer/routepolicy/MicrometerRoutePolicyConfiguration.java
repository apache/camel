package org.apache.camel.component.micrometer.routepolicy;

import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Timer;

import java.util.function.Consumer;

public class MicrometerRoutePolicyConfiguration {
    public static final MicrometerRoutePolicyConfiguration DEFAULT = new MicrometerRoutePolicyConfiguration();
    private boolean additionalCounters = true;
    private boolean exchangesSucceeded = true;
    private boolean exchangesFailed = true;
    private boolean exchangesTotal = true;
    private boolean externalRedeliveries = true;
    private boolean failuresHandled = true;
    private boolean longTask = false;
    private Consumer<Timer.Builder> timerInitiator;
    private Consumer<LongTaskTimer.Builder> longTaskInitiator;

    public boolean isAdditionalCounters() {
        return additionalCounters;
    }

    public void setAdditionalCounters(boolean additionalCounters) {
        this.additionalCounters = additionalCounters;
    }

    public boolean isExchangesSucceeded() {
        return exchangesSucceeded;
    }

    public void setExchangesSucceeded(boolean exchangesSucceeded) {
        this.exchangesSucceeded = exchangesSucceeded;
    }

    public boolean isExchangesFailed() {
        return exchangesFailed;
    }

    public void setExchangesFailed(boolean exchangesFailed) {
        this.exchangesFailed = exchangesFailed;
    }

    public boolean isExchangesTotal() {
        return exchangesTotal;
    }

    public void setExchangesTotal(boolean exchangesTotal) {
        this.exchangesTotal = exchangesTotal;
    }

    public boolean isExternalRedeliveries() {
        return externalRedeliveries;
    }

    public void setExternalRedeliveries(boolean externalRedeliveries) {
        this.externalRedeliveries = externalRedeliveries;
    }

    public boolean isFailuresHandled() {
        return failuresHandled;
    }

    public void setFailuresHandled(boolean failuresHandled) {
        this.failuresHandled = failuresHandled;
    }

    public boolean isLongTask() {
        return longTask;
    }

    public void setLongTask(boolean longTask) {
        this.longTask = longTask;
    }

    public Consumer<Timer.Builder> getTimerInitiator() {
        return timerInitiator;
    }

    public void setTimerInitiator(Consumer<Timer.Builder> timerInitiator) {
        this.timerInitiator = timerInitiator;
    }

    public Consumer<LongTaskTimer.Builder> getLongTaskInitiator() {
        return longTaskInitiator;
    }

    public void setLongTaskInitiator(Consumer<LongTaskTimer.Builder> longTaskInitiator) {
        this.longTaskInitiator = longTaskInitiator;
    }
}
