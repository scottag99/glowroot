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
package org.glowroot.weaving;

import java.io.IOException;
import java.util.List;

import com.google.common.collect.Sets;
import org.junit.Test;

import org.glowroot.weaving.preinit.GlobalCollector;
import org.glowroot.weaving.preinit.ReferencedMethod;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
public class PreInitializeClassesTest {

    @Test
    public void shouldCheckHardcodedListAgainstReality() throws IOException {
        // this test must be run against glowroot after shading/proguard
        GlobalCollector globalCollector = new GlobalCollector();
        // register WeavingMetricImpl since the WeavingClassFileTransformer constructor accepts the
        // WeavingMetricName interface and so WeavingMetricNameImpl would otherwise co unseen
        globalCollector.registerType("org/glowroot/trace/MetricTimerServiceImpl");
        // "call" WeavingClassFileTransformer constructor to capture types used by its weavers
        // LoadingCache (so these types will be in the list of possible subtypes later on)
        globalCollector.processMethodFailIfNotFound(ReferencedMethod.from(
                "org/glowroot/weaving/WeavingClassFileTransformer", "<init>",
                "(Ljava/util/List;Ljava/util/List;Lcom/google/common/base/Supplier;"
                        + "Lorg/glowroot/weaving/ParsedTypeCache;"
                        + "Lorg/glowroot/weaving/MetricTimerService;Z)V"));
        // "call" WeavingClassFileTransformer.transform()
        globalCollector.processMethodFailIfNotFound(ReferencedMethod.from(
                "org/glowroot/weaving/WeavingClassFileTransformer", "transform",
                "(Ljava/lang/ClassLoader;Ljava/lang/String;Ljava/lang/Class;"
                        + "Ljava/security/ProtectionDomain;[B)[B"));
        // "call" DataSource$ShutdownHookThread.run() and RollingFile$ShutdownHookThread.run()
        // because class loading during jvm shutdown throws exception
        globalCollector.processMethodFailIfNotFound(ReferencedMethod.from(
                "org/glowroot/local/store/DataSource$ShutdownHookThread", "run", "()V"));
        globalCollector.processMethodFailIfNotFound(ReferencedMethod.from(
                "org/glowroot/local/store/RollingFile$ShutdownHookThread", "run", "()V"));
        globalCollector.processOverrides();
        // these assertions just help for debugging, since it can be hard to see the differences in
        // the very large lists below in the "real" assertion
        List<String> globalCollectorUsedTypes = globalCollector.usedTypes();
        globalCollectorUsedTypes.removeAll(PreInitializeClasses.maybeUsedTypes());
        assertThat(Sets.difference(Sets.newHashSet(globalCollectorUsedTypes),
                Sets.newHashSet(PreInitializeClasses.usedTypes()))).isEmpty();
        assertThat(Sets.difference(Sets.newHashSet(PreInitializeClasses.usedTypes()),
                Sets.newHashSet(globalCollectorUsedTypes))).isEmpty();

        // this is the real assertion
        assertThat(PreInitializeClasses.usedTypes()).isEqualTo(globalCollectorUsedTypes);
    }
}
