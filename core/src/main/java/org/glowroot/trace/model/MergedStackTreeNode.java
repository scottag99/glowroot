/*
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
package org.glowroot.trace.model;

import java.lang.Thread.State;
import java.util.List;

import checkers.igj.quals.ReadOnly;
import checkers.nullness.quals.Nullable;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import dataflow.quals.Pure;

import org.glowroot.markers.ThreadSafe;

/**
 * Element of {@link MergedStackTree}.
 * 
 * @author Trask Stalnaker
 * @since 0.5
 */
@ThreadSafe
public class MergedStackTreeNode {

    @Nullable
    private final StackTraceElement stackTraceElement;
    // nodes mostly have a single child node, and rarely have more than two child nodes
    private final List<MergedStackTreeNode> childNodes = Lists.newArrayListWithCapacity(2);
    // using List over Set in order to preserve ordering
    @ReadOnly
    private List<String> metricNames;
    private int sampleCount;
    @Nullable
    private State leafThreadState;

    // this is for creating a single synthetic root node above other root nodes when there are
    // multiple root nodes
    @Nullable
    static MergedStackTreeNode createSyntheticRoot(List<MergedStackTreeNode> rootNodes) {
        if (rootNodes.isEmpty()) {
            return null;
        } else if (rootNodes.size() == 1) {
            return rootNodes.get(0);
        } else {
            int totalSampleCount = 0;
            for (MergedStackTreeNode rootNode : rootNodes) {
                totalSampleCount += rootNode.getSampleCount();
            }
            MergedStackTreeNode syntheticRootNode = new MergedStackTreeNode(null, null,
                    totalSampleCount);
            for (MergedStackTreeNode rootNode : rootNodes) {
                syntheticRootNode.addChildNode(rootNode);
            }
            return syntheticRootNode;
        }
    }

    static MergedStackTreeNode create(StackTraceElement stackTraceElement,
            @ReadOnly @Nullable List<String> metricNames) {
        return new MergedStackTreeNode(stackTraceElement, metricNames, 1);
    }

    private MergedStackTreeNode(@Nullable StackTraceElement stackTraceElement,
            @ReadOnly @Nullable List<String> metricNames, int sampleCount) {

        this.stackTraceElement = stackTraceElement;
        if (metricNames == null) {
            this.metricNames = Lists.newArrayList();
        } else {
            this.metricNames = Lists.newArrayList(metricNames);
        }
        this.sampleCount = sampleCount;
    }

    void addChildNode(MergedStackTreeNode methodTreeElement) {
        childNodes.add(methodTreeElement);
    }

    // may introduce contain duplicates
    void setMetricNames(@ReadOnly List<String> metricNames) {
        this.metricNames = metricNames;
    }

    void setLeafThreadState(State leafThreadState) {
        this.leafThreadState = leafThreadState;
    }

    // sampleCount is volatile to ensure visibility, but this method still needs to be called under
    // an appropriate lock so that two threads do not try to increment the count at the same time
    void incrementSampleCount() {
        sampleCount++;
    }

    @ReadOnly
    public List<MergedStackTreeNode> getChildNodes() {
        return childNodes;
    }

    @ReadOnly
    public List<String> getMetricNames() {
        return metricNames;
    }

    // only returns null for synthetic root
    @Nullable
    public StackTraceElement getStackTraceElement() {
        return stackTraceElement;
    }

    public int getSampleCount() {
        return sampleCount;
    }

    @Nullable
    public State getLeafThreadState() {
        return leafThreadState;
    }

    @Override
    @Pure
    public String toString() {
        return Objects.toStringHelper(this)
                .add("stackTraceElement", stackTraceElement)
                .add("childNodes", childNodes)
                .add("metricNames", metricNames)
                .add("sampleCount", sampleCount)
                .add("leafThreadState", leafThreadState)
                .toString();
    }
}
