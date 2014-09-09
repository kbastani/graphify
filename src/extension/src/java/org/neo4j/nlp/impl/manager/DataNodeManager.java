/*
 * Copyright (C) 2014 Kenny Bastani
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.neo4j.nlp.impl.manager;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.index.UniqueFactory;
import org.neo4j.nlp.abstractions.Manager;

import java.util.Map;

/**
 * This data manages a cache wrapper for a set of label data used to label unrecognized data in the
 * pattern recognition hierarchy.
 */
public class DataNodeManager extends Manager {

    public static final Cache<String, Long> dataCache = CacheBuilder.newBuilder().maximumSize(20000000).build();
    private UniqueFactory<Node> dataFactory;
    private final String label;
    private final String propertyKey;
    private final Label dynamicLabel;

    public DataNodeManager()
    {
        this.label = "Data";
        this.propertyKey = "value";
        dynamicLabel = DynamicLabel.label("Data");
    }

    @Override
    public Node getOrCreateNode(String keyValue, GraphDatabaseService db) {
        Node nodeStart = null;
        Long nodeId = dataCache.getIfPresent(keyValue);

        if (nodeId == null) {
            Transaction tx = db.beginTx();
            ResourceIterator<Node> results = db.findNodesByLabelAndProperty(dynamicLabel, propertyKey, keyValue).iterator();
            if (results.hasNext()) {
                nodeStart = results.next();
                dataCache.put(keyValue, nodeStart.getId());
            }
            tx.success();

        } else {
            nodeStart = db.getNodeById(nodeId);
        }

        if (nodeStart == null) {
            Transaction tx = db.beginTx();
            try {
                createNodeFactory(db);
                nodeStart = dataFactory.getOrCreate(propertyKey, keyValue);
                nodeStart.addLabel(dynamicLabel);
                tx.success();
            } catch (final Exception e) {
                tx.failure();
            } finally {
                tx.close();
                dataCache.put(keyValue, nodeStart != null ? nodeStart.getId() : 0);
            }
        }

        return nodeStart;
    }


    private void createNodeFactory(GraphDatabaseService db) {
        if (dataFactory == null) {
            dataFactory = new UniqueFactory.UniqueNodeFactory(db, label) {
                @Override
                protected void initialize(Node created, Map<String, Object> properties) {
                    created.setProperty(propertyKey, properties.get(propertyKey));
                }
            };
        }
    }
}

