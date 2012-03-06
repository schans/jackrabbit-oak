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
package org.apache.jackrabbit.mk;

import java.io.File;

import org.apache.jackrabbit.mk.model.ChildNodeEntry;
import org.apache.jackrabbit.mk.model.Commit;
import org.apache.jackrabbit.mk.model.CommitBuilder;
import org.apache.jackrabbit.mk.model.Node;
import org.apache.jackrabbit.mk.model.StoredCommit;
import org.apache.jackrabbit.mk.model.StoredNode;
import org.apache.jackrabbit.mk.store.DefaultRevisionStore;
import org.apache.jackrabbit.mk.store.NotFoundException;
import org.apache.jackrabbit.mk.store.RevisionStore;
import org.apache.jackrabbit.mk.util.PathUtils;

/**
 *
 */
public class Repository {

    private final String homeDir;
    private boolean initialized;
    private final DefaultRevisionStore rs;

    public Repository(String homeDir) throws Exception {
        File home = new File(homeDir == null ? "." : homeDir, ".mk");
        if (!home.exists()) {
            home.mkdirs();
        }
        this.homeDir = home.getCanonicalPath();

        rs = new DefaultRevisionStore();
    }
    
    /**
     * Alternate constructor, used for testing.
     * 
     * @param rs revision store, already initialized
     */
    public Repository(DefaultRevisionStore rs) {
        this.homeDir = null;
        this.rs = rs;
        
        initialized = true;
    }
    
    public void init() throws Exception {
        if (initialized) {
            return;
        }
        rs.initialize(new File(homeDir));

        initialized = true;
    }

    public void shutDown() throws Exception {
        if (!initialized) {
            return;
        }

        rs.close();

        initialized = false;
    }

    public RevisionStore getRevisionStore() {
        if (!initialized) {
            throw new IllegalStateException("not initialized");
        }

        return rs;
    }

    public String getHeadRevision() throws Exception {
        if (!initialized) {
            throw new IllegalStateException("not initialized");
        }
        return rs.getHeadCommitId();
    }

    public StoredCommit getHeadCommit() throws Exception {
        if (!initialized) {
            throw new IllegalStateException("not initialized");
        }
        return rs.getHeadCommit();
    }

    public StoredCommit getCommit(String id) throws NotFoundException, Exception {
        if (!initialized) {
            throw new IllegalStateException("not initialized");
        }
        return rs.getCommit(id);
    }

    /**
     *
     * @param revId
     * @param path
     * @return
     * @throws NotFoundException if either path or revision doesn't exist
     * @throws Exception if another error occurs
     */
    public StoredNode getNode(String revId, String path) throws NotFoundException, Exception {
        if (!initialized) {
            throw new IllegalStateException("not initialized");
        }

        StoredNode root = rs.getRootNode(revId);
        if (PathUtils.denotesRoot(path)) {
            return root;
        }

        //return root.getNode(path.substring(1), pm);
        String[] ids = resolvePath(revId, path);
        return rs.getNode(ids[ids.length - 1]);
    }

    public boolean nodeExists(String revId, String path) {
        if (!initialized) {
            throw new IllegalStateException("not initialized");
        }

        if (!PathUtils.isAbsolute(path)) {
            throw new IllegalArgumentException("illegal path");
        }

        try {
            String[] names = PathUtils.split(path);
            Node parent = rs.getRootNode(revId);
            for (int i = 0; i < names.length; i++) {
                ChildNodeEntry cne = parent.getChildNodeEntry(names[i]);
                if (cne == null) {
                    return false;
                }
                parent = rs.getNode(cne.getId());
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public CommitBuilder getCommitBuilder(String revId, String msg) throws Exception {
        return new CommitBuilder(revId, msg, rs);

    }

    /**
     *
     * @param revId
     * @param nodePath
     * @return
     * @throws IllegalArgumentException if the specified path is not absolute
     * @throws NotFoundException if either path or revision doesn't exist
     * @throws Exception if another error occurs
     */
    String[] /* array of node id's */ resolvePath(String revId, String nodePath) throws Exception {
        if (!PathUtils.isAbsolute(nodePath)) {
            throw new IllegalArgumentException("illegal path");
        }

        Commit commit = rs.getCommit(revId);

        if (PathUtils.denotesRoot(nodePath)) {
            return new String[]{commit.getRootNodeId()};
        }
        String[] names = PathUtils.split(nodePath);
        String[] ids = new String[names.length + 1];

        // get root node
        ids[0] = commit.getRootNodeId();
        Node parent = rs.getNode(ids[0]);
        // traverse path and remember id of each element
        for (int i = 0; i < names.length; i++) {
            ChildNodeEntry cne = parent.getChildNodeEntry(names[i]);
            if (cne == null) {
                throw new NotFoundException(nodePath);
            }
            ids[i + 1] = cne.getId();
            parent = rs.getNode(cne.getId());
        }
        return ids;
    }
}