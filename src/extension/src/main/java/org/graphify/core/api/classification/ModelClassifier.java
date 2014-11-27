package org.graphify.core.api.classification;

import org.apache.spark.mllib.classification.ClassificationModel;
import org.apache.spark.mllib.linalg.Vector;
import org.apache.spark.mllib.linalg.Vectors;
import org.graphify.core.api.training.ModelBuilder;
import org.graphify.core.kernel.impl.util.LearningManager;
import org.graphify.core.kernel.impl.util.VectorUtil;
import org.neo4j.graphdb.GraphDatabaseService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Copyright (C) 2014 Kenny Bastani
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
public class ModelClassifier {
    public static Double classifyText(ClassificationModel model, String text, GraphDatabaseService db, Integer targetId) {

        List<Integer> featureIndexList = ModelBuilder.featureIndexForTarget(targetId, db);

        List<LinkedHashMap<String, Object>> featureMap = VectorUtil.getFeatureFrequencyMap(db, text, LearningManager.GRAPH_MANAGER);

        // Get matched features and frequencies
        Map<Long, Integer> frequencyMap = VectorUtil.getTermFrequencyMapForInput(featureMap);
        List<Integer> inputVector = VectorUtil.getFeatureVectorFromMap(featureIndexList, featureMap);
        List<Double> unlabeledVector = VectorUtil.getWeightedVector(featureIndexList, inputVector, frequencyMap, db, targetId);

        Vector vector = Vectors.dense(toPrimitive(unlabeledVector.toArray(new Double[unlabeledVector.size()])));

        Double score = model.predict(vector);

        return score;
    }

    private static double[] toPrimitive(Double[] doubleArray) {
        double[] result = new double[doubleArray.length];
        for (int i = 0; i < doubleArray.length; i++) {
            result[i] = doubleArray[i];
        }
        return result;
    }
}
