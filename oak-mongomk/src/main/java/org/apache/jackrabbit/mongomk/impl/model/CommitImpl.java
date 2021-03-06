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
package org.apache.jackrabbit.mongomk.impl.model;

import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import org.apache.jackrabbit.mongomk.api.instruction.Instruction;
import org.apache.jackrabbit.mongomk.api.model.Commit;

/**
 * Implementation of {@link Commit}.
 */
public class CommitImpl implements Commit {

    private final String diff;
    private final List<Instruction> instructions;
    private final String message;
    private final String path;
    private final long timestamp;

    private Long baseRevisionId;
    private String branchId;
    private Long revisionId;

    /**
     * Constructs a new {@code CommitImpl}.
     *
     * @param path The path.
     * @param diff The diff.
     * @param message The message.
     */
    public CommitImpl(String path, String diff, String message) {
        this.path = path;
        this.diff = diff;
        this.message = message;
        instructions = new LinkedList<Instruction>();
        timestamp = new Date().getTime();
    }

    /**
     * Adds the given {@link Instruction}.
     *
     * @param instruction The {@code Instruction}.
     */
    public void addInstruction(Instruction instruction) {
        instructions.add(instruction);
    }

    @Override
    public Long getBaseRevisionId() {
        return baseRevisionId;
    }

    public void setBaseRevisionId(Long baseRevisionId) {
        this.baseRevisionId = baseRevisionId;
    }

    @Override
    public String getBranchId() {
        return branchId;
    }

    public void setBranchId(String branchId) {
        this.branchId = branchId;
    }

    @Override
    public String getDiff() {
        return diff;
    }

    @Override
    public List<Instruction> getInstructions() {
        return Collections.unmodifiableList(instructions);
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public Long getRevisionId() {
        return revisionId;
    }

    @Override
    public void setRevisionId(Long revisionId) {
        this.revisionId = revisionId;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }
}