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

package org.neo4j.nlp.impl.util;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.codehaus.jackson.map.ObjectMapper;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.nlp.helpers.GraphManager;

import java.util.*;

/**
 * This class traverses through data stored as nodes in Neo4j and finds similar patterns to an input
 * recursively in order to apply newly mined patterns that did not exist when a piece of data was
 * originally processed.
 */
public class PatternTraversal implements Runnable {
    private Thread t;
    private String threadName;
    private GraphManager graphManager;
    private Node patternNode;
    private GraphDatabaseService db;
    private List<Integer> sentenceHistory = new ArrayList<>();
    private boolean isRunning = false;
    private String className;

    /**
     * Instantiates a new instance of the PatternTraversal class.
     * @param sentence The input sentence of snippet of text that should be used as a seed for the traversal algorithm.
     * @param db The Neo4j graph database service.
     * @param graphManager A GraphManager class containing a cached representation of the pattern recognition tree.
     * @param patternNode A reference to a Neo4j node entity that represents an existing pattern in the recognition tree.
     * @param className The name of the label that describes the text content of the sentence sentence parameter.
     */
    public PatternTraversal( String sentence, GraphDatabaseService db, GraphManager graphManager, Node patternNode, String className){
        this.threadName = sentence;
        this.graphManager = graphManager;
        this.patternNode = patternNode;
        this.db = db;
        this.className = className;
    }

    /**
     * Whether or not the traversal algorithm is currently running on a separate thread.
     * @return Returns a boolean value that indicates whether or not the traversal algorithm is currently running on a separate thread.
     */
    public boolean getIsRunning()
    {
        return isRunning;
    }

    /**
     * This method will begin the pattern traversal algorithm if it is not already running.
     */
    public void run() {
        isRunning = true;
        try {
            while(isRunning) {
                // Find similar sentence
                int sentenceId = graphManager.handlePattern(patternNode, threadName, db, className);

                Map<String, Object> params = new HashMap<>();
                params.put("id", sentenceId);
                params.put("time", System.currentTimeMillis() / 1000L);

                String cypherJson = executeCypher(db, getSimilarSentenceTemplate(), params);
                JsonParser parser = new JsonParser();
                JsonElement o = (JsonElement) parser.parse(cypherJson);

                if (o.getAsJsonArray().size() == 0 || Collections.frequency(sentenceHistory, sentenceId) > 1) {
                    isRunning = false;
                } else {
                    sentenceId = o.getAsJsonArray().get(0).getAsJsonObject().get("sentenceId").getAsInt();
                    threadName = o.getAsJsonArray().get(0).getAsJsonObject().get("data").getAsString();
                    className = o.getAsJsonArray().get(0).getAsJsonObject().get("label").getAsString();
                    sentenceHistory.add(sentenceId);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Clear history
        sentenceHistory = new ArrayList<Integer>();
    }

    /**
     * Executes a cypher query on the GraphDatabaseService from Neo4j.
     * @param db The Neo4j graph database service.
     * @param cypher The Cypher query template.
     * @param params The parameter map to be used with the Cypher query template.
     * @return Returns a JSON string of the results returned from Neo4j.
     */
    private static String executeCypher(GraphDatabaseService db, String cypher, Map<String, Object> params) {
        org.neo4j.cypher.javacompat.ExecutionEngine engine;
        engine = new org.neo4j.cypher.javacompat.ExecutionEngine(db);

        org.neo4j.cypher.javacompat.ExecutionResult result;

        try ( Transaction tx = db.beginTx(); ) {
            result = engine.execute(cypher, params);
            tx.success();
        }

        ObjectMapper objectMapper = new ObjectMapper();

        List<Map> results = new ArrayList<>();
        for (Map<String,Object> row : result) {
            results.add(new LinkedHashMap(row));
        }
        String cypherJsonResult = new Gson().toJson(results);

        return cypherJsonResult;
    }

    /**
     * Gets a Neo4j Cypher query template for getting a similar sentence based on the number
     * of shared patterns that are bound to pieces of data.
     * @return Returns a Cypher query template for getting a similar sentence.
     */
    private static String getSimilarSentenceTemplate() {
        return  "MATCH (n) WHERE id(n) = {id}\n" +
                "WITH n\n" +
                "MATCH (n)<-[:MATCHES]-(pattern:Pattern)\n" +
                "WITH pattern, n\n" +
                "MATCH (pattern)-[:MATCHES]->(data:Data)\n" +
                "WHERE n <> data AND ({time} - data.time) > 10\n" +
                "WITH id(data) as sentenceId, data.label as label, data.value as data, count(pattern) as count, data.time as time\n" +
                "WHERE count > 2 \n" +
                "WITH time, sentenceId, data, label, count\n" +
                "ORDER BY count DESC\n" +
                "LIMIT 10\n" +
                "RETURN time, sentenceId, data, label, count\n" +
                "ORDER BY time\n" +
                "LIMIT 1";
    }

    /**
     * Starts the pattern traversal algorithm on a separate thread.
     * @param sentence The input sentence of snippet of text that should be used as a seed for the traversal algorithm.
     */
    public void start (String sentence)
    {
        threadName = sentence;
        t = new Thread (this, threadName);
        t.start ();
    }

    /**
     * Stops the pattern traversal algorithm that is currently operating on a separate thread.
     */
    public void stop ()
    {
        isRunning = false;
    }

}