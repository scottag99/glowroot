/*
 * Copyright 2012-2013 the original author or authors.
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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import checkers.igj.quals.Immutable;
import checkers.igj.quals.ReadOnly;
import checkers.nullness.quals.Nullable;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import dataflow.quals.Pure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.glowroot.markers.Static;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
@Static
class Schemas {

    private static final Logger logger = LoggerFactory.getLogger(Schemas.class);

    private static final Map<Integer, String> sqlTypeNames = Maps.newHashMap();

    static {
        sqlTypeNames.put(Types.VARCHAR, "varchar");
        sqlTypeNames.put(Types.BIGINT, "bigint");
        sqlTypeNames.put(Types.BOOLEAN, "boolean");
        sqlTypeNames.put(Types.CLOB, "clob");
        sqlTypeNames.put(Types.DOUBLE, "double");
    }

    private Schemas() {}

    static void syncTable(String tableName, @ReadOnly List<Column> columns,
            Connection connection) throws SQLException {

        if (!tableExists(tableName, connection)) {
            createTable(tableName, columns, connection);
        } else if (tableNeedsUpgrade(tableName, columns, connection)) {
            logger.warn("upgrading table {}, which unfortunately at this point just means"
                    + " dropping and re-create the table (losing existing data)", tableName);
            execute("drop table " + tableName, connection);
            createTable(tableName, columns, connection);
        }
    }

    static void syncIndexes(String tableName, @ReadOnly List<Index> indexes, Connection connection)
            throws SQLException {

        ImmutableSet<Index> desiredIndexes = ImmutableSet.copyOf(indexes);
        Set<Index> existingIndexes = getIndexes(tableName, connection);
        for (Index index : Sets.difference(existingIndexes, desiredIndexes)) {
            execute("drop index " + index.getName(), connection);
        }
        for (Index index : Sets.difference(desiredIndexes, existingIndexes)) {
            createIndex(tableName, index, connection);
        }
        // test the logic
        existingIndexes = getIndexes(tableName, connection);
        if (!existingIndexes.equals(desiredIndexes)) {
            logger.error("the logic in syncIndexes() needs fixing");
        }
    }

    static boolean tableExists(String tableName, Connection connection) throws SQLException {
        logger.debug("tableExists(): tableName={}", tableName);
        ResultSet resultSet = connection.getMetaData().getTables(null, null,
                tableName.toUpperCase(Locale.ENGLISH), null);
        try {
            return resultSet.next();
        } finally {
            resultSet.close();
        }
    }

    static ImmutableList<Column> getColumns(String tableName, Connection connection)
            throws SQLException {
        ImmutableList.Builder<Column> columns = ImmutableList.builder();
        ResultSet resultSet = connection.getMetaData().getColumns(null, null,
                tableName.toUpperCase(Locale.ENGLISH), null);
        try {
            while (resultSet.next()) {
                String columnName = resultSet.getString("COLUMN_NAME").toLowerCase(Locale.ENGLISH);
                int columnType = resultSet.getInt("DATA_TYPE");
                columns.add(new Column(columnName, columnType));
            }
        } finally {
            resultSet.close();
        }
        return columns.build();
    }

    private static void createTable(String tableName, @ReadOnly List<Column> columns,
            Connection connection) throws SQLException {

        StringBuilder sql = new StringBuilder();
        sql.append("create table ");
        sql.append(tableName);
        sql.append(" (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            String sqlTypeName = sqlTypeNames.get(columns.get(i).getType());
            if (sqlTypeName == null) {
                throw new SQLException("Unexpected sql type '" + columns.get(i).getType());
            }
            sql.append(columns.get(i).getName());
            sql.append(" ");
            sql.append(sqlTypeName);
            if (columns.get(i) instanceof PrimaryKeyColumn) {
                sql.append(" primary key");
            }
        }
        sql.append(")");
        execute(sql.toString(), connection);
        if (tableNeedsUpgrade(tableName, columns, connection)) {
            logger.warn("table {} thinks it still needs to be upgraded, even after it was just"
                    + "upgraded", tableName);
        }
    }

    private static boolean tableNeedsUpgrade(String tableName, @ReadOnly List<Column> columns,
            Connection connection) throws SQLException {

        if (primaryKeyNeedsUpgrade(tableName, Iterables.filter(columns, PrimaryKeyColumn.class),
                connection)) {
            return true;
        }
        ResultSet resultSet = connection.getMetaData().getColumns(null, null,
                tableName.toUpperCase(Locale.ENGLISH), null);
        try {
            for (Column column : columns) {
                if (!resultSet.next()
                        || !column.getName().equalsIgnoreCase(resultSet.getString("COLUMN_NAME"))
                        || column.getType() != resultSet.getInt("DATA_TYPE")) {
                    return true;
                }
            }
            // don't check resultSet.next(), ok to have extra columns
            // (e.g. for some kind of temporary debugging purpose)
            return false;
        } finally {
            resultSet.close();
        }
    }

    private static boolean primaryKeyNeedsUpgrade(String tableName,
            @ReadOnly Iterable<PrimaryKeyColumn> primaryKeyColumns, Connection connection)
            throws SQLException {

        ResultSet resultSet = connection.getMetaData().getPrimaryKeys(null, null,
                tableName.toUpperCase(Locale.ENGLISH));
        try {
            for (PrimaryKeyColumn primaryKeyColumn : primaryKeyColumns) {
                if (!resultSet.next() || !primaryKeyColumn.getName()
                        .equalsIgnoreCase(resultSet.getString("COLUMN_NAME"))) {
                    return true;
                }
            }
            // not ok to have extra columns on primary key
            return resultSet.next();
        } finally {
            resultSet.close();
        }
    }

    @VisibleForTesting
    static ImmutableSet<Index> getIndexes(String tableName, Connection connection)
            throws SQLException {

        ListMultimap<String, String> indexColumns = ArrayListMultimap.create();
        ResultSet resultSet = connection.getMetaData().getIndexInfo(null, null,
                tableName.toUpperCase(Locale.ENGLISH), false, false);
        try {
            while (resultSet.next()) {
                String indexName = resultSet.getString("INDEX_NAME");
                String columnName = resultSet.getString("COLUMN_NAME");
                // hack-ish to skip over primary key constraints which seem to be always
                // prefixed in H2 by PRIMARY_KEY_
                if (!indexName.startsWith("PRIMARY_KEY_")) {
                    indexColumns.put(indexName, columnName);
                }
            }
        } finally {
            resultSet.close();
        }
        ImmutableSet.Builder<Index> indexes = ImmutableSet.builder();
        for (Entry<String, Collection<String>> entry : indexColumns.asMap().entrySet()) {
            String name = entry.getKey();
            indexes.add(new Index(name, entry.getValue()));

        }
        return indexes.build();
    }

    private static void createIndex(String tableName, Index index, Connection connection)
            throws SQLException {

        StringBuilder sql = new StringBuilder();
        sql.append("create index ");
        sql.append(index.getName());
        sql.append(" on ");
        sql.append(tableName);
        sql.append(" (");
        for (int i = 0; i < index.getColumns().size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(index.getColumns().get(i));
        }
        sql.append(")");
        execute(sql.toString(), connection);
    }

    private static void execute(String sql, Connection connection) throws SQLException {
        Statement statement = connection.createStatement();
        try {
            statement.execute(sql);
        } finally {
            statement.close();
        }
    }

    @Immutable
    static class Column {
        private final String name;
        private final int type;

        Column(String name, int type) {
            this.name = name;
            this.type = type;
        }
        String getName() {
            return name;
        }
        private int getType() {
            return type;
        }
    }

    @Immutable
    static class PrimaryKeyColumn extends Column {
        PrimaryKeyColumn(String name, int type) {
            super(name, type);
        }
    }

    @Immutable
    static class Index {
        // nameUpper and columnsUpper are used to make equals/hashCode case insensitive
        private final String name;
        private final String nameUpper;
        private final ImmutableList<String> columns;
        private final ImmutableList<String> columnsUpper;
        Index(String name, @ReadOnly Iterable<String> columns) {
            this.name = name;
            this.nameUpper = name.toUpperCase(Locale.ENGLISH);
            this.columns = ImmutableList.copyOf(columns);
            ImmutableList.Builder<String> upperBuilder = ImmutableList.builder();
            for (String column : columns) {
                upperBuilder.add(column.toUpperCase(Locale.ENGLISH));
            }
            this.columnsUpper = upperBuilder.build();
        }
        private String getName() {
            return name;
        }
        private ImmutableList<String> getColumns() {
            return columns;
        }
        // equals/hashCode are used in Schema.syncIndexes() to diff list of indexes with list of
        // existing indexes
        @Override
        @Pure
        public boolean equals(@Nullable Object obj) {
            if (obj instanceof Index) {
                Index that = (Index) obj;
                return nameUpper.equalsIgnoreCase(that.nameUpper)
                        && Objects.equal(columnsUpper, that.columnsUpper);
            }
            return false;
        }
        @Override
        @Pure
        public int hashCode() {
            return Objects.hashCode(nameUpper, columnsUpper);
        }
    }
}
