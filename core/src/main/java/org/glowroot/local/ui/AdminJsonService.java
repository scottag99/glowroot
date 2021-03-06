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
package org.glowroot.local.ui;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import checkers.nullness.quals.Nullable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.glowroot.collector.TraceCollectorImpl;
import org.glowroot.config.ConfigService;
import org.glowroot.config.PointcutConfig;
import org.glowroot.local.store.DataSource;
import org.glowroot.local.store.SnapshotDao;
import org.glowroot.markers.OnlyUsedByTests;
import org.glowroot.markers.Singleton;
import org.glowroot.trace.PointcutConfigAdviceCache;
import org.glowroot.trace.TraceRegistry;
import org.glowroot.weaving.ParsedTypeCache;

/**
 * Json service for various admin tasks, bound to /backend/admin.
 * 
 * @author Trask Stalnaker
 * @since 0.5
 */
@Singleton
@JsonService
class AdminJsonService {

    private static final Logger logger = LoggerFactory.getLogger(AdminJsonService.class);

    private final SnapshotDao snapshotDao;
    private final ConfigService configService;
    private final PointcutConfigAdviceCache pointcutConfigAdviceCache;
    private final ParsedTypeCache parsedTypeCache;
    @Nullable
    private final Instrumentation instrumentation;
    private final TraceCollectorImpl traceCollector;
    private final DataSource dataSource;
    private final TraceRegistry traceRegistry;

    AdminJsonService(SnapshotDao snapshotDao, ConfigService configService,
            PointcutConfigAdviceCache pointcutConfigAdviceCache, ParsedTypeCache parsedTypeCache,
            @Nullable Instrumentation instrumentation, TraceCollectorImpl traceCollector,
            DataSource dataSource, TraceRegistry traceRegistry) {
        this.snapshotDao = snapshotDao;
        this.configService = configService;
        this.pointcutConfigAdviceCache = pointcutConfigAdviceCache;
        this.parsedTypeCache = parsedTypeCache;
        this.instrumentation = instrumentation;
        this.traceCollector = traceCollector;
        this.dataSource = dataSource;
        this.traceRegistry = traceRegistry;
    }

    @POST("/backend/admin/data/delete-all")
    void deleteAllData() {
        logger.debug("deleteAllData()");
        snapshotDao.deleteAllSnapshots();
    }

    @POST("/backend/admin/pointcuts/reweave")
    void reweavePointcutConfigs() throws UnmodifiableClassException {
        if (instrumentation == null) {
            logger.warn("retransformClasses does not work under IsolatedWeavingClassLoader");
            return;
        }
        if (!instrumentation.isRetransformClassesSupported()) {
            logger.warn("retransformClasses is not supported");
            return;
        }
        List<PointcutConfig> pointcutConfigs = configService.getPointcutConfigs();
        pointcutConfigAdviceCache.updateAdvisors(pointcutConfigs);
        Set<String> typeNames = Sets.newHashSet();
        for (PointcutConfig pointcutConfig : pointcutConfigs) {
            typeNames.add(pointcutConfig.getTypeName());
        }
        List<Class<?>> classes = Lists.newArrayList();
        classes.addAll(parsedTypeCache.getClassesWithReweavableAdvice());
        classes.addAll(parsedTypeCache.getExistingSubClasses(typeNames));
        if (classes.isEmpty()) {
            return;
        }
        instrumentation.retransformClasses(Iterables.toArray(classes, Class.class));
    }

    @POST("/backend/admin/data/compact")
    void compactData() {
        logger.debug("compactData()");
        try {
            dataSource.compact();
        } catch (SQLException e) {
            // this might be serious, worth logging as error
            logger.error(e.getMessage(), e);
        }
    }

    @OnlyUsedByTests
    @POST("/backend/admin/config/reset-all")
    void resetAllConfig() throws IOException {
        logger.debug("resetAllConfig()");
        configService.resetAllConfig();
    }

    @OnlyUsedByTests
    @GET("/backend/admin/num-pending-complete-traces")
    String getNumPendingCompleteTraces() {
        logger.debug("getNumPendingCompleteTraces()");
        return Integer.toString(traceCollector.getPendingCompleteTraces().size());
    }

    @OnlyUsedByTests
    @GET("/backend/admin/num-stored-snapshots")
    String getNumStoredSnapshots() {
        logger.debug("getNumStoredSnapshots()");
        return Long.toString(snapshotDao.count());
    }

    @OnlyUsedByTests
    @GET("/backend/admin/num-active-traces")
    String getNumActiveTraces() {
        logger.debug("getNumActiveTraces()");
        return Integer.toString(Iterables.size(traceRegistry.getTraces()));
    }
}
