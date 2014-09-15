package org.neo4j.nlp.impl.util;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import org.codehaus.jackson.map.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Ignore;
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

    @Ignore
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
        String result = graphManager.GeneratePattern(new PatternCount("word", 2, null));

        Assert.assertEquals(GraphManager.ROOT_TEMPLATE.replace("\\s", "\\sword\\s"), result);
    }

    @Test
    public void testClassEquality() throws Exception {
        Object stringArray = new String[] { "test" };

        Assert.assertTrue(stringArray.getClass() == String[].class);
    }

    @Ignore
    @Test
    public void testLearningManager() throws Exception {
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

        try (Transaction tx = db.beginTx()) {
            rootPattern = (String) rootNode.getProperty("pattern");
            tx.success();
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
        List<Double> v1 = getFeatureVector(db, graphManager, rootPattern, input1);
        String input2 = "The tenth word in a paragraph is interesting";
        List<Double> v2 = getFeatureVector(db, graphManager, rootPattern, input2);

        double cosineSimilarity = VectorUtil.cosineSimilarity(v1, v2);

        System.out.println(cosineSimilarity);

        String input3 = "The tenth word in a paragraph is interesting";
        List<Double> v3 = getFeatureVector(db, graphManager, rootPattern, input3);

        System.out.println(VectorUtil.cosineSimilarity(v1, v3));

        String input4 = "Durr in durr a durr";
        List<Double> v4 = getFeatureVector(db, graphManager, rootPattern, input4);

        System.out.println(VectorUtil.cosineSimilarity(v1, v4));

        String input5 = "The sixth letter in a stanza is interesting";

        VectorUtil.vectorSpaceModelCache.invalidateAll();

        System.out.println(new Gson().toJson(VectorUtil.similarDocumentMapForVector(db, graphManager, input3)));
        System.out.println(new Gson().toJson(VectorUtil.similarDocumentMapForVector(db, graphManager, input1)));
        System.out.println(new Gson().toJson(VectorUtil.similarDocumentMapForClass(db, "paragraph")));

    }

    @Ignore
    @Test
    public void testBackwardsPropagation() throws Exception {

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

        String rootPattern;

        try (Transaction tx = db.beginTx()) {
            rootPattern = (String) rootNode.getProperty("pattern");
            tx.success();
        }

        String input = "The last word in a document is interesting";
        classifyInput(db, graphManager, rootPattern, input);
        input = "The nineteenth word in a sentence is interesting";
        classifyInput(db, graphManager, rootPattern, input);
        input = "The fiftieth word in a paragraph is interesting";
        classifyInput(db, graphManager, rootPattern, input);


    }

    private List<Double> getFeatureVector(GraphDatabaseService db, GraphManager graphManager, String rootPattern, String input) {
        Map<String, Object> params = new HashMap<>();
        Map<Long, Integer> patternMatchers = PatternMatcher.match(rootPattern, input, db, graphManager);
        String featureIndex = executeCypher(db, FEATURE_INDEX_QUERY, params);
        ObjectMapper objectMapper = new ObjectMapper();
        List<Integer> featureIndexList = new ArrayList<>();

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
        Collections.addAll(longs, patternMatchers.keySet().stream().map(Long::intValue).collect(Collectors.toList()).toArray(new Integer[longs.size()]));

        Integer sum = patternMatchers.values().stream().mapToInt(Integer::intValue).sum();

        return featureIndexList.stream().map(i -> longs.contains(i) ? (patternMatchers.get(i.longValue()).doubleValue() / sum.doubleValue()) : 0.0).collect(Collectors.toList());
    }

    private void classifyInput(GraphDatabaseService db, GraphManager graphManager, String rootPattern, String input) {
        Map<String, Object> params;
        Map<Long, Integer> patternMatchers = PatternMatcher.match(rootPattern, input, db, graphManager);
        List<Long> longs = new ArrayList<>();
        Collections.addAll(longs, patternMatchers.keySet().toArray(new Long[longs.size()]));
        params = new HashMap<>();
        params.put("id", longs);
        System.out.println(executeCypher(db, SIMILAR_CLASS_FOR_FEATURE_VECTOR, params));
    }

    private static final String FEATURE_INDEX_QUERY = "MATCH (pattern:Pattern) RETURN id(pattern) as index ORDER BY pattern.threshold DESC";
    private static final String SIMILAR_CLASS_FOR_FEATURE_VECTOR = "MATCH (pattern:Pattern) WHERE id(pattern) in {id}\n" +
            "WITH pattern\n" +
            "MATCH (pattern)-[:HAS_CLASS]->(class:Class)\n" +
            "WHERE class.name <> 'CLASSIFY'\n" +
            "WITH SUM(1.0 / (pattern.classes + 1.0)) as rating, class\n" +
            "ORDER BY rating DESC\n" +
            "RETURN class.name as label, rating";

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