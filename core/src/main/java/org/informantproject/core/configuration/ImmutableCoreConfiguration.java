/**
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.informantproject.core.configuration;

import com.google.common.base.Objects;
import com.google.common.base.Objects.ToStringHelper;
import com.google.gson.Gson;

/**
 * Immutable structure to hold the current core configuration.
 * 
 * Default values should be conservative.
 * 
 * @author Trask Stalnaker
 * @since 0.5
 */
public class ImmutableCoreConfiguration {

    public static final int SPAN_LIMIT_DISABLED = -1;
    public static final int THRESHOLD_DISABLED = -1;

    // if tracing is disabled mid-trace there should be no issue
    // active traces will not accumulate additional spans
    // but they will be logged / emailed if they exceed the defined thresholds
    //
    // if tracing is enabled mid-trace there should be no issue
    // active traces that were not captured at their start will
    // continue not to accumulate spans
    // and they will not be logged / emailed even if they exceed the defined
    // thresholds
    private boolean enabled = true;

    // TODO convert from millis to seconds, support 0.1, etc
    // 0 means log all traces, -1 means log no traces
    // (though stuck threshold can still be used in this case)
    private int thresholdMillis = 30000;

    // minimum is imposed because of StuckTraceCollector#CHECK_INTERVAL_MILLIS
    // -1 means no stuck messages are gathered, should be minimum 100 milliseconds
    private int stuckThresholdMillis = 600000;

    // minimum is imposed because of StackCollector#CHECK_INTERVAL_MILLIS
    // -1 means no stack traces are gathered, should be minimum 100 milliseconds
    private int stackTraceInitialDelayMillis = 5000;

    private int stackTracePeriodMillis = 1000;

    // TODO this doesn't really make sense for Filters/servlets? or maybe just not top-level?
    // though even those might be interesting occasionally
    // TODO also re-think the name
    private int spanStackTraceThresholdMillis = 5000;

    // used to limit memory requirement, also used to help limit log file size,
    // 0 means don't capture any traces, -1 means no limit
    private int maxSpansPerTrace = 1000;

    // size of fixed-length rolling database for storing trace details (spans and merged stack
    // traces)
    private int rollingSizeMb = 100;

    private boolean warnOnSpanOutsideTrace = false;

    private int metricPeriodMillis = 15000;

    public boolean isEnabled() {
        return enabled;
    }

    public int getThresholdMillis() {
        return thresholdMillis;
    }

    public int getStuckThresholdMillis() {
        return stuckThresholdMillis;
    }

    public int getStackTraceInitialDelayMillis() {
        return stackTraceInitialDelayMillis;
    }

    public int getStackTracePeriodMillis() {
        return stackTracePeriodMillis;
    }

    public int getSpanStackTraceThresholdMillis() {
        return spanStackTraceThresholdMillis;
    }

    public int getMaxSpansPerTrace() {
        return maxSpansPerTrace;
    }

    public int getRollingSizeMb() {
        return rollingSizeMb;
    }

    public boolean isWarnOnSpanOutsideTrace() {
        return warnOnSpanOutsideTrace;
    }

    public int getMetricPeriodMillis() {
        return metricPeriodMillis;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }

    @Override
    public String toString() {
        ToStringHelper toStringHelper = Objects.toStringHelper(this)
                .add("enabed", enabled)
                .add("thresholdMillis", thresholdMillis)
                .add("stuckThresholdMillis", stuckThresholdMillis)
                .add("stackTraceInitialDelayMillis", stackTraceInitialDelayMillis)
                .add("stackTracePeriodMillis", stackTracePeriodMillis)
                .add("spanStackTraceThresholdMillis", spanStackTraceThresholdMillis)
                .add("maxSpansPerTrace", maxSpansPerTrace)
                .add("rollingSizeMb", rollingSizeMb)
                .add("warnOnSpanOutsideTrace", warnOnSpanOutsideTrace)
                .add("metricPeriodMillis", metricPeriodMillis);
        return toStringHelper.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ImmutableCoreConfiguration)) {
            return false;
        }
        ImmutableCoreConfiguration other = (ImmutableCoreConfiguration) o;
        return Objects.equal(enabled, other.isEnabled())
                && Objects.equal(thresholdMillis, other.getThresholdMillis())
                && Objects.equal(stuckThresholdMillis, other.getStuckThresholdMillis())
                && Objects.equal(stackTraceInitialDelayMillis,
                        other.getStackTraceInitialDelayMillis())
                && Objects.equal(stackTracePeriodMillis, other.getStackTracePeriodMillis())
                && Objects.equal(spanStackTraceThresholdMillis,
                        other.getSpanStackTraceThresholdMillis())
                && Objects.equal(maxSpansPerTrace, other.getMaxSpansPerTrace())
                && Objects.equal(rollingSizeMb, other.getRollingSizeMb())
                && Objects.equal(warnOnSpanOutsideTrace, other.isWarnOnSpanOutsideTrace())
                && Objects.equal(metricPeriodMillis, other.getMetricPeriodMillis());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(enabled, thresholdMillis, stuckThresholdMillis,
                stackTraceInitialDelayMillis, stackTracePeriodMillis,
                spanStackTraceThresholdMillis, maxSpansPerTrace, rollingSizeMb,
                warnOnSpanOutsideTrace, metricPeriodMillis);
    }

    static ImmutableCoreConfiguration fromJson(String json) {
        return new Gson().fromJson(json, CoreConfigurationBuilder.class).build();
    }

    static class CoreConfigurationBuilder {

        private boolean enabled;
        private int thresholdMillis;
        private int stuckThresholdMillis;
        private int stackTraceInitialDelayMillis;
        private int stackTracePeriodMillis;
        private int spanStackTraceThresholdMillis;
        private int maxSpansPerTrace;
        private int rollingSizeMb;
        private boolean warnOnSpanOutsideTrace;
        private int metricPeriodMillis;

        CoreConfigurationBuilder() {
            this(new ImmutableCoreConfiguration());
        }

        CoreConfigurationBuilder(ImmutableCoreConfiguration base) {
            enabled = base.enabled;
            thresholdMillis = base.thresholdMillis;
            stuckThresholdMillis = base.stuckThresholdMillis;
            stackTraceInitialDelayMillis = base.stackTraceInitialDelayMillis;
            stackTracePeriodMillis = base.stackTracePeriodMillis;
            spanStackTraceThresholdMillis = base.spanStackTraceThresholdMillis;
            maxSpansPerTrace = base.maxSpansPerTrace;
            rollingSizeMb = base.rollingSizeMb;
            warnOnSpanOutsideTrace = base.warnOnSpanOutsideTrace;
            metricPeriodMillis = base.metricPeriodMillis;
        }

        ImmutableCoreConfiguration build() {
            ImmutableCoreConfiguration configuration = new ImmutableCoreConfiguration();
            configuration.enabled = enabled;
            configuration.thresholdMillis = thresholdMillis;
            configuration.stuckThresholdMillis = stuckThresholdMillis;
            configuration.stackTraceInitialDelayMillis = stackTraceInitialDelayMillis;
            configuration.stackTracePeriodMillis = stackTracePeriodMillis;
            configuration.spanStackTraceThresholdMillis = spanStackTraceThresholdMillis;
            configuration.maxSpansPerTrace = maxSpansPerTrace;
            configuration.rollingSizeMb = rollingSizeMb;
            configuration.warnOnSpanOutsideTrace = warnOnSpanOutsideTrace;
            configuration.metricPeriodMillis = metricPeriodMillis;
            return configuration;
        }

        CoreConfigurationBuilder setEnabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        CoreConfigurationBuilder setThresholdMillis(int thresholdMillis) {
            this.thresholdMillis = thresholdMillis;
            return this;
        }

        CoreConfigurationBuilder setStuckThresholdMillis(int stuckThresholdMillis) {
            this.stuckThresholdMillis = stuckThresholdMillis;
            return this;
        }

        CoreConfigurationBuilder setStackTraceInitialDelayMillis(int stackTraceInitialDelayMillis) {
            this.stackTraceInitialDelayMillis = stackTraceInitialDelayMillis;
            return this;
        }

        CoreConfigurationBuilder setStackTracePeriodMillis(int stackTracePeriodMillis) {
            this.stackTracePeriodMillis = stackTracePeriodMillis;
            return this;
        }

        CoreConfigurationBuilder setSpanStackTraceThresholdMillis(int
                spanStackTraceThresholdMillis) {
            this.spanStackTraceThresholdMillis = spanStackTraceThresholdMillis;
            return this;
        }

        CoreConfigurationBuilder setMaxSpansPerTrace(int maxSpansPerTrace) {
            this.maxSpansPerTrace = maxSpansPerTrace;
            return this;
        }

        CoreConfigurationBuilder setRollingSizeMb(int rollingSizeMb) {
            this.rollingSizeMb = rollingSizeMb;
            return this;
        }

        CoreConfigurationBuilder setWarnOnSpanOutsideTrace(boolean warnOnSpanOutsideTrace) {
            this.warnOnSpanOutsideTrace = warnOnSpanOutsideTrace;
            return this;
        }

        CoreConfigurationBuilder setMetricPeriodMillis(int metricPeriodMillis) {
            this.metricPeriodMillis = metricPeriodMillis;
            return this;
        }
    }
}