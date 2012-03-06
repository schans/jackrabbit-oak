/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.mk.model;

import org.apache.jackrabbit.mk.store.Binding;

/**
 *
 */
public class StoredCommit extends AbstractCommit {

    private final String id;

    public static StoredCommit deserialize(String id, Binding binding) throws Exception {
        String rootNodeId = binding.readStringValue("rootNodeId");
        long commitTS = binding.readLongValue("commitTS");
        String msg = binding.readStringValue("msg");
        String parentId = binding.readStringValue("parentId");
        return new StoredCommit(id, "".equals(parentId) ? null : parentId, commitTS, rootNodeId, "".equals(msg) ? null : msg);
    }

    public StoredCommit(String id, String parentId, long commitTS, String rootNodeId, String msg) {
        this.id = id;
        this.parentId = parentId;
        this.commitTS = commitTS;
        this.rootNodeId = rootNodeId;
        this.msg = msg;
    }

    public StoredCommit(String id, Commit commit) {
        super(commit);
        this.id = id;
    }

    public String getId() {
        return id;
    }
}