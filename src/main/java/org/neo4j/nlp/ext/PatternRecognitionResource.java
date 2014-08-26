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
import org.neo4j.nlp.impl.manager.NodeManager;
import org.neo4j.nlp.impl.util.LearningManager;
import org.neo4j.nlp.impl.util.VectorUtil;
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
@Path("/graphify")
public class PatternRecognitionResource {

    private static final ObjectMapper objectMapper  = new ObjectMapper();
    private static final GraphManager GRAPH_MANAGER  = new GraphManager("Pattern");

    public PatternRecognitionResource(@Context GraphDatabaseService db) {

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
            return Response.status(200).entity("{\"error\":\"" + Arrays.toString(e.getStackTrace()) + "\"}").build();
        }

            LabeledText labeledText = new LabeledText();
            ArrayList labels = (ArrayList)input.get("label");
            ArrayList texts = new ArrayList();
            if(input.get("text").getClass() == ArrayList.class) {
                texts = (ArrayList)input.get("text");
            }
            else
            {
               texts.add(input.get("text"));
            }

            labeledText.setLabel((String[])labels.toArray(new String[labels.size()]));
            labeledText.setText((String[])texts.toArray(new String[texts.size()]));

            if(input.containsKey("focus")) {
                labeledText.setFocus((int) input.get("focus"));
            }
            else
            {
                labeledText.setFocus(1);
            }

            // Add first matcher
            for (int i = 0; i < labeledText.getFocus(); i++) {
                Transaction tx = db.beginTx();
                getRootPatternNode(db);
                LearningManager.trainInput(Arrays.asList(labeledText.getText()), Arrays.asList(labeledText.getLabel()), GRAPH_MANAGER, db);
                tx.success();
                tx.close();
            }


            return Response.ok()
                    .entity("{\"success\":\"true\"}")
                    .type(MediaType.APPLICATION_JSON)
                    .build();

    }

    /**
     * Classify a body of text using the training model stored in the graph database.
     * @param body The JSON model that binds to the LabeledText class model.
     * @param db The Neo4j graph database service.
     * @return Returns a sorted list of classes ranked on probability.
     * @throws IOException
     */
    @POST
    @Path("/classify")
    @Produces(MediaType.APPLICATION_JSON)
    public Response classify(String body, @Context GraphDatabaseService db) throws IOException {
        HashMap<String, Object> input;
        try {

            input = objectMapper.readValue(body, HashMap.class);

            String text = null;

            if(input.containsKey("text")) {
                text = ((String) input.get("text"));
            }
            else
            {
                throw new Exception("Error parsing JSON");
            }

            // This method trains a model on a supplied label and text content

            String result = new Gson().toJson(VectorUtil.similarDocumentMapForVector(db, GRAPH_MANAGER, text));

            return Response.ok()
                    .entity(result)
                    .type(MediaType.APPLICATION_JSON)
                    .build();

        } catch (Exception e) {
            return Response.status(400).entity(String.format("{\"error\":\"%s %s\"}", e.toString(), Arrays.toString(e.getStackTrace()))).build();
        }
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

            String text = null;

            if(input.containsKey("text")) {
                text = ((String) input.get("text"));
            }
            else
            {
                throw new Exception("Error parsing JSON");
            }

            // This method trains a model on a supplied label and text content
            Node patternNode;

            int dataId;

            // Add first matcher
            try ( Transaction tx = db.beginTx() ) {
                patternNode = getRootPatternNode(db);
                dataId = GRAPH_MANAGER.handlePattern(patternNode, text, db, new String[] {"CLASSIFY"});
                tx.success();
            }

            Map<String, Object> params = new HashMap<>();

            params.put("id", dataId);

            String similarClass = executeCypher(db, getSimilarDataTemplate(), params);

            return Response.ok()
                    .entity(similarClass)
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        } catch (Exception e) {
            return Response.status(400).entity("{\"error\":\"Error parsing JSON.\"}").build();
        }
    }

    /**
     * Gets a Neo4j node entity that contains the root pattern for performing hierarchical pattern recognition from.
     * @param db The Neo4j graph database service.
     * @return Returns a Neo4j node entity that contains the root pattern for performing hierarchical pattern recognition from.
     */
    private Node getRootPatternNode(GraphDatabaseService db) {
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

        //String similarClass = executeCypher(db, getSimilarClass(), params);

        String similarClass = new Gson().toJson(VectorUtil.similarDocumentMapForClass(db, name));

        return Response.status( 200 )
                .entity(similarClass)
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/calculatesimilarity")
    public Response calculateSimilarity(@PathParam("name") String name, @Context GraphDatabaseService db) throws IOException {

        String result = "";

        result = new Gson().toJson(VectorUtil.getCosineSimilarityVector(db));

        return Response.ok()
                .entity(result)
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
                "MATCH (p1:Class { name: {name}})\n" +
                "MATCH (p1)<-[x:HAS_CLASS]-(pattern:Pattern),\n" +
                "      (pattern)-[y:HAS_CLASS]->(p2:Class)\n" +
                "WITH SUM(1.0 / (pattern.classes + 1.0)) as rating, p1, p2\n" +
                "ORDER BY rating DESC\n" +
                "RETURN p2.name as class, rating as weight LIMIT 100";
    }

    private static String getSimilarClassForFeatureVector() {
        return
                "MATCH (pattern:Pattern) WHERE id(pattern) in {id}\n" +
                "WITH pattern\n" +
                "MATCH (pattern)-[:HAS_CLASS]->(class:Class)\n" +
                "WHERE class.name <> 'CLASSIFY'\n" +
                "WITH SUM(1.0 / (pattern.classes + 1.0)) as rating, class\n" +
                "ORDER BY rating DESC\n" +
                "RETURN class.name as label, rating as weight LIMIT 100";
    }

    private static String getContentClassification() {
        return
                "MATCH (data:Data) WHERE id(data) = {id}\n" +
                "WITH data\n" +
                "MATCH (data)<-[:MATCHES]-(pattern:Pattern),\n" +
                "      (pattern)-[:HAS_CLASS]->(class:Class)\n" +
                "WHERE class.name <> 'CLASSIFY'\n" +
                "WITH SUM(1.0 / (pattern.classes + 1.0)) as rating, data, class\n" +
                "ORDER BY rating DESC\n" +
                "RETURN class.name as class, rating as weight LIMIT 100";
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
                "WHERE n <> data AND data.label <> 'CLASSIFY' AND coalesce(pattern.classes, 0) < 10\n" +
                "WITH data.label as label, data.value as data, count(pattern) as count\n" +
                "WHERE count > 2\n" +
                "RETURN data, label, count\n" +
                "ORDER BY count DESC\n" +
                "LIMIT 10";
    }
}
