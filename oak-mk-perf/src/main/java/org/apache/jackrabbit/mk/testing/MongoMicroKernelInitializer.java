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
package org.apache.jackrabbit.mk.testing;

import java.util.ArrayList;

import org.apache.jackrabbit.mk.api.MicroKernel;
import org.apache.jackrabbit.mk.blobs.BlobStore;
import org.apache.jackrabbit.mk.util.Configuration;
import org.apache.jackrabbit.mongomk.api.NodeStore;
import org.apache.jackrabbit.mongomk.impl.BlobStoreMongo;
import org.apache.jackrabbit.mongomk.impl.MongoConnection;
import org.apache.jackrabbit.mongomk.impl.MongoMicroKernel;
import org.apache.jackrabbit.mongomk.impl.NodeStoreMongo;

import com.mongodb.BasicDBObjectBuilder;

/**
 * Creates a {@code MongoMicroKernel}.Initialize the mongo database for the
 * tests.
 */
public class MongoMicroKernelInitializer implements MicroKernelInitializer {

    public ArrayList<MicroKernel> init(Configuration conf, int mksNumber)
            throws Exception {

        ArrayList<MicroKernel> mks = new ArrayList<MicroKernel>();
        MongoConnection mongoConnection;
        mongoConnection = new MongoConnection(conf.getHost(),
                conf.getMongoPort(), conf.getMongoDatabase());

        // initialize the database
        // temporary workaround.Remove the sleep.
        Thread.sleep(1000);
        mongoConnection.initializeDB(true);
        mongoConnection = new MongoConnection(conf.getHost(),
                conf.getMongoPort(), "admin");
        // set the shard key
        mongoConnection.getDB()
                .command(
                        BasicDBObjectBuilder
                                .start("shardCollection", "test.nodes")
                                .push("key").add("path", 1).add("revId", 1)
                                .pop().get());

        for (int i = 0; i < mksNumber; i++) {
            mongoConnection = new MongoConnection(conf.getHost(),
                     conf.getMongoPort() , conf.getMongoDatabase());
            NodeStore nodeStore = new NodeStoreMongo(mongoConnection);
            BlobStore blobStore = new BlobStoreMongo(mongoConnection);
            mks.add(new MongoMicroKernel(nodeStore, blobStore));
        }

        return mks;
    }

    public String getType() {
        return "Mongo Microkernel implementation";
    }
}
