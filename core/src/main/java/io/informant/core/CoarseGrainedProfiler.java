/**
 * Copyright 2011-2013 the original author or authors.
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
package io.informant.core;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import io.informant.config.CoarseProfilingConfig;
import io.informant.config.ConfigService;
import io.informant.util.Singleton;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Ticker;

/**
 * Owns the thread (via a single threaded scheduled executor) that captures coarse-grained thread
 * dumps for traces that exceed the configured threshold.
 * 
 * @author Trask Stalnaker
 * @since 0.5
 */
@Singleton
class CoarseGrainedProfiler implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(CoarseGrainedProfiler.class);
    private static final int CHECK_INTERVAL_MILLIS = 50;

    private final ScheduledExecutorService scheduledExecutor;
    private final TraceRegistry traceRegistry;
    private final ConfigService configService;
    private final Ticker ticker;

    CoarseGrainedProfiler(ScheduledExecutorService scheduledExecutor, TraceRegistry traceRegistry,
            ConfigService configService, Ticker ticker) {
        this.scheduledExecutor = scheduledExecutor;
        this.traceRegistry = traceRegistry;
        this.configService = configService;
        this.ticker = ticker;
        // the main repeating Runnable (this) only runs every CHECK_INTERVAL_MILLIS at which time it
        // checks to see if there are any traces that may need stack traces scheduled before the
        // main repeating Runnable runs again (in another CHECK_INTERVAL_MILLIS).
        // the main repeating Runnable schedules a repeating CollectStackCommand for any trace that
        // may need a stack trace in the next CHECK_INTERVAL_MILLIS.
        // since the majority of traces never end up needing stack traces this is much more
        // efficient than scheduling a repeating CollectStackCommand for every trace (this was
        // learned the hard way).
        scheduledExecutor.scheduleAtFixedRate(this, 0, CHECK_INTERVAL_MILLIS, MILLISECONDS);
    }

    public void run() {
        try {
            runInternal();
        } catch (Error e) {
            // log and re-throw serious error which will terminate subsequent scheduled executions
            // (see ScheduledExecutorService.scheduleAtFixedRate())
            logger.error(e.getMessage(), e);
            throw e;
        } catch (Throwable t) {
            // log and terminate successfully
            logger.error(t.getMessage(), t);
        }
    }

    // look for traces that will exceed the stack trace initial delay threshold within the next
    // polling interval and schedule stack trace capture to occur at the appropriate time(s)
    private void runInternal() {
        // order configs by trace percentage so that lowest percentage configs have first shot
        long currentTick = ticker.read();
        CoarseProfilingConfig config = configService.getCoarseProfilingConfig();
        if (!config.isEnabled()) {
            return;
        }
        long stackTraceThresholdTime = currentTick
                - MILLISECONDS.toNanos(config.getInitialDelayMillis() - CHECK_INTERVAL_MILLIS);
        for (Trace trace : traceRegistry.getTraces()) {
            // if the trace will exceed the stack trace initial delay threshold before the next
            // scheduled execution of this repeating Runnable (in other words, it is within
            // COMMAND_INTERVAL_MILLIS from exceeding the threshold) and the stack trace capture
            // hasn't already been scheduled then schedule it
            if (!Nanoseconds.lessThan(trace.getStartTick(), stackTraceThresholdTime)) {
                // since the list of traces are "nearly" ordered by start time, if this trace didn't
                // meet the threshold then no subsequent trace will exceed the threshold (or at
                // least not by much given the "nearly" ordering in trace registry, which would at
                // worst lead to a trace having its profiling start a smidge later than desired)
                break;
            }
            if (trace.getCoarseProfilingScheduledFuture() == null) {
                scheduleProfiling(trace, currentTick, config);
            }
        }
    }

    // schedule stack traces to be taken every X seconds
    private void scheduleProfiling(Trace trace, long currentTick, CoarseProfilingConfig config) {
        long endTick = getEndTickForCommand(trace.getStartTick(), config);
        CollectStackCommand command = new CollectStackCommand(trace, endTick, false, ticker);
        long initialDelayRemainingMillis = getInitialDelayForCommand(trace.getStartTick(),
                currentTick, config);
        ScheduledFuture<?> scheduledFuture = scheduledExecutor.scheduleAtFixedRate(command,
                initialDelayRemainingMillis, config.getIntervalMillis(), MILLISECONDS);
        trace.setCoarseProfilingScheduledFuture(scheduledFuture);
    }

    private static long getEndTickForCommand(long startTick, CoarseProfilingConfig config) {
        // need to take max in case total is smaller than interval
        long durationMillis = Math.max(SECONDS.toMillis(config.getTotalSeconds())
                - config.getIntervalMillis() / 2, config.getIntervalMillis() / 2);
        // extra half interval is to make sure the final stack trace is grabbed if it aligns on
        // total (e.g. 1s initial delay, 1s interval, 10 second total should result in exactly 10
        // stack traces)
        return startTick + MILLISECONDS.toNanos(config.getInitialDelayMillis())
                + MILLISECONDS.toNanos(durationMillis);
    }

    private static long getInitialDelayForCommand(long startTick, long currentTick,
            CoarseProfilingConfig config) {
        long traceDurationMillis = NANOSECONDS.toMillis(currentTick - startTick);
        long initialDelayRemainingMillis = Math.max(0, config.getInitialDelayMillis()
                - traceDurationMillis);
        return initialDelayRemainingMillis;
    }
}