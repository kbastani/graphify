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
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.traversal.Evaluators;

import java.util.ArrayList;
import java.util.List;

import static org.neo4j.graphdb.DynamicRelationshipType.withName;

/**
 * This class is used to cache relationships that exist in a pattern recognition hierarchy.
 */
public class DataRelationshipManager {

    public static final Cache<Long, List<Long>> relationshipCache = CacheBuilder.newBuilder().maximumSize(20000000).build();
    private final String relationshipType;

    public DataRelationshipManager()
    {
        this.relationshipType = "MATCHES";
    }

    public List<Long> getDataNodesById(Long start)
    {
        return relationshipCache.getIfPresent(start);
    }

    public void getOrCreateNode(Long start, Long end, GraphDatabaseService db) {
        List<Long> relList = relationshipCache.getIfPresent(start);

        Node startNode = db.getNodeById(start);

        if (relList == null) {
            List<Long> nodeList = new ArrayList<>();
            for(Node endNodes : db.traversalDescription()
                    .depthFirst()
                    .relationships(withName(relationshipType), Direction.OUTGOING)
                    .evaluator(Evaluators.fromDepth(1))
                    .evaluator(Evaluators.toDepth(1))
                    .traverse(startNode)
                    .nodes())
            {
                nodeList.add(endNodes.getId());
            }

            relList = nodeList;
            relationshipCache.put(start, relList);
        }

        if (!relList.contains(end)) {
            Transaction tx = db.beginTx();
            try {
                Node endNode = db.getNodeById(end);
                startNode.createRelationshipTo(endNode, withName(relationshipType));
                tx.success();
            } catch (final Exception e) {
                tx.failure();
            } finally {
                tx.close();
                relList.add(end);
                relationshipCache.put(start, relList);
            }
        }
    }
}