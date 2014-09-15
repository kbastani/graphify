package org.neo4j.nlp.impl.cache;

import com.google.common.cache.Cache;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.Evaluators;
import org.neo4j.helpers.collection.IteratorUtil;
import org.neo4j.nlp.helpers.GraphManager;
import org.neo4j.nlp.impl.manager.NodeManager;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import static org.neo4j.graphdb.DynamicRelationshipType.withName;

/**
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
*/public abstract class RelationshipCache {

    public List<Long> getRelationships(Long start, GraphDatabaseService db, GraphManager graphManager)
    {
        return getLongs(start, db, graphManager);
    }

    private List<Long> getLongs(Long start, GraphDatabaseService db, GraphManager graphManager) {
        List<Long> relList;

        Cache<Long, List<Long>> relCache = getRelationshipCache();
        relList = relCache.getIfPresent(start);

        NodeManager nodeManager = new NodeManager();
        String pattern = (String)nodeManager.getNodeAsMap(start,db).get("pattern");
        Node startNode = graphManager.getOrCreateNode(pattern, db);

        if(relList == null)
            relList = getLongs(start, db, null, startNode);

        return relList;
    }

    private List<Long> getLongs(Long start, GraphDatabaseService db, List<Long> relList, Node startNode) {
        if (relList == null) {
            try (Transaction tx = db.beginTx()) {
                relList = IteratorUtil.asCollection(db.traversalDescription()
                        .depthFirst()
                        .relationships(withName(getRelationshipType()), Direction.OUTGOING)
                        .evaluator(Evaluators.fromDepth(1))
                        .evaluator(Evaluators.toDepth(1))
                        .traverse(startNode)
                        .nodes())
                        .stream()
                        .map(Node::getId)
                        .collect(Collectors.toList());

                relList = new HashSet<>(relList).stream().map(n -> n).collect(Collectors.toList());

                if (relList.size() > 0) {
                    String propertyKey = getRelationshipAggregateKey();
                    Integer propertyValue = relList.size();
                    startNode.setProperty(propertyKey, propertyValue);
                }

                tx.success();
            }

            getRelationshipCache().put(start, relList);

        }
        return relList;
    }

    public void getOrCreateRelationship(Long start, Long end, GraphDatabaseService db, GraphManager graphManager) {

        List<Long> relList = getLongs(start, db, graphManager);

        if (!relList.contains(end)) {
            Transaction tx = db.beginTx();
            try {
                Node startNode = db.getNodeById(start);
                Node endNode = db.getNodeById(end);
                Relationship rel = startNode.createRelationshipTo(endNode, withName(getRelationshipType()));
                rel.setProperty("matches", 1);
                relList.add(end);
                relList = new HashSet<>(relList).stream().map(n -> n).collect(Collectors.toList());
                startNode.setProperty(getRelationshipAggregateKey(), relList.size());
                tx.success();
            } catch (final Exception e) {
                tx.failure();
            } finally {
                tx.close();
                getRelationshipCache().put(start, relList);
            }
        }
        else {
            Node startNode = db.getNodeById(start);

            Relationship rel =IteratorUtil.asCollection(db.traversalDescription()
                    .depthFirst()
                    .relationships(withName(getRelationshipType()), Direction.OUTGOING)
                    .evaluator(Evaluators.fromDepth(1))
                    .evaluator(Evaluators.toDepth(1))
                    .traverse(startNode)
                    .relationships())
                    .stream()
                    .filter(a -> a.getEndNode().getId() == end)
                    .findFirst()
                    .get();

            Integer matches = (Integer)rel.getProperty("matches");

            rel.setProperty("matches", matches + 1);
        }
    }

    protected abstract Cache<Long, List<Long>> getRelationshipCache();
    protected abstract String getRelationshipAggregateKey();
    protected abstract String getRelationshipType();
}
