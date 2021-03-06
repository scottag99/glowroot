/*
 * Copyright 2011-2014 the original author or authors.
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
package org.glowroot.container.local;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import checkers.igj.quals.ReadOnly;
import checkers.nullness.quals.Nullable;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Stopwatch;
import com.google.common.base.Ticker;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import org.glowroot.GlowrootModule;
import org.glowroot.collector.Snapshot;
import org.glowroot.collector.SnapshotCreator;
import org.glowroot.collector.SnapshotWriter;
import org.glowroot.collector.TraceCollectorImpl;
import org.glowroot.container.common.ObjectMappers;
import org.glowroot.container.trace.Trace;
import org.glowroot.container.trace.TraceService;
import org.glowroot.local.store.SnapshotDao;
import org.glowroot.local.ui.TraceExportHttpService;
import org.glowroot.markers.ThreadSafe;
import org.glowroot.trace.TraceRegistry;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
// even though this is thread safe, it is not useful for running tests in parallel since
// getLastTrace() and others are not scoped to a particular test
@ThreadSafe
class LocalTraceService extends TraceService {

    @ReadOnly
    private static final ObjectMapper mapper = ObjectMappers.create();

    private final SnapshotDao snapshotDao;
    private final TraceExportHttpService traceExportHttpService;
    private final TraceCollectorImpl traceCollector;
    private final TraceRegistry traceRegistry;
    private final Ticker ticker;

    LocalTraceService(GlowrootModule glowrootModule) {
        snapshotDao = glowrootModule.getStorageModule().getSnapshotDao();
        traceExportHttpService = glowrootModule.getUiModule().getTraceExportHttpService();
        traceCollector = glowrootModule.getCollectorModule().getTraceCollector();
        traceRegistry = glowrootModule.getTraceModule().getTraceRegistry();
        // can't use ticker from Glowroot since it is shaded when run in mvn and unshaded in ide
        ticker = Ticker.systemTicker();
    }

    @Override
    public int getNumPendingCompleteTraces() {
        return traceCollector.getPendingCompleteTraces().size();
    }

    @Override
    public long getNumStoredSnapshots() {
        return snapshotDao.count();
    }

    @Override
    public InputStream getTraceExport(String id) throws Exception {
        return new ByteArrayInputStream(traceExportHttpService.getExportBytes(id));
    }

    void assertNoActiveTraces() throws Exception {
        Stopwatch stopwatch = Stopwatch.createStarted();
        // if interruptAppUnderTest() was used to terminate an active trace, it may take a few
        // milliseconds to interrupt the thread and end the active trace
        while (stopwatch.elapsed(SECONDS) < 2) {
            int numActiveTraces = Iterables.size(traceRegistry.getTraces());
            if (numActiveTraces == 0) {
                return;
            }
        }
        throw new AssertionError("There are still active traces");
    }

    void deleteAllSnapshots() {
        snapshotDao.deleteAllSnapshots();
    }

    @Override
    @Nullable
    protected Trace getLastTrace(boolean summary) throws Exception {
        Snapshot snapshot = snapshotDao.getLastSnapshot(summary);
        if (snapshot == null) {
            return null;
        }
        return ObjectMappers.readRequiredValue(mapper,
                SnapshotWriter.toString(snapshot, summary), Trace.class);
    }

    @Override
    @Nullable
    protected Trace getActiveTrace(boolean summary) throws Exception {
        List<org.glowroot.trace.model.Trace> traces = Lists.newArrayList(traceRegistry.getTraces());
        if (traces.isEmpty()) {
            return null;
        } else if (traces.size() > 1) {
            throw new IllegalStateException("Unexpected number of active traces");
        } else {
            Snapshot snapshot = SnapshotCreator.createActiveSnapshot(traces.get(0),
                    traces.get(0).getEndTick(), ticker.read(), summary);
            return ObjectMappers.readRequiredValue(mapper,
                    SnapshotWriter.toString(snapshot, summary), Trace.class);
        }
    }
}
