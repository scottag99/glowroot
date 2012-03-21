/**
 * Copyright 2012 the original author or authors.
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
package org.informantproject.test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.List;
import java.util.Random;

import org.informantproject.api.Optional;
import org.informantproject.test.api.LevelOne;
import org.informantproject.testkit.AppUnderTest;
import org.informantproject.testkit.Configuration.PluginConfiguration;
import org.informantproject.testkit.InformantContainer;
import org.informantproject.testkit.Trace;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
public class PluginPropertyTest {

    private static final String PLUGIN_ID = "org.informantproject:informant-integration-tests";

    private static final Random random = new Random();

    private static InformantContainer container;

    @BeforeClass
    public static void setUp() throws Exception {
        container = InformantContainer.create();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        container.shutdown();
    }

    @Test
    public void shouldUpdateAndReadBackPluginConfiguration() throws Exception {
        // given
        String randomText = "Level " + random.nextLong();
        boolean randomBoolean = random.nextBoolean();
        PluginConfiguration randomPluginConfiguration = container.getInformant()
                .getPluginConfiguration(PLUGIN_ID);
        randomPluginConfiguration.setProperty("alternateDescription", randomText);
        randomPluginConfiguration.setProperty("starredDescription", randomBoolean);
        container.getInformant().storePluginConfiguration(PLUGIN_ID, randomPluginConfiguration);
        // when
        PluginConfiguration pluginConfiguration = container.getInformant().getPluginConfiguration(
                PLUGIN_ID);
        // then
        assertThat((String) pluginConfiguration.getProperty("alternateDescription").get(), is(
                randomText));
        assertThat((Boolean) pluginConfiguration.getProperty("starredDescription").get(), is(
                randomBoolean));
    }

    @Test
    public void shouldReadDefaultPropertyValue() throws Exception {
        // when
        PluginConfiguration pluginConfiguration = container.getInformant().getPluginConfiguration(
                PLUGIN_ID);
        // then
        assertThat((String) pluginConfiguration.getProperty("hasDefaultVal").get(), is("one"));
    }

    @Test
    public void shouldClearPluginProperty() throws Exception {
        // given
        PluginConfiguration pluginConfiguration = container.getInformant().getPluginConfiguration(
                PLUGIN_ID);
        pluginConfiguration.setProperty("alternateDescription", "a non-null value");
        container.getInformant().storePluginConfiguration(PLUGIN_ID, pluginConfiguration);
        // when
        pluginConfiguration = container.getInformant().getPluginConfiguration(PLUGIN_ID);
        pluginConfiguration.setProperty("alternateDescription", null);
        container.getInformant().storePluginConfiguration(PLUGIN_ID, pluginConfiguration);
        // then
        pluginConfiguration = container.getInformant().getPluginConfiguration(PLUGIN_ID);
        assertThat(pluginConfiguration.getProperty("alternateDescription"), is(Optional.absent()));
    }

    @Test
    public void shouldReadAlternateDescription() throws Exception {
        // given
        container.getInformant().setThresholdMillis(0);
        PluginConfiguration pluginConfiguration = container.getInformant().getPluginConfiguration(
                "org.informantproject:informant-integration-tests");
        pluginConfiguration.setProperty("alternateDescription", "Level 1");
        pluginConfiguration.setProperty("starredDescription", false);
        container.getInformant().storePluginConfiguration(
                "org.informantproject:informant-integration-tests", pluginConfiguration);
        // when
        container.executeAppUnderTest(SimpleApp.class);
        // then
        List<Trace> traces = container.getInformant().getAllTraces();
        assertThat(traces.size(), is(1));
        assertThat(traces.get(0).getDescription(), is("Level 1"));
    }

    @Test
    public void shouldReadStarredDescription() throws Exception {
        // given
        container.getInformant().setThresholdMillis(0);
        PluginConfiguration pluginConfiguration = container.getInformant().getPluginConfiguration(
                "org.informantproject:informant-integration-tests");
        pluginConfiguration.setProperty("alternateDescription", null);
        pluginConfiguration.setProperty("starredDescription", true);
        container.getInformant().storePluginConfiguration(
                "org.informantproject:informant-integration-tests", pluginConfiguration);
        // when
        container.executeAppUnderTest(SimpleApp.class);
        // then
        List<Trace> traces = container.getInformant().getAllTraces();
        assertThat(traces.size(), is(1));
        assertThat(traces.get(0).getDescription(), is("Level One*"));
    }

    public static class SimpleApp implements AppUnderTest {
        public void executeApp() throws InterruptedException {
            new LevelOne().call("a", "b");
        }
    }
}