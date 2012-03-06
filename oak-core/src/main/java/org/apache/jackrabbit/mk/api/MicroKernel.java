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
package org.apache.jackrabbit.mk.api;

import java.io.InputStream;

/**
 * The MicroKernel <b>design goals/principles</b>:
 * <ul>
 * <li>manage huge trees of nodes and properties efficiently</li>
 * <li>MVCC-based concurrency control</li>
 * <li>GIT/SVN-inspired DAG-based data model</li>
 * <li>highly scalable concurrent read & write operations</li>
 * <li>stateless API</li>
 * <li>portable to C</li>
 * <li>efficient support for large number of child nodes</li>
 * <li>integrated API for storing/retrieving large binaries (similar to existing DataStore API)</li>
 * <li>human-readable data serialization (JSON)</li>
 * </ul>
 * <p/>
 * The MicroKernel <b>Data Model</b>:
 * <ul>
 * <li>simple JSON-inspired data model: just nodes and properties</li>
 * <li>a node consists of an unordered set of name -&gt; item mappings. each
 * property and child node is uniquely named and a single name can only
 * refer to a property or a child node, not both at the same time.
 * <li>properties are represented as name/value pairs</li>
 * <li>supported property types: string, number</li>
 * <li>other property types (weak/hard reference, date, etc) would need to be
 * encoded/mangled in name or value</li>
 * <li>no support for JCR/XML-like namespaces, "foo:bar" is just an ordinary name</li>
 * </ul>
 * <p/>
 * <b>Architecture (overview)</b>:
 * <ol>
 * <li>JCR (full TCK-compliant implementation)</li>
 * <li>SPI (node types, workspaces, namespaces, access control, search, locking, ...)</li>
 * <li><i>MicroKernel</i></li>
 * </ol>
 * </ul>
 */
public interface MicroKernel {

    /**
     * Dispose this instance.
     */
    void dispose();

    //---------------------------------------------------------< REVISION ops >

    /**
     * Return the id of the current head revision.
     *
     * @return id of head revision
     * @throws MicroKernelException if an error occurs
     */
    String getHeadRevision() throws MicroKernelException;

    /**
     * Returns a chronological list of all revisions since a specific point
     * in time.
     * <p/>
     * Format:
     * <pre>
     * [ { "id" : "<revisionId>", "ts" : <revisionTimestamp> }, ... ]
     * </pre>
     *
     * @param since      timestamp (ms) of earliest revision to be returned
     * @param maxEntries maximum #entries to be returned;
     *                   if < 0, no limit will be applied.
     * @return a chronological list of revisions in JSON format.
     * @throws MicroKernelException if an error occurs
     */
    String /* jsonArray */ getRevisions(long since, int maxEntries)
            throws MicroKernelException;

    /**
     * Wait for a commit to occur that is newer than the given revision number.
     * <p/>
     * This method is useful efficient polling. The method will return the current head revision
     * if it is newer than the given old revision number, or wait until the given number of
     * milliseconds passed or a new head revision is available.
     *
     * @param maxWaitMillis the maximum number of milliseconds to wait (0 if the
     *                      method should not wait).
     * @return the current head revision
     * @throws MicroKernelException if an error occurs
     * @throws InterruptedException if the thread was interrupted
     */
    String waitForCommit(String oldHeadRevision, long maxWaitMillis) throws MicroKernelException, InterruptedException;

    /**
     * Returns a revision journal, starting with <code>fromRevisionId</code>
     * and ending with <code>toRevisionId</code>.
     * <p/>
     * Format:
     * <pre>
     * [ { "id" : "&lt;revisionId&gt;", "ts" : "&lt;revisionTimestamp&gt;", "msg" : "&lt;commitMessage&gt;", "changes" : "&lt;JSON diff&gt;" }, ... ]
     * </pre>
     *
     * @param fromRevisionId first revision to be returned in journal
     * @param toRevisionId   last revision to be returned in journal, if null the current head revision is assumed
     * @return a chronological list of revisions in JSON format
     * @throws MicroKernelException if an error occurs
     */
    String /* jsonArray */ getJournal(String fromRevisionId, String toRevisionId)
            throws MicroKernelException;

    /**
     * Returns the JSON diff representation of the changes between the specified
     * revisions. The changes will be consolidated if the specified range
     * covers intermediary revisions. The revisions need not be in a specified
     * chronological order.
     *
     * <p/>
     * Format:
     * <pre>
     * [ { "id" : "&lt;revisionId&gt;", "ts" : "&lt;revisionTimestamp&gt;", "msg" : "&lt;commitMessage&gt;", "changes" : "&lt;JSON diff&gt;" }, ... ]
     * </pre>
     *
     * @param fromRevisionId a revision
     * @param toRevisionId   another revision, if null the current head revision is assumed
     * @param path consider only changes that affected the specified subtree at <code>path</code>;
     *        if null the default "/" is assumed
     * @return JSON diff representation of the changes
     * @throws MicroKernelException if an error occurs
     */
    String /* JSON diff */ diff(String fromRevisionId, String toRevisionId,
                                String path)
            throws MicroKernelException;

    //-------------------------------------------------------------< READ ops >

    /**
     * Determines whether the specified node exists.
     *
     * @param path       path denoting node
     * @param revisionId revision, if null the current head revision is assumed
     * @return <code>true</code> if the specified node exists, otherwise <code>false</code>
     * @throws MicroKernelException if an error occurs
     */
    boolean nodeExists(String path, String revisionId) throws MicroKernelException;

    /**
     * Returns the number of child nodes of the specified node.
     * <p/>
     * This is a convenience method since this information could gathered by
     * calling <code>getNodes(path, revisionId, 0, 0, 0)</code> and evaluating
     * the <code>:childNodeCount</code> property.
     *
     *
     * @param path       path denoting node
     * @param revisionId revision, if null the current head revision is assumed
     * @return the number of child nodes
     * @throws MicroKernelException if an error occurs
     */
    long getChildNodeCount(String path, String revisionId) throws MicroKernelException;

    /**
     * Returns the node tree rooted at the specified parent node with depth 1.
     * Depth 1 means all properties of the node are returned, including the list
     * of child nodes and their properties (including
     * <code>:childNodeCount</code>). Example:
     * <pre>
     * {
     *     "someprop": "someval",
     *     ":childNodeCount": 2,
     *     "child1" : {
     *          "prop1": "foo",
     *          ":childNodeCount": 2
     *      },
     *      "child2": {
     *          "prop1": "bar"
     *          ":childNodeCount": 0
     *      }
     * }
     * </pre>
     * The collection of name/value pairs denoting child nodes is assumed to be
     * ordered.
     * <p/>
     * Remarks:
     * <ul>
     * <li>If the property <code>:childNodeCount</code> equals 0, then the
     * node does not have any child nodes.
     * <li>If the value of <code>:childNodeCount</code> is larger than the list
     * of returned child nodes, then the node has more child nodes than those
     * included in the tree. Large number of child nodes can be retrieved in
     * chunks using {@link #getNodes(String, String, int, long, int)}</li>
     * </ul>
     * This method is a convenience method for
     * <code>getNodes(path, revisionId, 1, 0, -1)</code>
     *
     * @param path       path denoting root of node tree to be retrieved
     * @param revisionId revision, if null the current head revision is assumed
     * @return node tree in JSON format
     * @throws MicroKernelException if an error occurs
     */
    String /* jsonTree */ getNodes(String path, String revisionId) throws MicroKernelException;

    /**
     * Returns the node tree rooted at the specified parent node with the
     * specified depth, maximum child node count and offset. The depth of the
     * returned tree is governed by the <code>depth</code> parameter:
     * <table>
     * <tr>
     * <td>depth = 0</td>
     * <td>properties, including <code>:childNodeCount</code> and the list
     * of child node names (as empty objects)</td>
     * </tr>
     * <tr>
     * <td>depth = 1</td>
     * <td>properties, child nodes and their properties (including
     * <code>:childNodeCount</code>)</td>
     * </tr>
     * <tr>
     * <td>depth = 2</td>
     * <td>[and so on...]</td>
     * </tr>
     * </table>
     * Offset and count only affect the returned child node list of this node.
     *
     * @param path       path denoting root of node tree to be retrieved
     * @param revisionId revision, if null the current head revision is assumed
     * @param depth      maximum depth of returned tree
     * @param offset     start position in child node list (0 to start at the
     *                   beginning)
     * @param count      maximum number of child nodes to retrieve (-1 for as many as
     *                   possible)
     * @return node tree in JSON format
     * @throws MicroKernelException if an error occurs
     */
    String /* jsonTree */ getNodes(String path, String revisionId, int depth, long offset, int count) throws MicroKernelException;

    //------------------------------------------------------------< WRITE ops >

    /**
     * Applies the specified changes on the specified target node.
     * <p>
     * If <code>path.length() == 0</code> the paths specified in the
     * <code>jsonDiff</code> are expected to be absolute.
     * <p>
     * The implementation tries to merge changes if the revision id of the
     * commit is set accordingly. As an example, deleting a node is allowed if
     * the node existed in the given revision, even if it was deleted in the
     * meantime.
     *
     * @param path path denoting target node
     * @param jsonDiff changes to be applied in JSON diff format.
     * @param revisionId revision the changes are based on, if null the current head revision is assumed
     * @param message commit message
     * @return id of newly created revision
     * @throws MicroKernelException if an error occurs
     */
    String /* revisionId */ commit(String path, String jsonDiff, String revisionId, String message)
            throws MicroKernelException;


    //--------------------------------------------------< BLOB READ/WRITE ops >

    /**
     * Returns the length of the specified blob.
     *
     * @param blobId blob identifier
     * @return length of the specified blob
     * @throws MicroKernelException if an error occurs
     */
    long getLength(String blobId) throws MicroKernelException;

    /**
     * Reads up to <code>length</code> bytes of data from the specified blob into
     * the given array of bytes.  An attempt is made to read as many as
     * <code>length</code> bytes, but a smaller number may be read.
     * The number of bytes actually read is returned as an integer.
     *
     * @param blobId blob identifier
     * @param pos    the offset within the blob
     * @param buff   the buffer into which the data is read.
     * @param off    the start offset in array <code>buff</code>
     *               at which the data is written.
     * @param length the maximum number of bytes to read
     * @return the total number of bytes read into the buffer, or
     *         <code>-1</code> if there is no more data because the end of
     *         the blob content has been reached.
     * @throws MicroKernelException if an error occurs
     */
    int /* count */ read(String blobId, long pos, byte[] buff, int off, int length)
            throws MicroKernelException;

    /**
     * Stores the content of the given stream and returns an associated
     * identifier for later retrieval.
     * <p>
     * If identical stream content has been stored previously, then the existing
     * identifier will be returned instead of storing a redundant copy.
     * <p>
     * The stream is closed by this method.
     *
     * @param in InputStream providing the blob content
     * @return blob identifier associated with the given content
     * @throws MicroKernelException if an error occurs
     */
    String /* blobId */ write(InputStream in) throws MicroKernelException;
}