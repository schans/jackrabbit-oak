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
package org.apache.jackrabbit.mk.store;

import java.io.Closeable;
import java.io.InputStream;
import java.util.Iterator;

import org.apache.jackrabbit.mk.model.ChildNode;
import org.apache.jackrabbit.mk.model.ChildNodeEntriesMap;
import org.apache.jackrabbit.mk.model.Id;
import org.apache.jackrabbit.mk.model.MutableCommit;
import org.apache.jackrabbit.mk.model.MutableNode;
import org.apache.jackrabbit.mk.model.NodeState;
import org.apache.jackrabbit.mk.model.StoredCommit;
import org.apache.jackrabbit.mk.model.StoredNode;
import org.apache.jackrabbit.mk.util.IOUtils;

/**
 * Revision garbage collector that copies reachable revisions from a "from" revision
 * store to a "to" revision store. It assumes that both stores share the same blob
 * store.
 * 
 * In the current design, the head revision and all the nodes it references are
 * reachable.
 */
public class CopyingGC implements RevisionStore, Closeable {
    
    /**
     * From store.
     */
    private RevisionStore rsFrom;
    
    /**
     * To store.
     */
    private RevisionStore rsTo;

    /**
     * Flag indicating whether a GC cycle is running.
     */
    private volatile boolean running;
    
    /**
     * Create a new instance of this class.
     * 
     * @param rsFrom from store
     * @param rsTo to store 
     */
    public CopyingGC(RevisionStore rsFrom, RevisionStore rsTo) {
        this.rsFrom = rsFrom;
        this.rsTo = rsTo;
    }
    
    /**
     * Start GC cycle.
     * 
     * @throws Exception if an error occurs
     */
    public void start() throws Exception {
        // Copy the head commit
        MutableCommit commitTo = copy(rsFrom.getHeadCommit());
        commitTo.setParentId(rsTo.getHeadCommitId());
        
        rsTo.lockHead();
        
        try {
            rsTo.putHeadCommit(commitTo);
        } finally {
            rsTo.unlockHead();
        }
        running = true;
    }
    
    /**
     * Stop GC cycle.
     */
    public void stop() {
        running = false;
        
        // TODO: swap rsFrom/rsTo and reset them
        rsFrom = rsTo;
        rsTo = null;
    }
    
    public void close() {
        if (rsFrom instanceof Closeable) {
            IOUtils.closeQuietly((Closeable) rsFrom);
        }
        if (rsTo instanceof Closeable) {
            IOUtils.closeQuietly((Closeable) rsTo);
        }
    }
    
    /**
     * Copy a commit and all the nodes belonging to it, starting at the root node.
     * 
     * @param commit commit to copy
     * @return commit in the "to" store, not yet persisted
     * @throws Exception if an error occurs
     */
    private MutableCommit copy(StoredCommit commit) throws Exception {
        StoredNode nodeFrom = rsFrom.getNode(commit.getRootNodeId());
        copy(nodeFrom);
        
        return new MutableCommit(commit);
    }
    
    /**
     * Copy a node and all its descendants into a target store
     * @param node source node
     * @throws Exception if an error occurs
     */
    private void copy(StoredNode node) throws Exception {
        try {
            rsTo.getNode(node.getId());
            return;
        } catch (NotFoundException e) {
            // ignore, better add a has() method
        }
        rsTo.putNode(new MutableNode(node, rsTo));

        Iterator<ChildNode> iter = node.getChildNodeEntries(0, -1);
        while (iter.hasNext()) {
            ChildNode c = iter.next();
            copy(rsFrom.getNode(c.getId()));
        }
    }
    
    // ---------------------------------------------------------- RevisionStore

    public NodeState getNodeState(StoredNode node) {
        return new StoredNodeAsState(node, this);
    }

    public Id getId(NodeState node) {
        return ((StoredNodeAsState) node).getId();
    }

    public StoredNode getNode(Id id) throws NotFoundException, Exception {
        if (running) {
            return rsTo.getNode(id);
        }
        return rsFrom.getNode(id);
    }

    public StoredCommit getCommit(Id id) throws NotFoundException,
            Exception {
        
        if (running) {
            return rsTo.getCommit(id);
        }
        return rsFrom.getCommit(id);
    }

    public ChildNodeEntriesMap getCNEMap(Id id) throws NotFoundException,
            Exception {
        
        if (running) {
            return rsTo.getCNEMap(id);
        }
        return rsFrom.getCNEMap(id);
    }

    public StoredNode getRootNode(Id commitId) throws NotFoundException,
            Exception {

        if (running) {
            return rsTo.getRootNode(commitId);
        }
        return rsFrom.getRootNode(commitId);
    }

    public StoredCommit getHeadCommit() throws Exception {
        return running ? rsTo.getHeadCommit() : rsFrom.getHeadCommit(); 
    }

    public Id getHeadCommitId() throws Exception {
        return running ? rsTo.getHeadCommitId() : rsFrom.getHeadCommitId();
    }

    public Id putNode(MutableNode node) throws Exception {
        return running ? rsTo.putNode(node) : rsFrom.putNode(node);
    }

    public Id putCNEMap(ChildNodeEntriesMap map) throws Exception {
        return running ? rsTo.putCNEMap(map) : rsFrom.putCNEMap(map);
    }

    // TODO: potentially dangerous, if lock & unlock interfere with GC start
    public void lockHead() {
        if (running) {
            rsTo.lockHead();
        } else {
            rsFrom.lockHead();
        }
    }

    public Id putHeadCommit(MutableCommit commit) throws Exception {
        return running ? rsTo.putHeadCommit(commit) : rsFrom.putHeadCommit(commit);
    }

    // TODO: potentially dangerous, if lock & unlock interfere with GC start
    public void unlockHead() {
        if (running) {
            rsTo.unlockHead();
        } else {
            rsFrom.unlockHead();
        }
    }
    
    public int getBlob(String blobId, long pos, byte[] buff, int off, int length)
            throws NotFoundException, Exception {
        
        // Assuming that from and to store use the same BlobStore instance
        return rsTo.getBlob(blobId, pos, buff, off, length);
    }

    public long getBlobLength(String blobId) throws NotFoundException,
            Exception {
        
        // Assuming that from and to store use the same BlobStore instance
        return rsTo.getBlobLength(blobId);
    }

    public String putBlob(InputStream in) throws Exception {
        // Assuming that from and to store use the same BlobStore instance
        return rsTo.putBlob(in);
    }
}