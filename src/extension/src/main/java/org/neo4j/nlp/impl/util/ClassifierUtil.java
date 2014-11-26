package org.neo4j.nlp.impl.util;

import org.apache.spark.SparkConf;
import org.apache.spark.SparkContext;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.mllib.classification.LogisticRegressionModel;
import org.apache.spark.mllib.classification.LogisticRegressionWithSGD;
import org.apache.spark.mllib.evaluation.BinaryClassificationMetrics;
import org.apache.spark.mllib.linalg.Vectors;
import org.apache.spark.mllib.regression.LabeledPoint;
import scala.Tuple2;

public class ClassifierUtil {

    private static SparkContext sparkContext = null;

    public static LogisticRegressionModel trainLogisticRegressionModel(String path) {

        SparkContext sc = getSparkContext();
        JavaRDD data = sc.textFile(path, 1).toJavaRDD();
        JavaRDD<LabeledPoint> parsedData = getLabeledPointJavaRDD(data);

        // Split initial RDD into two... [60% training data, 40% testing data].
        JavaRDD<LabeledPoint> training = parsedData.sample(false, 0.6, 11L);
        training.cache();
        JavaRDD<LabeledPoint> test = parsedData.subtract(training);

        // Run training algorithm to build the model.
        int numIterations = 100;

        final LogisticRegressionModel model = LogisticRegressionWithSGD.train(training.rdd(), numIterations);

        // Clear the default threshold.
        model.clearThreshold();

        // Compute raw scores on the test set.
        JavaRDD<Tuple2<Object, Object>> scoreAndLabels = test.map(
                p -> {
                    Double score = model.predict(p.features());
                    return new Tuple2<>(score, p.label());
                }
        );

        // Get evaluation metrics.
        BinaryClassificationMetrics metrics =
                new BinaryClassificationMetrics(JavaRDD.toRDD(scoreAndLabels));
        double auROC = metrics.areaUnderROC();

        System.out.println("Area under ROC = " + auROC);

        return model;
    }

    private static JavaRDD<LabeledPoint> getLabeledPointJavaRDD(JavaRDD data) {
        return data.map(line -> {
                String[] parts = ((String)line).split(",");
                String[] pointsStr = parts[1].split(" ");
                double[] points = new double[pointsStr.length];
                for (int i = 0; i < pointsStr.length; i++)
                    points[i] = Double.valueOf(pointsStr[i]);
                return new LabeledPoint(Double.valueOf(parts[0]),
                        Vectors.dense(points));
            });
    }

    private static SparkContext getSparkContext() {
        if(sparkContext == null) {
            String appName = "graphify";
            SparkConf conf = new SparkConf().setAppName(appName).set("spark.master", "local[8]")
                    .set("spark.locality.wait", "3000")
                    .set("spark.executor.memory", "13g")
                    .set("spark.cores.max", "8");

            return new SparkContext(conf);
        }
        else {
            return sparkContext;
        }
    }
}
