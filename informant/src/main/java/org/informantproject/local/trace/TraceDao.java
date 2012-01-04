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
package org.informantproject.local.trace;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.informantproject.util.Clock;
import org.informantproject.util.JdbcUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Data access object for storing and reading trace data from the embedded H2 database.
 * 
 * @author Trask Stalnaker
 * @since 0.5
 */
@Singleton
public class TraceDao {

    private static final Logger logger = LoggerFactory.getLogger(TraceDao.class);

    private final Connection connection;
    private final Clock clock;

    private final PreparedStatement insertPreparedStatement;
    private final PreparedStatement selectPreparedStatement;
    private final PreparedStatement deletePreparedStatement;
    private final PreparedStatement countPreparedStatement;

    private final boolean valid;

    @Inject
    TraceDao(Connection connection, Clock clock) {
        this.connection = connection;
        this.clock = clock;
        PreparedStatement insertPS = null;
        PreparedStatement selectPS = null;
        PreparedStatement deletePS = null;
        PreparedStatement countPS = null;
        boolean errorOnInit = false;
        try {
            if (!JdbcUtil.tableExists("trace", connection)) {
                createTable(connection);
            }
            insertPS = connection.prepareStatement("insert into trace (id, capturedAt, startAt,"
                    + " stuck, duration, completed, threadNames, username, spans, mergedStackTree)"
                    + " values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            selectPS = connection.prepareStatement("select id, capturedAt, startAt, stuck,"
                    + " duration, completed, threadNames, username, spans, mergedStackTree"
                    + " from trace where capturedAt >= ? and capturedAt <= ?");
            deletePS = connection.prepareStatement("delete from trace where id = ?");
            countPS = connection.prepareStatement("select count(*) from trace");
        } catch (SQLException e) {
            errorOnInit = true;
            logger.error(e.getMessage(), e);
        }
        insertPreparedStatement = insertPS;
        selectPreparedStatement = selectPS;
        deletePreparedStatement = deletePS;
        countPreparedStatement = countPS;
        this.valid = !errorOnInit;
    }

    void storeTrace(StoredTrace storedTrace) {
        logger.debug("storeTrace(): storedTrace={}", storedTrace);
        if (!valid) {
            return;
        }
        synchronized (connection) {
            try {
                int index = 1;
                insertPreparedStatement.setString(index++, storedTrace.getId());
                insertPreparedStatement.setLong(index++, clock.currentTimeMillis());
                insertPreparedStatement.setLong(index++, storedTrace.getStartAt());
                insertPreparedStatement.setBoolean(index++, storedTrace.isStuck());
                insertPreparedStatement.setLong(index++, storedTrace.getDuration());
                insertPreparedStatement.setBoolean(index++, storedTrace.isCompleted());
                insertPreparedStatement.setString(index++, storedTrace.getThreadNames());
                insertPreparedStatement.setString(index++, storedTrace.getUsername());
                insertPreparedStatement.setString(index++, storedTrace.getSpans());
                insertPreparedStatement.setString(index++, storedTrace.getMergedStackTree());
                // TODO write metric data
                insertPreparedStatement.executeUpdate();
                // don't close prepared statement
            } catch (SQLException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

    public List<StoredTrace> readStoredTraces(long capturedFrom, long capturedTo) {
        if (!valid) {
            return Collections.emptyList();
        }
        synchronized (connection) {
            try {
                selectPreparedStatement.setLong(1, capturedFrom);
                selectPreparedStatement.setLong(2, capturedTo);
                ResultSet resultSet = selectPreparedStatement.executeQuery();
                try {
                    List<StoredTrace> traces = new ArrayList<StoredTrace>();
                    while (resultSet.next()) {
                        traces.add(buildStoredTraceFromResultSet(resultSet));
                    }
                    return traces;
                } finally {
                    resultSet.close();
                }
            } catch (SQLException e) {
                logger.error(e.getMessage(), e);
                return Collections.emptyList();
            }
        }
    }

    void delete(String id) {
        logger.debug("delete(): id={}", id);
        if (!valid) {
            return;
        }
        synchronized (connection) {
            try {
                deletePreparedStatement.setString(1, id);
                int rowCount = deletePreparedStatement.executeUpdate();
                if (rowCount != 1) {
                    logger.error("unexpected delete row count '" + rowCount + "'",
                            new IllegalStateException());
                }
            } catch (SQLException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

    long count() {
        if (!valid) {
            return 0;
        }
        synchronized (connection) {
            try {
                ResultSet resultSet = countPreparedStatement.executeQuery();
                try {
                    resultSet.next();
                    return resultSet.getLong(1);
                } finally {
                    resultSet.close();
                }
            } catch (SQLException e) {
                logger.error(e.getMessage(), e);
                return 0;
            }
        }
    }

    private static void createTable(Connection connection) throws SQLException {
        Statement statement = connection.createStatement();
        try {
            statement.execute("create table trace (id varchar, capturedAt bigint, startAt bigint,"
                    + " stuck boolean, duration bigint, completed boolean, threadnames varchar,"
                    + " username varchar, spans varchar, mergedStackTree varchar)");
        } finally {
            statement.close();
        }
    }

    private static StoredTrace buildStoredTraceFromResultSet(ResultSet resultSet)
            throws SQLException {

        StoredTrace storedTrace = new StoredTrace();
        int columnIndex = 1;
        storedTrace.setId(resultSet.getString(columnIndex++));
        columnIndex++; // TODO place holder for capturedAt
        storedTrace.setStartAt(resultSet.getLong(columnIndex++));
        storedTrace.setStuck(resultSet.getBoolean(columnIndex++));
        storedTrace.setDuration(resultSet.getLong(columnIndex++));
        storedTrace.setCompleted(resultSet.getBoolean(columnIndex++));
        storedTrace.setThreadNames(resultSet.getString(columnIndex++));
        storedTrace.setUsername(resultSet.getString(columnIndex++));
        storedTrace.setSpans(resultSet.getString(columnIndex++));
        storedTrace.setMergedStackTree(resultSet.getString(columnIndex++));
        return storedTrace;
    }
}