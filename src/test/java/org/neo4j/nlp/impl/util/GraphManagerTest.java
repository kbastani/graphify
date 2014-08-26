package org.neo4j.nlp.impl.util;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import org.codehaus.jackson.map.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.nlp.helpers.GraphManager;
import org.neo4j.nlp.impl.cache.ClassRelationshipCache;
import org.neo4j.nlp.impl.cache.PatternRelationshipCache;
import org.neo4j.nlp.impl.manager.ClassNodeManager;
import org.neo4j.nlp.impl.manager.DataNodeManager;
import org.neo4j.nlp.impl.manager.DataRelationshipManager;
import org.neo4j.nlp.impl.manager.NodeManager;
import org.neo4j.nlp.models.PatternCount;
import org.neo4j.test.TestGraphDatabaseFactory;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class GraphManagerTest {

    @Test
    public void testCypherJsonResult() throws Exception {

        GraphDatabaseService db;
        db = setUpDb();

        Map<String, Object> params = new HashMap<>();

        params.put("name", "Information theory");

        String similarClass = executeCypher(db, getSimilarClass(), params);

        Assert.assertEquals("[]", similarClass);

    }

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

    private static String getSimilarClass() {
        return  "MATCH (class:Class { name: {name} })\n" +
                "MATCH (class)<-[:HAS_CLASS]-(pattern:Pattern),\n" +
                "      (pattern)-[:HAS_CLASS]->(classes:Class)\n" +
                "RETURN class.name as class, classes.name as relatedTo, count(pattern) as patterns\n" +
                "ORDER BY patterns DESC\n" +
                "LIMIT 10";
    }

    @Test
    public void testGetTemplate() throws Exception {
        @NotNull
        GraphManager graphManager = new GraphManager("pattern");
        System.out.println(graphManager.GetTemplate(GraphManager.ROOT_TEMPLATE.replace("\\s", "\\sis\\sknown\\s")));
    }

    private static GraphDatabaseService setUpDb()
    {
        return new TestGraphDatabaseFactory().newImpermanentDatabaseBuilder().newGraphDatabase();
    }

    @Test
    public void testGeneratePattern() throws Exception {
        GraphManager graphManager = new GraphManager("Pattern");
        String result = graphManager.GeneratePattern(0, new PatternCount("word", 2, null), GraphManager.ROOT_TEMPLATE);

        Assert.assertEquals(GraphManager.ROOT_TEMPLATE.replace("\\s", "\\sword\\s"), result);
    }

    @Test
    public void testClassEquality() throws Exception {
        Object stringArray = new String[] { "test" };

        Assert.assertTrue(stringArray.getClass() == String[].class);
    }

    @Test
    public void testLearningManager() throws Exception {
        // Invalidate all caches
        NodeManager.globalNodeCache.invalidateAll();
        DataNodeManager.dataCache.invalidateAll();
        ClassNodeManager.classCache.invalidateAll();
        GraphManager.edgeCache.invalidateAll();
        GraphManager.inversePatternCache.invalidateAll();
        GraphManager.patternCache.invalidateAll();;
        DataRelationshipManager.relationshipCache.invalidateAll();
        ClassRelationshipCache.relationshipCache.invalidateAll();
        PatternRelationshipCache.relationshipCache.invalidateAll();

        GraphDatabaseService db = setUpDb();
        GraphManager graphManager = new GraphManager("Pattern");
        Node rootNode = getRootPatternNode(db, graphManager);

        Map<String, String> text = new HashMap<>();
        text.put("The first word in a sentence is interesting", "sentence");
        text.put("The second word in a sentence is interesting", "sentence");
        text.put("The third word in a sentence is interesting", "sentence");
        text.put("The fourth word in a paragraph is interesting", "paragraph");
        text.put("The fifth word in a sentence is interesting", "sentence");
        text.put("The sixth word in a paragraph is interesting", "paragraph");
        text.put("The seventh word in a sentence is interesting", "sentence");
        text.put("The eighth word in a document is interesting", "document");
        text.put("The ninth word in a sentence is interesting", "sentence");
        text.put("The tenth word in a paragraph is interesting", "paragraph");
        text.put("The eleventh word in a sentence is interesting", "sentence");
        text.put("The twelfth word in a paragraph is interesting", "paragraph");
        text.put("The thirteenth word in a sentence is interesting", "sentence");
        text.put("The fourteenth word in a document is interesting", "document");
        text.put("The fifteenth word in a sentence is interesting", "sentence");
        text.put("The sixteenth word in a paragraph is interesting", "paragraph");
        text.put("The seventeenth word in a sentence is interesting", "sentence");
        text.put("The nineteenth word in a document is interesting", "document");
        text.put("The twentieth word in a sentence is interesting", "sentence");
        text.put("The twenty-first word in a paragraph is interesting", "paragraph");
        text.put("The twenty-second word in a sentence is interesting", "sentence");
        text.put("The twenty-third word in a document is interesting", "document");
        text.put("The twenty-fourth word in a document is interesting", "document");
        text.put("The twenty-fifth word in a document is interesting", "document");
        text.put("The twenty-sixth word in a document is interesting", "document");
        text.put("The first word in a sentence is interesting", "sentence");
        text.put("The second word in a sentence is interesting", "sentence");
        text.put("The third word in a sentence is interesting", "sentence");
        text.put("The fourth word in a paragraph is interesting", "paragraph");
        text.put("The fifth word in a sentence is interesting", "sentence");
        text.put("The sixth word in a paragraph is interesting", "paragraph");
        text.put("The seventh word in a sentence is interesting", "sentence");
        text.put("The eighth word in a document is interesting", "document");
        text.put("The ninth word in a sentence is interesting", "sentence");
        text.put("The tenth word in a paragraph is interesting", "paragraph");
        text.put("The eleventh word in a sentence is interesting", "sentence");
        text.put("The twelfth word in a paragraph is interesting", "paragraph");
        text.put("The thirteenth word in a sentence is interesting", "sentence");
        text.put("The fourteenth word in a document is interesting", "document");
        text.put("The fifteenth word in a sentence is interesting", "sentence");
        text.put("The sixteenth word in a paragraph is interesting", "paragraph");
        text.put("The seventeenth word in a sentence is interesting", "sentence");
        text.put("The nineteenth word in a document is interesting", "document");
        text.put("The twentieth word in a sentence is interesting", "sentence");
        text.put("The twenty-first word in a paragraph is interesting", "paragraph");
        text.put("The twenty-second word in a sentence is interesting", "sentence");
        text.put("The twenty-third word in a document is interesting", "document");
        text.put("The twenty-fourth word in a document is interesting", "document");
        text.put("The twenty-fifth word in a document is interesting", "document");
        text.put("The twenty-sixth word in a document is interesting", "document");
        text.put("The first word in a sentence is interesting", "sentence");
        text.put("The second word in a sentence is interesting", "sentence");
        text.put("The third word in a sentence is interesting", "sentence");
        text.put("The fourth word in a paragraph is interesting", "paragraph");
        text.put("The fifth word in a sentence is interesting", "sentence");
        text.put("The sixth word in a paragraph is interesting", "paragraph");
        text.put("The seventh word in a sentence is interesting", "sentence");
        text.put("The eighth word in a document is interesting", "document");
        text.put("The ninth word in a sentence is interesting", "sentence");
        text.put("The tenth word in a paragraph is interesting", "paragraph");
        text.put("The eleventh word in a sentence is interesting", "sentence");
        text.put("The twelfth word in a paragraph is interesting", "paragraph");
        text.put("The thirteenth word in a sentence is interesting", "sentence");
        text.put("The fourteenth word in a document is interesting", "document");
        text.put("The fifteenth word in a sentence is interesting", "sentence");
        text.put("The sixteenth word in a paragraph is interesting", "paragraph");
        text.put("The seventeenth word in a sentence is interesting", "sentence");
        text.put("The nineteenth word in a document is interesting", "document");
        text.put("The twentieth word in a sentence is interesting", "sentence");
        text.put("The twenty-first word in a paragraph is interesting", "paragraph");
        text.put("The twenty-second word in a sentence is interesting", "sentence");
        text.put("The twenty-third word in a document is interesting", "document");
        text.put("The twenty-fourth word in a document is interesting", "document");
        text.put("The twenty-fifth word in a document is interesting", "document");
        text.put("The twenty-sixth word in a document is interesting", "document");
        text.put("The first word in a sentence is interesting", "sentence");
        text.put("The second word in a sentence is interesting", "sentence");
        text.put("The third word in a sentence is interesting", "sentence");
        text.put("The fourth word in a paragraph is interesting", "paragraph");
        text.put("The fifth word in a sentence is interesting", "sentence");
        text.put("The sixth word in a paragraph is interesting", "paragraph");
        text.put("The seventh word in a sentence is interesting", "sentence");
        text.put("The eighth word in a document is interesting", "document");
        text.put("The ninth word in a sentence is interesting", "sentence");
        text.put("The tenth word in a paragraph is interesting", "paragraph");
        text.put("The eleventh word in a sentence is interesting", "sentence");
        text.put("The twelfth word in a paragraph is interesting", "paragraph");
        text.put("The thirteenth word in a sentence is interesting", "sentence");
        text.put("The fourteenth word in a document is interesting", "document");
        text.put("The fifteenth word in a sentence is interesting", "sentence");
        text.put("The sixteenth word in a paragraph is interesting", "paragraph");
        text.put("The seventeenth word in a sentence is interesting", "sentence");
        text.put("The nineteenth word in a document is interesting", "document");
        text.put("The twentieth word in a sentence is interesting", "sentence");
        text.put("The twenty-first word in a paragraph is interesting", "paragraph");
        text.put("The twenty-second word in a sentence is interesting", "sentence");
        text.put("The twenty-third word in a document is interesting", "document");
        text.put("The twenty-fourth word in a document is interesting", "document");
        text.put("The twenty-fifth word in a document is interesting", "document");
        text.put("The twenty-sixth word in a document is interesting", "document");
        text.put("The twenty-third note in a ensemble is musical", "ensemble");
        text.put("The twenty-fourth note in a ensemble is musical", "ensemble");
        text.put("The twenty-fifth note in a ensemble is musical", "ensemble");
        text.put("The twenty-sixth note in a ensemble is musical", "ensemble");
        text.put("The first note in a ensemble is musical", "ensemble");
        text.put("The second note in a ensemble is musical", "ensemble");
        text.put("The third note in a ensemble is musical", "ensemble");
        text.put("The fourth note in a ensemble is musical", "ensemble");
        text.put("The fifth note in a ensemble is musical", "ensemble");
        text.put("The sixth note in a ensemble is musical", "ensemble");
        text.put("The seventh note in a ensemble is musical", "ensemble");
        text.put("The ninth note in a ensemble is musical", "ensemble");

        for (String str : text.keySet())
        {
            LearningManager.trainInput(Lists.asList(str, new String[0]), Lists.asList(text.get(str), new String[0]), graphManager, db);
        }

        String rootPattern;

        Transaction tx = db.beginTx();
        try {
            rootPattern = (String)rootNode.getProperty("pattern");
            tx.success();
        }
        finally {
            tx.close();
        }

        String input = "The fiftieth word in a document is interesting";
        classifyInput(db, graphManager, rootPattern, input);
        input = "The fiftieth word in a sentence is interesting";
        classifyInput(db, graphManager, rootPattern, input);
        input = "The fiftieth word in a paragraph is interesting";
        classifyInput(db, graphManager, rootPattern, input);

        // Output a feature vector on a new input from this point in the model development.
        // Feature vectors are a single dimension of values, 0 for non-match or non-zero for match.
        // When the value of an index in the vector is 1, the feature at that index was recognized in an input.

        input = "The last word in a sentence is interesting";
        System.out.println(getFeatureVector(db, graphManager, rootPattern, input));

        String input1 = "The last word in a sentence is interesting";
        List<Integer> v1 = getFeatureVector(db, graphManager, rootPattern, input1);
        String input2 = "The tenth word in a paragraph is interesting";
        List<Integer> v2 = getFeatureVector(db, graphManager, rootPattern, input2);

        double cosineSimilarity = VectorUtil.cosineSimilarity(v1, v2);

        System.out.println(cosineSimilarity);

        String input3 = "The tenth word in a paragraph is interesting";
        List<Integer> v3 = getFeatureVector(db, graphManager, rootPattern, input3);

        System.out.println(VectorUtil.cosineSimilarity(v1, v3));

        String input4 = "The fifth word in a document is interesting";
        List<Integer> v4 = getFeatureVector(db, graphManager, rootPattern, input4);

        System.out.println(VectorUtil.cosineSimilarity(v1, v4));

        String input5 = "The sixth letter in a stanza is interesting";
        List<Integer> v5 = getFeatureVector(db, graphManager, rootPattern, input5);

        //System.out.println(VectorUtil.cosineSimilarity(v1, v5));

        //System.out.println(VectorUtil.similarDocumentMap(db));

        //System.out.println(VectorUtil.updateSimilarityRelationships(db));

        VectorUtil.vectorSpaceModelCache.invalidateAll();

        //System.out.println(new Gson().toJson(VectorUtil.similarDocumentMapForClass(db, "document")));

        System.out.println(new Gson().toJson(VectorUtil.similarDocumentMapForVector(db, graphManager, input5)));

//        Map<String, Object> params = new HashMap<>();
//        System.out.println(CypherUtil.executeCypher(db, "MATCH (class:Class)-[r:RELATED_TO]->(classes:Class) RETURN class.name as class, classes.name as relatedTo, r.similarity as similarity ORDER BY similarity DESC", params));
    }

    @Test
    public void testBackwardsPropagation() throws Exception {

        // Invalidate all caches
        NodeManager.globalNodeCache.invalidateAll();
        DataNodeManager.dataCache.invalidateAll();
        ClassNodeManager.classCache.invalidateAll();
        GraphManager.edgeCache.invalidateAll();
        GraphManager.inversePatternCache.invalidateAll();
        GraphManager.patternCache.invalidateAll();;
        DataRelationshipManager.relationshipCache.invalidateAll();
        ClassRelationshipCache.relationshipCache.invalidateAll();
        PatternRelationshipCache.relationshipCache.invalidateAll();

        GraphDatabaseService db = setUpDb();
        GraphManager graphManager = new GraphManager("Pattern");
        Node rootNode = getRootPatternNode(db, graphManager);

        Map<String, String> text = new HashMap<>();
        text.put("The first word in a sentence is interesting", "sentence");
        text.put("The second word in a sentence is interesting", "sentence");
        text.put("The third word in a sentence is interesting", "sentence");
        text.put("The fourth word in a paragraph is interesting", "paragraph");
        text.put("The fifth word in a sentence is interesting", "sentence");
        text.put("The sixth word in a paragraph is interesting", "paragraph");
        text.put("The seventh word in a sentence is interesting", "sentence");
        text.put("The eighth word in a document is interesting", "document");
        text.put("The ninth word in a sentence is interesting", "sentence");
        text.put("The tenth word in a paragraph is interesting", "paragraph");
        text.put("The eleventh word in a sentence is interesting", "sentence");
        text.put("The twelfth word in a paragraph is interesting", "paragraph");
        text.put("The thirteenth word in a sentence is interesting", "sentence");
        text.put("The fourteenth word in a document is interesting", "document");
        text.put("The fifteenth word in a sentence is interesting", "sentence");
        text.put("The sixteenth word in a paragraph is interesting", "paragraph");
        text.put("The seventeenth word in a sentence is interesting", "sentence");
        text.put("The nineteenth word in a document is interesting", "document");
        text.put("The twentieth word in a sentence is interesting", "sentence");
        text.put("The twenty-first word in a paragraph is interesting", "paragraph");
        text.put("The twenty-second word in a sentence is interesting", "sentence");
        text.put("The twenty-third word in a document is interesting", "document");
        text.put("The twenty-fourth word in a document is interesting", "document");
        text.put("The twenty-fifth word in a document is interesting", "document");
        text.put("The twenty-sixth word in a document is interesting", "document");

        for (String str : text.keySet())
        {
            graphManager.handlePattern(rootNode, str, db, new String[] { text.get(str) });
        }

        // Test new sentences
        int document = graphManager.handlePattern(rootNode, "The fiftieth word in a document is interesting", db, new String[] { "CLASSIFY" });
        int sentence = graphManager.handlePattern(rootNode, "The last word in a sentence is interesting", db, new String[] { "CLASSIFY" });
        int paragraph = graphManager.handlePattern(rootNode, "The longest word in a paragraph is interesting", db, new String[] { "CLASSIFY" });

        Map<String, Object> params = new HashMap<>();

        // Infer on text related to document
        params.put("id", document);
        String documentResult = executeCypher(db, getSimilarClassForText(), params);
        Assert.assertEquals("Infer on document: ", documentResult, "[{\"label\":\"document\"}]");

        // Infer on text related to sentence
        params.put("id", sentence);
        String sentenceResult = executeCypher(db, getSimilarClassForText(), params);
        Assert.assertEquals("Infer on sentence: ", sentenceResult, "[{\"label\":\"sentence\"}]");

        // Infer on text related to paragraph
        params.put("id", paragraph);
        String paragraphResult = executeCypher(db, getSimilarClassForText(), params);
        Assert.assertEquals("Infer on paragraph: ", paragraphResult, "[{\"label\":\"paragraph\"}]");

        String rootPattern;

        Transaction tx = db.beginTx();
        try {
            rootPattern = (String)rootNode.getProperty("pattern");
            tx.success();
        }
        finally {
            tx.close();
        }

        String input = "The last word in a document is interesting";
        classifyInput(db, graphManager, rootPattern, input);
        input = "The nineteenth word in a sentence is interesting";
        classifyInput(db, graphManager, rootPattern, input);
        input = "The fiftieth word in a paragraph is interesting";
        classifyInput(db, graphManager, rootPattern, input);


    }

    private List<Integer> getFeatureVector(GraphDatabaseService db, GraphManager graphManager, String rootPattern, String input) {
        Map<String, Object> params = new HashMap<>();
        Map<Long, Integer> patternMatchers = PatternMatcher.match(rootPattern, input, db, graphManager);
        String featureIndex = executeCypher(db, getFeatureIndex(), params);
        ObjectMapper objectMapper = new ObjectMapper();
        List<Integer> featureIndexList = new ArrayList<>();

        //System.out.println(executeCypher(db, getFeatureIndexDebug(), params));


        try {
            ArrayList<LinkedHashMap<?, ?>> results;
            results = objectMapper.readValue(featureIndex, ArrayList.class);
            featureIndexList = results.stream()
                    .map(a -> (Integer)a.get("index"))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
        }

        List<Integer> longs = new ArrayList<>();
        Collections.addAll(longs, patternMatchers.keySet().stream().map(n -> n.intValue()).collect(Collectors.toList()).toArray(new Integer[longs.size()]));

        //System.out.println(longs);
        //System.out.println(featureIndexList);

        return featureIndexList.stream().map(i -> longs.contains(i) ? 1 : 0).collect(Collectors.toList());
    }

    private void classifyInput(GraphDatabaseService db, GraphManager graphManager, String rootPattern, String input) {
        Map<String, Object> params;
        Map<Long, Integer> patternMatchers = PatternMatcher.match(rootPattern, input, db, graphManager);
        List<Long> longs = new ArrayList<>();
        Collections.addAll(longs, patternMatchers.keySet().toArray(new Long[longs.size()]));
        params = new HashMap<>();
        params.put("id", longs);
        System.out.println(executeCypher(db, getSimilarClassForFeatureVector(), params));
    }

    private static String getFeatureIndex() {
        return
                "MATCH (pattern:Pattern) RETURN id(pattern) as index ORDER BY pattern.threshold DESC";
    }

    private static String getFeatureIndexDebug() {
        return
                "MATCH (pattern:Pattern) WITH pattern, { index: id(pattern), feature: pattern.phrase } as feature ORDER BY pattern.threshold DESC RETURN collect(feature) as features";
    }

    private static String getSimilarClassForFeatureVector() {
        return
                "MATCH (pattern:Pattern) WHERE id(pattern) in {id}\n" +
                "WITH pattern\n" +
                "MATCH (pattern)-[:HAS_CLASS]->(class:Class)\n" +
                "WHERE class.name <> 'CLASSIFY'\n" +
                "WITH SUM(1.0 / (pattern.classes + 1.0)) as rating, class\n" +
                "ORDER BY rating DESC\n" +
                "RETURN class.name as label, rating";
    }

    /**
     * A Cypher query template that is used to find similar labels to a supplied label name.
     * @return Returns a string of the Cypher query template for finding similar labels to a supplied label name.
     */
    private static String getSimilarClassForText() {
        return
            "MATCH (data:Data) WHERE id(data) = {id}\n" +
            "WITH data\n" +
            "MATCH (data)<-[:MATCHES]-(pattern:Pattern),\n" +
            "      (pattern)-[:HAS_CLASS]->(class:Class)\n" +
            "WHERE class.name <> 'CLASSIFY'\n" +
            "WITH SUM(1.0 / (pattern.classes + 1.0)) as rating, data, class\n" +
            "ORDER BY rating DESC\n" +
            "RETURN class.name as label LIMIT 1";
    }

    /**
     * Gets a Neo4j node entity that contains the root pattern for performing hierarchical pattern recognition from.
     * @param db The Neo4j graph database service.
     * @return Returns a Neo4j node entity that contains the root pattern for performing hierarchical pattern recognition from.
     */
    private Node getRootPatternNode(GraphDatabaseService db, GraphManager graphManager) {
        Node patternNode;
        patternNode = graphManager.getOrCreateNode(GraphManager.ROOT_TEMPLATE, db);
        try(Transaction tx = db.beginTx()) {
            if (!patternNode.hasProperty("matches")) {
                patternNode.setProperty("matches", 0);
                patternNode.setProperty("threshold", GraphManager.MIN_THRESHOLD);
                patternNode.setProperty("root", 1);
                patternNode.setProperty("phrase", "{0} {1}");
            }
            tx.success();
        }
        return patternNode;
    }
}