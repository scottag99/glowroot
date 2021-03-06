/*
 * Copyright 2012-2014 the original author or authors.
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
import java.sql.Statement;
import java.util.Properties;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import org.h2.jdbc.JdbcConnection;
import org.junit.Test;

import org.glowroot.local.store.Schemas.Index;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
public class SchemasTest {

    @Test
    public void shouldReadIndexes() throws Exception {
        // given
        Connection connection = new JdbcConnection("jdbc:h2:mem:", new Properties());
        Statement statement = connection.createStatement();
        statement.execute("create table tab (a varchar, b bigint)");
        statement.execute("create index tab_idx on tab (a)");
        // when
        Set<Index> indexes = Schemas.getIndexes("tab", connection);
        assertThat(indexes).hasSize(1);
        assertThat(indexes.iterator().next())
                .isEqualTo(new Index("tab_idx", ImmutableList.of("a")));
        // then
    }

    // Schemas.syncIndexes("tab", indexes, connection);
}
