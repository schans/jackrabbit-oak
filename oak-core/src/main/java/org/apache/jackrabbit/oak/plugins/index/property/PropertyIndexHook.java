/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.oak.plugins.index.property;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.plugins.index.IndexHook;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeStateDiff;

import com.google.common.collect.Maps;

/**
 * {@link IndexHook} implementation that is responsible for keeping the
 * {@link PropertyIndex} up to date
 * 
 * @see PropertyIndex
 * @see PropertyIndexLookup
 * 
 */
public class PropertyIndexHook implements IndexHook {

    private final NodeBuilder builder;

    private Map<String, List<PropertyIndexUpdate>> indexes;

    public PropertyIndexHook(NodeBuilder builder) {
        this.builder = builder;
    }


    // -----------------------------------------------------< IndexHook >--

    @Override @Nonnull
    public NodeStateDiff preProcess() {
        indexes = Maps.newHashMap();
        return new PropertyIndexDiff(builder, indexes);
    }

    @Override
    public void postProcess() throws CommitFailedException {
        for (List<PropertyIndexUpdate> updates : indexes.values()) {
            for (PropertyIndexUpdate update : updates) {
                update.apply();
            }
        }
    }

    @Override
    public void close() throws IOException {
        indexes = null;
    }
}