package org.neo4j.nlp.impl.util;

import junit.framework.Assert;
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
import org.neo4j.test.TestGraphDatabaseFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class VectorUtilTest
{

    private static GraphDatabaseService setUpDb()
    {
        return new TestGraphDatabaseFactory().newImpermanentDatabaseBuilder().newGraphDatabase();
    }

    final public static Charset ENCODING = StandardCharsets.UTF_8;

    final public static String negativeSentimentDirectory = "../examples/graphify-examples-sentiment-analysis/src/main/resources/txt_sentoken/neg";
    final public static String positiveSentimentDirectory = "../examples/graphify-examples-sentiment-analysis/src/main/resources/txt_sentoken/pos";


    @Test
    public void sentimentAnalysisTest() throws IOException {
        GraphDatabaseService db = setUpDb();
        GraphManager graphManager = new GraphManager("Pattern");

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
        VectorUtil.vectorSpaceModelCache.invalidateAll();

        // Train the model on 200 examples each of positive and negative reviews
        train(db, graphManager);

        // Test the model on the next 200 examples of positive and negative reviews
        Map<String, Double> errorMap = test(db, graphManager);

        // To ensure the validity of the classifier, assert success ratio is greater than 50%
        Assert.assertTrue(errorMap.get("positive") > .5 && errorMap.get("negative") > .5);

        System.out.println(errorMap);
    }

    final static Integer trainCount = 200;

    private static Map<String, Double> test(GraphDatabaseService db, GraphManager graphManager) throws IOException {
        Integer negativeError = 0;
        Integer positiveError = 0;

        List<String> negativeText = readLargerTextFile(negativeSentimentDirectory);
        List<String> positiveText = readLargerTextFile(positiveSentimentDirectory);

        for (int i = 1; i < trainCount * 2; i++) {
            if(i % 2 == 0)
            {
                negativeError += testOnText(negativeText.get(trainCount + (i / 2)), "negative", db, graphManager) ? 0 : 1;
            } else {
                positiveError += testOnText(positiveText.get(trainCount + (i / 2)), "positive", db, graphManager) ? 0 : 1;
            }
        }

        Map<String, Double> errorMap = new HashMap<>();

        errorMap.put("negative", 1.0 - (negativeError.doubleValue() / trainCount.doubleValue()));
        errorMap.put("positive", 1.0 - (positiveError.doubleValue() / trainCount.doubleValue()));

        // Return success ratio
        return errorMap;
    }

    private static void train(GraphDatabaseService db, GraphManager graphManager) throws IOException {
        List<String> negativeText = readLargerTextFile(negativeSentimentDirectory);
        List<String> positiveText = readLargerTextFile(positiveSentimentDirectory);

        for (int i = 0; i < trainCount * 2; i++) {
            if(i % 2 == 0)
            {
                trainOnText(new String[] { negativeText.get(i / 2) }, new String[]{"negative"}, db, graphManager);
            } else {
                trainOnText(new String[] { positiveText.get(i / 2) }, new String[]{"positive"}, db, graphManager);
            }
        }
    }

    private static void trainOnText(String[] text, String[] label, GraphDatabaseService db, GraphManager graphManager) {
        Transaction tx = db.beginTx();
        getRootPatternNode(db, graphManager);
        LearningManager.trainInput(Arrays.asList(text), Arrays.asList(label), graphManager, db);
        tx.success();
        tx.close();
    }

    private static Node getRootPatternNode(GraphDatabaseService db, GraphManager graphManager) {
        Node patternNode;
        patternNode = new NodeManager().getOrCreateNode(graphManager, GraphManager.ROOT_TEMPLATE, db);
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

    private static boolean testOnText(String text, String label, GraphDatabaseService db, GraphManager graphManager) {
        Map<String, List<LinkedHashMap<String, Object>>> hashMap = VectorUtil.similarDocumentMapForVector(db, graphManager, cleanText(text));

        // Validate guess
        ArrayList classes = (ArrayList) hashMap.get("classes");

        if (classes.size() > 0) {
            LinkedHashMap className = (LinkedHashMap) classes.stream().findFirst().get();

            return className.get("class").equals(label);
        }
        else
        {
            return false;
        }
    }

    public static List<String> readLargerTextFile(String aFileName) throws IOException {

        List<String> files = new ArrayList<>();
        File fileDir = new File(aFileName);

        for (final File fileEntry : fileDir.listFiles()) {
            StringBuilder sb = new StringBuilder();

            Path path = Paths.get(aFileName + "/" + fileEntry.getName());
            try (Scanner scanner = new Scanner(path, ENCODING.name())) {
                while (scanner.hasNextLine()) {
                    sb.append(scanner.nextLine()).append("\n");
                }
            }

            String result = sb.toString();
            files.add(result);
        }

        return files;
    }

}