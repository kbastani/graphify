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

    public PatternRecognitionResource(@Context GraphDatabaseService database )
    {

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
        ArrayList labels = (ArrayList) input.get("label");
        ArrayList texts = new ArrayList();
        if (input.get("text").getClass() == ArrayList.class) {
            texts = (ArrayList) input.get("text");
        } else {
            texts.add(input.get("text"));
        }

        for (int i = 0; i < texts.size(); i++) {
            texts.set(i, cleanText((String) texts.get(i)));
        }

        labeledText.setLabel((String[]) labels.toArray(new String[labels.size()]));
        labeledText.setText((String[]) texts.toArray(new String[texts.size()]));

        if (input.containsKey("focus")) {
            labeledText.setFocus((int) input.get("focus"));
        } else {
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

            String text;

            if(input.containsKey("text")) {
                text = ((String) input.get("text"));
            }
            else
            {
                throw new Exception("Error parsing JSON");
            }

            // This method trains a model on a supplied label and text content
            String result = new Gson().toJson(VectorUtil.similarDocumentMapForVector(db, GRAPH_MANAGER, cleanText(text)));

            return Response.ok()
                    .entity(result)
                    .type(MediaType.APPLICATION_JSON)
                    .build();

        } catch (Exception e) {
            return Response.status(400).entity(String.format("{\"error\":\"%s %s\"}", e.toString(), Arrays.toString(e.getStackTrace()))).build();
        }
    }

    @POST
    @Path("/extractfeatures")
    @Produces(MediaType.APPLICATION_JSON)
    public Response extract(String body, @Context GraphDatabaseService db) throws IOException {
        HashMap<String, Object> input;
        try {

            input = objectMapper.readValue(body, HashMap.class);

            String text;

            if(input.containsKey("text")) {
                text = ((String) input.get("text"));
            }
            else
            {
                throw new Exception("Error parsing JSON");
            }
            List<LinkedHashMap<String, Object>> phrases = VectorUtil.getPhrases(db, cleanText(text), GRAPH_MANAGER);


            return Response.ok()
                    .entity(new Gson().toJson(phrases))
                    .type(MediaType.APPLICATION_JSON)
                    .build();

        } catch (Exception e) {
            return Response.status(400).entity(String.format("{\"error\":\"%s %s\"}", e.toString(), Arrays.toString(e.getStackTrace()))).build();
        }
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

        String result;

        result = new Gson().toJson(VectorUtil.getCosineSimilarityVector(db));

        return Response.ok()
                .entity(result)
                .type(MediaType.APPLICATION_JSON)
                .build();
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

    private static String cleanText(String t) {
        t = t.replaceAll("(,|:|;)", " ");
        t = t.replaceAll("  ", " ");
        t = t.replaceAll("\\n", " ");
        t = t.replaceAll("([\\.]\\s)", " .1. .1. ");
        t = t.replaceAll("  ", " ");
        t = t.trim();

        return t;
    }
}