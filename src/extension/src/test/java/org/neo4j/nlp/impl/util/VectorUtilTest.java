package org.neo4j.nlp.impl.util;

import org.apache.commons.lang.ArrayUtils;
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
import org.neo4j.test.TestGraphDatabaseFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class VectorUtilTest
{
    final public static int TEST_COUNT = 100;
    final public static int TRAIN_COUNT = 100;
    final public static int SAMPLE_SIZE = 1000;

    final public static Charset ENCODING = StandardCharsets.UTF_8;

    final public static String negativeSentimentDirectory = "../examples/graphify-examples-sentiment-analysis/src/main/resources/txt_sentoken/neg";
    final public static String positiveSentimentDirectory = "../examples/graphify-examples-sentiment-analysis/src/main/resources/txt_sentoken/pos";

    final private static Stack<Integer> NEGATIVE_RANDOMIZED_INDEX = getRandomizedIndex(0, SAMPLE_SIZE);
    final private static Stack<Integer> POSITIVE_RANDOMIZED_INDEX = getRandomizedIndex(0, SAMPLE_SIZE);

    private static GraphDatabaseService setUpDb()
    {
        return new TestGraphDatabaseFactory().newImpermanentDatabaseBuilder().newGraphDatabase();
    }

    @Ignore
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

        // Train the model on examples each of positive and negative reviews
        train(db, graphManager);

        // Create a matrix of training data
        List<List<Integer>> trainingSet = new ArrayList<>();
        List<Integer> target = new ArrayList<>();
        // Iterate back over the training data
        List<String> negativeText = readLargerTextFile(negativeSentimentDirectory);
        List<String> positiveText = readLargerTextFile(positiveSentimentDirectory);

        for (int i = 0; i < TRAIN_COUNT; i++) {
            List<Integer> row = VectorUtil.getFeatureVectorForInput(db, graphManager, negativeText.get(i));
            System.out.println(row);
            trainingSet.add(row);
            target.add(0);
        }

        for (int i = 0; i < TRAIN_COUNT; i++) {
            List<Integer> row = VectorUtil.getFeatureVectorForInput(db, graphManager, positiveText.get(i));
            System.out.println(row);
            trainingSet.add(row);
            target.add(1);
        }

        // Write results to text files
        FileWriter fw = new FileWriter("../examples/graphify-examples-sentiment-analysis/src/main/resources/training/X_train.txt");


        trainingSet.forEach(a -> {
            try {
                fw.write(a.stream().map(Object::toString).collect(Collectors.toList())
                        .stream()
                        .reduce((x, y) -> x + " " + y).get() + "\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        fw.flush();
        fw.close();

        FileWriter fw2 = new FileWriter("../examples/graphify-examples-sentiment-analysis/src/main/resources/training/y_train.txt");


        fw2.write(target.stream().map(Object::toString).collect(Collectors.toList())
                .stream()
                .reduce((x, y) -> x + " " + y).get() + "\n");

        fw2.flush();
        fw2.close();
    }

    @Test
    public void binaryClassificationTest() throws IOException {
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

        // Train the model on examples each of positive and negative reviews
        train(db, graphManager);

        // Create a matrix of training data
        List<List<Double>> trainingSet = new ArrayList<>();
        List<Integer> target = new ArrayList<>();
        // Iterate back over the training data
        List<String> negativeText = readLargerTextFile(negativeSentimentDirectory);
        List<String> positiveText = readLargerTextFile(positiveSentimentDirectory);

        for (int i = 0; i < TEST_COUNT; i++) {
            List<Double> row = VectorUtil.getWeightedVectorForClass("negative", VectorUtil.getFeatureVectorForInput(db, graphManager, negativeText.get(i)).stream().map(a -> a.intValue()).collect(Collectors.toList()), db);
            trainingSet.add(row);
            target.add(0);
        }

        for (int i = 0; i < TEST_COUNT; i++) {
            List<Double> row = VectorUtil.getWeightedVectorForClass("positive", VectorUtil.getFeatureVectorForInput(db, graphManager, positiveText.get(i)).stream().map(a -> a.intValue()).collect(Collectors.toList()), db);
            trainingSet.add(row);
            target.add(1);
        }

        // Write results to text files
        FileWriter fw = new FileWriter("../examples/graphify-examples-sentiment-analysis/src/main/resources/training/X_train.txt");

        final int[] counter = {0};
        trainingSet.forEach(a -> {
            try {
                Integer result = target.get(counter[0]);
                fw.write(result + ",");
                fw.write(a.stream().map(Object::toString).collect(Collectors.toList())
                        .stream()
                        .reduce((x, y) -> x + " " + y).get() + "\n");
                counter[0]++;
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        fw.flush();
        fw.close();

        // Test features as logistic regression classifier
        ClassifierUtil.trainLogisticRegressionModel("../examples/graphify-examples-sentiment-analysis/src/main/resources/training/X_train.txt");
    }

    private static Stack<Integer> getRandomizedIndex(int lowerBound, int upperBound)
    {
        List<Integer> randomIndex = new ArrayList<>();

        Collections.addAll(randomIndex, ArrayUtils.toObject(IntStream.range(lowerBound, upperBound).toArray()));

        randomIndex.sort((a, b) -> new Random().nextInt(2) == 0 ? -1 : 1 );

        Stack<Integer> integerStack = new Stack<>();

        integerStack.addAll(randomIndex);

        return integerStack;
    }

    private static Map<String, Double> test(GraphDatabaseService db, GraphManager graphManager, Integer testCount) throws IOException {
        Integer negativeError = 0;
        Integer positiveError = 0;
        Integer totalCount = 0;
        Integer negativeStep = 0;
        Integer positiveStep = 0;
        Integer allStep = 0;

        List<String> negativeText = readLargerTextFile(negativeSentimentDirectory);
        List<String> positiveText = readLargerTextFile(positiveSentimentDirectory);

        List<String> positiveErrorText = new ArrayList<>();
        List<String> negativeErrorText = new ArrayList<>();

        Map<String, Double> errorMap = new HashMap<>();

        DecimalFormat df = new DecimalFormat("#.##");


        for (int i = 1; i < testCount * 2; i++) {
            if(i % 2 == 0)
            {
                String text = negativeText.get(NEGATIVE_RANDOMIZED_INDEX.pop());
                int result = testOnText(text, "negative", db, graphManager) ? 0 : 1;
                totalCount += result;
                negativeError += result;
                negativeStep += 1;

                if(result == 1)
                {
                    // Debug content with classification errors
                    //negativeErrorText.add(text);
                }
            } else {
                String text = positiveText.get(POSITIVE_RANDOMIZED_INDEX.pop());
                int result = testOnText(text, "positive", db, graphManager) ? 0 : 1;
                totalCount += result;
                positiveError += result;
                positiveStep += 1;

                if(result == 1)
                {
                    // Debug content with classification errors
                    //positiveErrorText.add(text);
                }
            }

            allStep += 1;

            errorMap.put("negative", (1.0 - (negativeError.doubleValue() / negativeStep)));
            errorMap.put("positive", (1.0 - (positiveError.doubleValue() / positiveStep)));
            errorMap.put("all", (1.0 - ((negativeError.doubleValue() + positiveError.doubleValue()) / allStep)));

            System.out.print("\r{all=" + df.format(errorMap.get("all")) +", negative="+ df.format(errorMap.get("negative"))+", positive=" + df.format(errorMap.get("positive")) + "}");
        }


        errorMap.put("negative", 1.0 - (negativeError.doubleValue() / testCount.doubleValue()));
        errorMap.put("positive", 1.0 - (positiveError.doubleValue() / testCount.doubleValue()));
        errorMap.put("all", 1.0 - (totalCount.doubleValue() / (testCount.doubleValue() * 2)));

        // Return success ratio
        return errorMap;
    }

    private static void train(GraphDatabaseService db, GraphManager graphManager) throws IOException {
        List<String> negativeText = readLargerTextFile(negativeSentimentDirectory);
        List<String> positiveText = readLargerTextFile(positiveSentimentDirectory);

        for (int i = 0; i < TRAIN_COUNT * 2; i++) {
            if(i % 2 == 0) {
                trainOnText(new String[] { cleanText(negativeText.get(NEGATIVE_RANDOMIZED_INDEX.pop())) }, new String[]{"negative"}, db, graphManager);
            } else {
                trainOnText(new String[] { cleanText(positiveText.get(POSITIVE_RANDOMIZED_INDEX.pop())) }, new String[]{"positive"}, db, graphManager);
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
        t = t.replaceAll("([\\.]\\s)", ".1. .0. ");
        t = t.replaceAll("  ", " ");
        t = t.replaceAll("   ", " ");
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