package org.neo4j.nlp.impl.traversal;

import org.junit.Test;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.nlp.helpers.GraphManager;
import org.neo4j.nlp.impl.cache.ClassRelationshipCache;
import org.neo4j.nlp.impl.cache.PatternRelationshipCache;
import org.neo4j.nlp.impl.manager.ClassNodeManager;
import org.neo4j.nlp.impl.manager.DataNodeManager;
import org.neo4j.nlp.impl.manager.DataRelationshipManager;
import org.neo4j.nlp.impl.manager.NodeManager;
import org.neo4j.nlp.impl.util.LearningManager;
import org.neo4j.test.TestGraphDatabaseFactory;
import scala.collection.mutable.HashMap;
import traversal.DecisionTree;

import java.util.Arrays;

/**
 * Copyright (C) 2014 Kenny Bastani
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
public class DecisionTreeTest {
    public static NodeManager nodeManager = new NodeManager();

    private GraphDatabaseService setUpDb() {
        return new TestGraphDatabaseFactory().newImpermanentDatabaseBuilder().newGraphDatabase();
    }

    @Test
    public void testPatternMatchTraversal() throws Exception {
        // Invalidate all caches
        NodeManager.globalNodeCache.invalidateAll();
        DataNodeManager.dataCache.invalidateAll();
        ClassNodeManager.classCache.invalidateAll();
        GraphManager.edgeCache.invalidateAll();
        GraphManager.inversePatternCache.invalidateAll();
        GraphManager.patternCache.invalidateAll();
        DataRelationshipManager.relationshipCache.invalidateAll();
        ClassRelationshipCache.relationshipCache.invalidateAll();
        PatternRelationshipCache.relationshipCache.invalidateAll();

        GraphManager graphManager = new GraphManager("Pattern");
        GraphDatabaseService db = setUpDb();
        DecisionTree<Long> tree = new DecisionTree<>(0L, new HashMap<>(), db, graphManager);

        // Creates a hierarchical graph of branch factor 2 with depth 5
        createHierarchy(db, graphManager, null, 2, 5, 0, "[0-9]");

        //System.out.println(tree.traverseTo(1L).renderGraph());

        nodeManager.setNodeProperty(0L, "pattern",
                String.join(" ", Arrays.asList("The first person to go to space was smart".split(" "))
                        .subList(0, 1)), db);

        nodeManager.setNodeProperty(1L, "pattern",
                String.join(" ", Arrays.asList("The first person to go to space was smart".split(" "))
                        .subList(0, 2)), db);

        nodeManager.setNodeProperty(2L, "pattern",
                String.join(" ", Arrays.asList("The first person to go to space was smart".split(" "))
                        .subList(0, 3)), db);

        nodeManager.setNodeProperty(3L, "pattern",
                String.join(" ", Arrays.asList("The first person to go to space was smart".split(" "))
                        .subList(0, 4)), db);

        nodeManager.setNodeProperty(7L, "pattern",
                String.join(" ", Arrays.asList("The first person to go to space was smart".split(" "))
                        .subList(0, 5)), db);

        nodeManager.setNodeProperty(9L, "pattern",
                String.join(" ", Arrays.asList("The first person to go to space was smart".split(" "))
                        .subList(0, 6)), db);

        graphManager.updateCache(0L, db);
        graphManager.updateCache(1L, db);
        graphManager.updateCache(2L, db);
        graphManager.updateCache(3L, db);
        graphManager.updateCache(7L, db);
        graphManager.updateCache(9L, db);

        // Traverse by pattern
        System.out.println(tree.traverseByPattern("The first person to go to space was smart"));

        System.out.println(tree.renderGraph());
    }

    private void createHierarchy(GraphDatabaseService db, GraphManager graphManager, Node root, int breadth, int maxDepth, int layer, String pattern) {
        Node leaf = nodeManager.getOrCreateNode(graphManager, pattern, db);

        if(root != null) {
            LearningManager.mapBranchToLeaf(db, NodeManager.getNodeAsMap(root.getId(), db), graphManager, leaf);
        }

        if(layer < maxDepth) {
            for (int i = 0; i < breadth; i++) {
                createHierarchy(db, graphManager, leaf, breadth, maxDepth, layer + 1, leaf.getId() + "_" + i);
            }
        }
    }
}
