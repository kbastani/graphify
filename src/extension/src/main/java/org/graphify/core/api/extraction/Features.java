package org.graphify.core.api.extraction;

/**
 * Copyright (C) 2014 Kenny Bastani
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

import org.graphify.core.kernel.helpers.GraphManager;
import org.graphify.core.kernel.impl.manager.NodeManager;
import org.graphify.core.kernel.impl.util.LearningManager;
import org.graphify.core.kernel.models.LabeledText;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;

import java.util.Arrays;

/**
 * Users will be able to extract features by ingesting text of any length through a REST API endpoint.
 */
public class Features {

    private static final GraphManager GRAPH_MANAGER  = new GraphManager("Pattern");

    public static void extractFeatures(GraphDatabaseService db, LabeledText labeledText) {
        // Add first matcher
        for (int i = 0; i < labeledText.getFocus(); i++) {
            Transaction tx = db.beginTx();
            getRootPatternNode(db);
            LearningManager.trainInput(Arrays.asList(labeledText.getText()), Arrays.asList(labeledText.getLabel()), GRAPH_MANAGER, db);
            tx.success();
            tx.close();
        }
    }

    /**
     * Gets a Neo4j node entity that contains the root pattern for performing hierarchical pattern recognition from.
     * @param db The Neo4j graph database service.
     * @return Returns a Neo4j node entity that contains the root pattern for performing hierarchical pattern recognition from.
     */
    private static Node getRootPatternNode(GraphDatabaseService db) {
        Node patternNode;
        patternNode = new NodeManager().getOrCreateNode(GRAPH_MANAGER, GraphManager.ROOT_TEMPLATE, db);
        if(!patternNode.hasProperty("matches")) {
            patternNode.setProperty("matches", 0);
            patternNode.setProperty("threshold", GraphManager.MIN_THRESHOLD);
            patternNode.setProperty("root", 1);
            patternNode.setProperty("phrase", "{0} {1}");
        }
        return patternNode;
    }
}
