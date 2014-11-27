package org.graphify.core.api.training;

import junit.framework.TestCase;
import org.apache.commons.lang.ArrayUtils;
import org.apache.spark.mllib.classification.ClassificationModel;
import org.apache.spark.mllib.classification.LogisticRegressionModel;
import org.graphify.core.api.classification.ModelClassifier;
import org.graphify.core.api.extraction.Features;
import org.graphify.core.api.selection.FeatureSelector;
import org.graphify.core.kernel.models.FeatureTargetResponse;
import org.graphify.core.kernel.models.LabeledText;
import org.graphify.core.kernel.models.SelectedFeatures;
import org.graphify.core.kernel.models.TrainModelRequest;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.test.TestGraphDatabaseFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.IntStream;

public class ModelBuilderTest extends TestCase {

    final public static int TEST_COUNT = 500;
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

    public void testTrainLearningModel() throws Exception {
        GraphDatabaseService db = setUpDb();

        train(db);

        FeatureTargetResponse featureTargetResponse = FeatureSelector.createFeatureTarget(new SelectedFeatures(new ArrayList<String>() {
            {
                add("positive");
                add("negative");
            }
        }, "binary"), db);

        ClassificationModel model = ModelBuilder.trainLearningModel(new TrainModelRequest(.4, featureTargetResponse.getTargetId()), db);

        //test(db, model, TEST_COUNT, featureTargetResponse.getTargetId());
    }

    private static void train(GraphDatabaseService db) throws IOException {
        List<String> negativeText = readLargerTextFile(negativeSentimentDirectory);
        List<String> positiveText = readLargerTextFile(positiveSentimentDirectory);

        for (int i = 0; i < TRAIN_COUNT * 2; i++) {
            if(i % 2 == 0) {
                trainOnText(new String[] { cleanText(negativeText.get(NEGATIVE_RANDOMIZED_INDEX.pop())) }, new String[]{"negative"}, db);
            } else {
                trainOnText(new String[] { cleanText(positiveText.get(POSITIVE_RANDOMIZED_INDEX.pop())) }, new String[]{"positive"}, db);
            }
        }
    }

    private static Map<String, Double> test(GraphDatabaseService db, LogisticRegressionModel model, Integer testCount, Integer targetId) throws IOException {
        Integer negativeError = 0;
        Integer positiveError = 0;
        Integer totalCount = 0;
        Integer negativeStep = 0;
        Integer positiveStep = 0;
        Integer allStep = 0;

        List<String> negativeText = readLargerTextFile(negativeSentimentDirectory);
        List<String> positiveText = readLargerTextFile(positiveSentimentDirectory);

        Map<String, Double> errorMap = new HashMap<>();

        DecimalFormat df = new DecimalFormat("#.##");


        for (int i = 1; i < testCount * 2; i++) {
            if(i % 2 == 0)
            {
                String text = negativeText.get(NEGATIVE_RANDOMIZED_INDEX.pop());
                Double score = ModelClassifier.classifyText(model, text, db, targetId);
                int result = score < .5 ? 0 : 1;
                totalCount += result;
                negativeError += result;
                negativeStep += 1;
            } else {
                String text = positiveText.get(POSITIVE_RANDOMIZED_INDEX.pop());
                Double score = ModelClassifier.classifyText(model, text, db, targetId);
                int result = score >= .5 ? 0 : 1;
                totalCount += result;
                positiveError += result;
                positiveStep += 1;
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

    private static void trainOnText(String[] text, String[] label, GraphDatabaseService db) {
        Features.extractFeatures(db, new LabeledText(text, label, 1));
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

    private static Stack<Integer> getRandomizedIndex(int lowerBound, int upperBound)
    {
        List<Integer> randomIndex = new ArrayList<Integer>();

        Collections.addAll(randomIndex, ArrayUtils.toObject(IntStream.range(lowerBound, upperBound).toArray()));

        randomIndex.sort((a, b) -> new Random().nextInt(2) == 0 ? -1 : 1 );

        Stack<Integer> integerStack = new Stack<>();

        integerStack.addAll(randomIndex);

        return integerStack;
    }
}