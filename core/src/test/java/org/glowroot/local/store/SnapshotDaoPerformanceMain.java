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
package org.glowroot.local.store;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import com.google.common.base.Stopwatch;
import com.google.common.base.Ticker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.glowroot.markers.Static;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
@Static
public class SnapshotDaoPerformanceMain {

    private static final Logger logger = LoggerFactory
            .getLogger(SnapshotDaoPerformanceMain.class);

    private SnapshotDaoPerformanceMain() {}

    public static void main(String... args) throws Exception {
        SnapshotTestData snapshotTestData = new SnapshotTestData();
        DataSource dataSource = new DataSource();
        ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
        RollingFile rollingFile = new RollingFile(new File("glowroot.rolling.db"), 1000000,
                scheduledExecutor, Ticker.systemTicker());
        SnapshotDao snapshotDao = new SnapshotDao(dataSource, rollingFile);

        Stopwatch stopwatch = Stopwatch.createStarted();
        for (int i = 0; i < 1000; i++) {
            snapshotDao.store(snapshotTestData.createSnapshot());
        }
        logger.info("elapsed time: {}", stopwatch.elapsed(MILLISECONDS));
        logger.info("num traces: {}", snapshotDao.count());
    }
}
