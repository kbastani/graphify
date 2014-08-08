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

package org.neo4j.nlp.ext;


import com.google.gson.Gson;
import org.codehaus.jackson.map.ObjectMapper;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.nlp.helpers.GraphManager;
import org.neo4j.nlp.models.LabeledText;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.*;

/**
 * Exposes a set of methods from a Neo4j graph database used to train natural language parsing models for text
 * classification. The algorithms used in this unmanaged extension are based on original research by Kenny Bastani.
 * To understand more about how hierarchical pattern recognition is used to create a natural language parsing model,
 * read the blog post "Hierarchical Pattern Recognition" at bit.ly/1lMjSm5
 */
@Path("/pattern")
public class PatternRecognitionResource {

    static final ObjectMapper objectMapper  = new ObjectMapper();
    static final GraphManager GRAPH_MANAGER  = new GraphManager("Pattern", "pattern");
    static GraphDatabaseService graphDB;

    public PatternRecognitionResource(@Context GraphDatabaseService db) {
        graphDB = db;
    }

    /**
     * A REST API method that trains a natural language parse tree with a supplied text input and a
     * label that describes that text input.
     * @param body The JSON model that binds to the LabeledText class model.
     * @param db The Neo4j GraphDatabaseService that is the persistent data store for the natural language parsing model.
     * @return Returns a JSON response with a probability distribution of inferred classes that may describe the supplied text input based on the current training model represented as a hierarchical pattern recognition tree.
     * @throws IOException
     */
    @POST
    @Path("/training")
    @Produces(MediaType.APPLICATION_JSON)
    public Response training(String body, @Context GraphDatabaseService db) throws IOException {
        HashMap<String, Object> input;
        try {
            input = objectMapper.readValue(body, HashMap.class);
        } catch (Exception e) {
            return Response.status(400).entity("{\"error\":\"Error parsing JSON.\"}").build();
        }

        LabeledText labeledText = new LabeledText();
        labeledText.setLabel((String)input.get("label"));
        if(input.containsKey("label"))
            labeledText.setText((String)input.get("text"));
        if(input.containsKey("focus")) {
            labeledText.setFocus((int) input.get("focus"));
        }
        else
        {
            labeledText.setFocus(10);
        }

        // This method trains a model on a supplied label and text content
        Node patternNode;

        // Add first matcher
        try ( Transaction tx = db.beginTx() ) {
            patternNode = getRootPatternNode(db);
            for (int i = 0; i < labeledText.getFocus(); i++) {
                GRAPH_MANAGER.handlePattern(patternNode, labeledText.getText(), db, labeledText.getLabel());
            }
            tx.success();
        }

        Map<String, Object> params = new HashMap<>();

        params.put("name", labeledText.getLabel());

        String similarClass = executeCypher(db, getSimilarClass(), params);

        return Response.ok()
                .entity(similarClass)
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    /**
     * A REST API method that trains a natural language parse tree with a supplied text input and a
     * label that describes that text input.
     * @param body The JSON model that binds to the LabeledText class model.
     * @param db The Neo4j GraphDatabaseService that is the persistent data store for the natural language parsing model.
     * @return Returns a JSON response with a probability distribution of inferred classes that may describe the supplied text input based on the current training model represented as a hierarchical pattern recognition tree.
     * @throws IOException
     */
    @POST
    @Path("/related")
    @Produces(MediaType.APPLICATION_JSON)
    public Response related(String body, @Context GraphDatabaseService db) throws IOException {
        HashMap<String, String> input;
        try {
            input = objectMapper.readValue(body, HashMap.class);
        } catch (Exception e) {
            return Response.status(400).entity("{\"error\":\"Error parsing JSON.\"}").build();
        }

        LabeledText labeledText = new LabeledText();;
        labeledText.setText(input.get("text"));

        // This method trains a model on a supplied label and text content
        Node patternNode;

        int dataId;

        // Add first matcher
        try ( Transaction tx = db.beginTx() ) {
            patternNode = getRootPatternNode(db);
            dataId = GRAPH_MANAGER.handlePattern(patternNode, labeledText.getText(), db, "CLASSIFY");
            tx.success();
        }

        Map<String, Object> params = new HashMap<>();

        params.put("id", dataId);

        String similarClass = executeCypher(db, getSimilarDataTemplate(), params);

        return Response.ok()
                .entity(similarClass)
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    /**
     * Gets a Neo4j node entity that contains the root pattern for performing hierarchical pattern recognition from.
     * @param db The Neo4j graph database service.
     * @return Returns a Neo4j node entity that contains the root pattern for performing hierarchical pattern recognition from.
     */
    private Node getRootPatternNode(GraphDatabaseService db) {
        Node patternNode;
        patternNode = GRAPH_MANAGER.getOrCreateNode("(\\b[\\w']+\\b)\\s(\\b[\\w']+\\b)", db);
        if(!patternNode.hasProperty("matches")) {
            patternNode.setProperty("matches", 0);
            patternNode.setProperty("threshold", 5);
            patternNode.setProperty("root", 1);
        }
        return patternNode;
    }

    /**
     * Gets a probability distribution of labels that are related to the supplied label.
     * @param name The label name to find similar labels for.
     * @param db The Neo4j GraphDatabaseService that is the persistent data store for the natural language parsing model.
     * @return Returns a JSON model containing a probability distribution that describes related labels to the supplied label.
     * @throws IOException
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/similar/{name}")
    public Response getSimilarClasses(@PathParam("name") String name, @Context GraphDatabaseService db) throws IOException {
        Map<String, Object> params = new HashMap<>();

        params.put("name", name);

        String similarClass = executeCypher(db, getSimilarClass(), params);

        return Response.status( 200 )
                .entity(similarClass)
                .type(MediaType.APPLICATION_JSON)
                .build();
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

        try ( Transaction tx = db.beginTx() ) {
            result = engine.execute(cypher, params);
            tx.success();
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (Map<String,Object> row : result) {
            results.add(new LinkedHashMap<>(row));
        }

        return new Gson().toJson(results);
    }

    /**
     * A Cypher query template that is used to find similar labels to a supplied label name.
     * @return Returns a string of the Cypher query template for finding similar labels to a supplied label name.
     */
    private static String getSimilarClass() {
        return
                "MATCH (class:Class { name: {name} })\n" +
                "MATCH (class)<-[:HAS_CLASS]-(pattern:Pattern),\n" +
                "      (pattern)-[:HAS_CLASS]->(classes:Class)\n" +
                "WHERE classes.name <> 'CLASSIFY'\n" +
                "WITH class.name as class, classes.name as relatedTo, count(pattern) as patterns\n" +
                "WITH sum(patterns) as total\n" +
                "MATCH (class:Class { name: {name} })\n" +
                "MATCH (class)<-[:HAS_CLASS]-(pattern:Pattern),\n" +
                "      (pattern)-[:HAS_CLASS]->(classes:Class)\n" +
                "WHERE classes.name <> 'CLASSIFY'\n" +
                "RETURN classes.name as class, toFloat(toFloat(count(pattern)) / toFloat(total)) as weight\n" +
                "ORDER BY weight DESC\n" +
                "LIMIT 100";
    }

    /**
     * A Cypher query template that is used to find the most similar data to a supplied data ID.
     * @return Returns an ordered set of data that are similar to a supplied data ID.
     */
    private static String getSimilarDataTemplate() {
        return  "MATCH (n) WHERE id(n) = {id}\n" +
                "WITH n\n" +
                "MATCH (n)<-[:MATCHES]-(pattern:Pattern)\n" +
                "WITH pattern, n\n" +
                "MATCH (pattern)-[:MATCHES]->(data:Data)\n" +
                "WHERE n <> data AND data.label <> 'CLASSIFY'\n" +
                "WITH data.label as label, data.value as data, count(pattern) as count\n" +
                "WHERE count > 2\n" +
                "RETURN data, label, count\n" +
                "ORDER BY count DESC\n" +
                "LIMIT 10";
    }
}
