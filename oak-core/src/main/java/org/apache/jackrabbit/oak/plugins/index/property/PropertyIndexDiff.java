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

import static org.apache.jackrabbit.JcrConstants.JCR_PRIMARYTYPE;
import static org.apache.jackrabbit.oak.plugins.index.IndexConstants.INDEX_DEFINITIONS_NAME;
import static org.apache.jackrabbit.oak.plugins.index.IndexConstants.INDEX_DEFINITIONS_NODE_TYPE;
import static org.apache.jackrabbit.oak.commons.PathUtils.concat;

import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.plugins.memory.MemoryNodeState;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.jackrabbit.oak.spi.state.NodeStateDiff;
import org.apache.jackrabbit.oak.spi.state.NodeStateUtils;

/**
 * {@link NodeStateDiff} implementation that extracts changes for the
 * {@link PropertyIndexHook} to be applied on the {@link PropertyIndex}
 * 
 * @see PropertyIndexHook
 * 
 */
class PropertyIndexDiff implements NodeStateDiff {

    private final PropertyIndexDiff parent;

    private final NodeBuilder node;

    private final String name;

    private String path;

    private final Map<String, List<PropertyIndexUpdate>> updates;

    private PropertyIndexDiff(
            PropertyIndexDiff parent,
            NodeBuilder node, String name, String path,
            Map<String, List<PropertyIndexUpdate>> updates) {
        this.parent = parent;
        this.node = node;
        this.name = name;
        this.path = path;
        this.updates = updates;

        if (node != null && isIndexNodeType(node.getProperty(JCR_PRIMARYTYPE))) {
            update(node, name);
        }
        if (node != null && node.hasChildNode(INDEX_DEFINITIONS_NAME)) {
            NodeBuilder index = node.child(INDEX_DEFINITIONS_NAME);
            for (String indexName : index.getChildNodeNames()) {
                update(index.child(indexName), indexName);
            }
        }
    }

    public PropertyIndexDiff(
            NodeBuilder root, Map<String, List<PropertyIndexUpdate>> updates) {
        this(null, root, null, "/", updates);
    }

    public PropertyIndexDiff(PropertyIndexDiff parent, String name) {
        this(parent, getChildNode(parent.node, name),
                name, null, parent.updates);
    }

    private static NodeBuilder getChildNode(NodeBuilder node, String name) {
        if (node != null && node.hasChildNode(name)) {
            return node.child(name);
        } else {
            return null;
        }
    }

    private String getPath() {
        if (path == null) { // => parent != null
            path = concat(parent.getPath(), name);
        }
        return path;
    }

    private Iterable<PropertyIndexUpdate> getIndexes(String name) {
        List<PropertyIndexUpdate> indexes = updates.get(name);
        if (indexes != null) {
            return indexes;
        } else {
            return ImmutableList.of();
        }
    }

    private void update(NodeBuilder builder, String indexName) {
        PropertyState ps = builder.getProperty("propertyNames");
        Iterable<String> propertyNames = ps != null ? ps.getValue(Type.STRINGS)
                : ImmutableList.of(indexName);
        for (String pname : propertyNames) {
            List<PropertyIndexUpdate> list = this.updates.get(pname);
            if (list == null) {
                list = Lists.newArrayList();
                this.updates.put(pname, list);
            }
            list.add(new PropertyIndexUpdate(getPath(), builder));
        }
    }

    private boolean isIndexNodeType(PropertyState ps) {
        return ps != null && !ps.isArray()
                && ps.getValue(Type.STRING).equals(INDEX_DEFINITIONS_NODE_TYPE);
    }

    //-----------------------------------------------------< NodeStateDiff >--

    @Override
    public void propertyAdded(PropertyState after) {
        for (PropertyIndexUpdate update : getIndexes(after.getName())) {
            update.insert(getPath(), after);
        }
    }

    @Override
    public void propertyChanged(PropertyState before, PropertyState after) {
        for (PropertyIndexUpdate update : getIndexes(after.getName())) {
            update.remove(getPath(), before);
            update.insert(getPath(), after);
        }
    }

    @Override
    public void propertyDeleted(PropertyState before) {
        for (PropertyIndexUpdate update : getIndexes(before.getName())) {
            update.remove(getPath(), before);
        }
    }

    @Override
    public void childNodeAdded(String name, NodeState after) {
        childNodeChanged(name, MemoryNodeState.EMPTY_NODE, after);
    }

    @Override
    public void childNodeChanged(
            String name, NodeState before, NodeState after) {
        if (!NodeStateUtils.isHidden(name)) {
            after.compareAgainstBaseState(before, new PropertyIndexDiff(this, name));
        }
    }

    @Override
    public void childNodeDeleted(String name, NodeState before) {
        childNodeChanged(name, before, MemoryNodeState.EMPTY_NODE);
    }

}