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
package io.informant.local.trace;

import static org.fest.assertions.api.Assertions.assertThat;
import io.informant.core.util.DataSource;
import io.informant.core.util.MockClock;
import io.informant.core.util.RollingFile;
import io.informant.core.util.Threads;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
public class TraceSnapshotDaoTest {

    private Collection<Thread> preExistingThreads;
    private DataSource dataSource;
    private File rollingDbFile;
    private RollingFile rollingFile;
    private TraceSnapshotDao snapshotDao;

    @Before
    public void before() throws SQLException, IOException {
        preExistingThreads = Threads.currentThreads();
        dataSource = new DataSource();
        if (dataSource.tableExists("trace_snapshot")) {
            dataSource.execute("drop table trace_snapshot");
        }
        rollingDbFile = new File("informant.rolling.db");
        rollingFile = new RollingFile(rollingDbFile, 1000000);
        snapshotDao = new TraceSnapshotDao(dataSource, rollingFile, new MockClock());
    }

    @After
    public void after() throws Exception {
        Threads.preShutdownCheck(preExistingThreads);
        dataSource.close();
        rollingFile.close();
        rollingDbFile.delete();
        Threads.postShutdownCheck(preExistingThreads);
    }

    @Test
    public void shouldReadSnapshot() {
        // given
        TraceSnapshot snapshot = new TraceSnapshotTestData().createSnapshot();
        snapshotDao.storeSnapshot(snapshot);
        // when
        List<TraceSnapshotPoint> points = snapshotDao.readPoints(0, 0, 0, Long.MAX_VALUE, false,
                false, false, null, null, 1);
        TraceSnapshot snapshot2 = snapshotDao.readSnapshot(points.get(0).getId());
        // then
        assertThat(snapshot2.getStartAt()).isEqualTo(snapshot.getStartAt());
        assertThat(snapshot2.isStuck()).isEqualTo(snapshot.isStuck());
        assertThat(snapshot2.getId()).isEqualTo(snapshot.getId());
        assertThat(snapshot2.getDuration()).isEqualTo(snapshot.getDuration());
        assertThat(snapshot2.isCompleted()).isEqualTo(snapshot.isCompleted());
        assertThat(snapshot2.getDescription()).isEqualTo("test description");
        assertThat(snapshot2.getUserId()).isEqualTo(snapshot.getUserId());
        // TODO verify metricData, trace and mergedStackTree
    }

    @Test
    public void shouldReadSnapshotWithDurationQualifier() {
        // given
        TraceSnapshot snapshot = new TraceSnapshotTestData().createSnapshot();
        snapshotDao.storeSnapshot(snapshot);
        // when
        List<TraceSnapshotPoint> points = snapshotDao.readPoints(0, 0, snapshot.getDuration(),
                snapshot.getDuration(), false, false, false, null, null, 1);
        // then
        assertThat(points).hasSize(1);
    }

    @Test
    public void shouldNotReadSnapshotWithHighDurationQualifier() {
        // given
        TraceSnapshot snapshot = new TraceSnapshotTestData().createSnapshot();
        snapshotDao.storeSnapshot(snapshot);
        // when
        List<TraceSnapshotPoint> points = snapshotDao.readPoints(0, 0, snapshot.getDuration() + 1,
                snapshot.getDuration() + 2, false, false, false, null,
                null, 1);
        // then
        assertThat(points).isEmpty();
    }

    @Test
    public void shouldNotReadSnapshotWithLowDurationQualifier() {
        // given
        TraceSnapshot snapshot = new TraceSnapshotTestData().createSnapshot();
        snapshotDao.storeSnapshot(snapshot);
        // when
        List<TraceSnapshotPoint> points = snapshotDao.readPoints(0, 0, snapshot.getDuration() - 2,
                snapshot.getDuration() - 1, false, false, false, null, null, 1);
        // then
        assertThat(points).isEmpty();
    }

    @Test
    public void shouldDeletedTrace() {
        // given
        snapshotDao.storeSnapshot(new TraceSnapshotTestData().createSnapshot());
        // when
        snapshotDao.deleteSnapshotsBefore(0);
        // then
        assertThat(snapshotDao.count()).isEqualTo(0);
    }
}