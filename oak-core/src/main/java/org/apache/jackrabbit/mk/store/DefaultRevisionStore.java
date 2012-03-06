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

import org.apache.jackrabbit.mk.blobs.BlobStore;
import org.apache.jackrabbit.mk.blobs.FileBlobStore;
import org.apache.jackrabbit.mk.model.ChildNodeEntriesMap;
import org.apache.jackrabbit.mk.model.MutableCommit;
import org.apache.jackrabbit.mk.model.Node;
import org.apache.jackrabbit.mk.model.MutableNode;
import org.apache.jackrabbit.mk.model.StoredCommit;
import org.apache.jackrabbit.mk.model.StoredNode;
import org.apache.jackrabbit.mk.store.pm.H2PersistenceManager;
import org.apache.jackrabbit.mk.store.pm.PersistenceManager;
import org.apache.jackrabbit.mk.util.SimpleLRUCache;
import org.apache.jackrabbit.mk.util.StringUtils;

import java.io.File;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Default revision store implementation, passing calls to a <code>PersistenceManager</code>
 * and a <code>BlobStore</code>, respectively and providing caching. 
 */
public class DefaultRevisionStore implements RevisionStore {

    public static final String CACHE_SIZE = "mk.cacheSize";
    public static final int DEFAULT_CACHE_SIZE = 10000;

    private boolean initialized;

    private String headId;
    private long headCounter;
    private final ReentrantReadWriteLock headLock = new ReentrantReadWriteLock();
    private PersistenceManager pm;
    private BlobStore blobStore;
    private boolean blobStoreNeedsClose;

    private Map<String, Object> cache;

    public void initialize(File homeDir) throws Exception {
        if (initialized) {
            throw new IllegalStateException("already initialized");
        }

        cache = Collections.synchronizedMap(SimpleLRUCache.<String, Object>newInstance(determineInitialCacheSize()));

        pm = new H2PersistenceManager();
        //pm = new InMemPersistenceManager();
        //pm = new MongoPersistenceManager();
        //pm = new BDbPersistenceManager();
        //pm = new FSPersistenceManager();
        pm.initialize(homeDir);
        
        if (pm instanceof BlobStore) {
            blobStore = (BlobStore) pm;
        } else {
            blobStore = new FileBlobStore(new File(homeDir, "blobs").getCanonicalPath());
            blobStoreNeedsClose = true;
        }

        // make sure we've got a HEAD commit
        headId = pm.readHead();
        if (headId == null || headId.length() == 0) {
            // assume virgin repository
            byte[] rawHeadId = longToBytes(++headCounter);
            headId = StringUtils.convertBytesToHex(rawHeadId);
            
            String rootNodeId = pm.writeNode(new MutableNode(this));
            MutableCommit initialCommit = new MutableCommit();
            initialCommit.setCommitTS(System.currentTimeMillis());
            initialCommit.setRootNodeId(rootNodeId);
            pm.writeCommit(rawHeadId, initialCommit);
            pm.writeHead(headId);
        } else {
            headCounter = Long.parseLong(headId, 16);
        }

        initialized = true;
    }

    public void close() {
        verifyInitialized();

        cache.clear();

        if (blobStoreNeedsClose) {
            blobStore.close();
        }
        pm.close();
        
        initialized = false;
    }

    protected void verifyInitialized() {
        if (!initialized) {
            throw new IllegalStateException("not initialized");
        }
    }

    protected int determineInitialCacheSize() {
        String val = System.getProperty(CACHE_SIZE);
        return (val != null) ? Integer.parseInt(val) : DEFAULT_CACHE_SIZE;
    }

    /**
     * Convert a long value into a fixed-size byte array of size 16.
     * 
     * @param value value
     * @return byte array
     */
    static byte[] longToBytes(long value) {
        byte[] result = new byte[16];
        
        for (int i = result.length - 1; i >= 0 && value != 0; i--) {
            result[i] = (byte) (value & 0xff);
            value >>>= 8;
        }
        return result;
    }
    
    //--------------------------------------------------------< RevisionStore >

    public String putNode(Node node) throws Exception {
        verifyInitialized();

        PersistHook callback = null;
        if (node instanceof PersistHook) {
            callback = (PersistHook) node;
            callback.prePersist(this);
        }

        String id = pm.writeNode(node);
        
        if (callback != null)  {
            callback.postPersist(this);
        }
        
        cache.put(id, new StoredNode(id, node, this));

        return id;
    }

    public String putCNEMap(ChildNodeEntriesMap map) throws Exception {
        verifyInitialized();

        PersistHook callback = null;
        if (map instanceof PersistHook) {
            callback = (PersistHook) map;
            callback.prePersist(this);
        }

       String id = pm.writeCNEMap(map);

        if (callback != null)  {
            callback.postPersist(this);
        }

        cache.put(id, map);

        return id;
    }

    public String putCommit(MutableCommit commit) throws Exception {
        verifyInitialized();

        PersistHook callback = null;
        if (commit instanceof PersistHook) {
            callback = (PersistHook) commit;
            callback.prePersist(this);
        }

        String id = commit.getId();
        byte[] rawId;
        
        if (id == null) {
            rawId = longToBytes(++headCounter);
            id = StringUtils.convertBytesToHex(rawId);
        } else {
            rawId = StringUtils.convertHexToBytes(id);
        }
        pm.writeCommit(rawId, commit);

        if (callback != null)  {
            callback.postPersist(this);
        }

        cache.put(id, new StoredCommit(id, commit));

        return id;
    }

    public void setHeadCommitId(String commitId) throws Exception {
        verifyInitialized();

        headLock.writeLock().lock();
        try {
            pm.writeHead(commitId);
            headId = commitId;
        } finally {
            headLock.writeLock().unlock();
        }
    }

    public void lockHead() {
        headLock.writeLock().lock();
    }

    public void unlockHead() {
        headLock.writeLock().unlock();
    }

    public String putBlob(InputStream in) throws Exception {
        verifyInitialized();

        return blobStore.writeBlob(in);
    }

    //-----------------------------------------------------< RevisionProvider >

    public StoredNode getNode(String id) throws NotFoundException, Exception {
        verifyInitialized();

        StoredNode node = (StoredNode) cache.get(id);
        if (node != null) {
            return node;
        }

        Binding nodeBinding = pm.readNodeBinding(id);
        node = StoredNode.deserialize(id, this, nodeBinding);

        cache.put(id, node);

        return node;
    }

    public ChildNodeEntriesMap getCNEMap(String id) throws NotFoundException, Exception {
        verifyInitialized();

        ChildNodeEntriesMap map = (ChildNodeEntriesMap) cache.get(id);
        if (map != null) {
            return map;
        }

        map = pm.readCNEMap(id);

        cache.put(id, map);

        return map;
    }

    public StoredCommit getCommit(String id) throws NotFoundException, Exception {
        verifyInitialized();

        StoredCommit commit = (StoredCommit) cache.get(id);
        if (commit != null) {
            return commit;
        }

        commit = pm.readCommit(id);
        cache.put(id, commit);

        return commit;
    }

    public StoredNode getRootNode(String commitId) throws NotFoundException, Exception {
        return getNode(getCommit(commitId).getRootNodeId());
    }

    public StoredCommit getHeadCommit() throws Exception {
        return getCommit(getHeadCommitId());
    }

    public String getHeadCommitId() throws Exception {
        verifyInitialized();

        headLock.readLock().lock();
        try {
            return headId;
        } finally {
            headLock.readLock().unlock();
        }
    }

    public int getBlob(String blobId, long pos, byte[] buff, int off, int length) throws NotFoundException, Exception {
        verifyInitialized();

        return blobStore.readBlob(blobId, pos, buff, off, length);
    }

    public long getBlobLength(String blobId) throws NotFoundException, Exception {
        verifyInitialized();

        return blobStore.getBlobLength(blobId);
    }
}