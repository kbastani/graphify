package org.graphify.core.api.training;

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

import org.apache.spark.mllib.classification.ClassificationModel;
import org.graphify.core.kernel.impl.util.ClassifierUtil;
import org.graphify.core.kernel.impl.util.LearningManager;
import org.graphify.core.kernel.impl.util.VectorUtil;
import org.graphify.core.kernel.models.TrainModelRequest;
import org.graphify.core.kernel.models.TrainModelResponse;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.Evaluators;
import org.neo4j.helpers.collection.IteratorUtil;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.neo4j.graphdb.DynamicRelationshipType.withName;

/**
 * Once feature targets have been built, those targets are used to generate machine learning classifiers.
 */
public class ModelBuilder {

    public static ClassificationModel trainLearningModel(TrainModelRequest trainModelRequest, GraphDatabaseService db) throws IOException {
        TrainModelResponse trainModelResponse = new TrainModelResponse();

        // Validate model
        validateModel(trainModelRequest, db);

        // Select feature vector from the target
        Transaction tx = db.beginTx();

        // Select target node with ID
        Node targetNode = db.findNodesByLabelAndProperty(DynamicLabel.label("Target"), "id", trainModelRequest.getTargetId())
                .iterator()
                .next();

        // Get the feature index for the target
        List<Integer> featureIndexList = Arrays.asList(toObject((int[])targetNode.getProperty("featureIndex")));

        // Traverse to class nodes
        List<Node> classNodes;

        classNodes = IteratorUtil.asCollection(db.traversalDescription()
                .depthFirst()
                .relationships(withName("TARGETS"), Direction.OUTGOING)
                .evaluator(Evaluators.fromDepth(1))
                .evaluator(Evaluators.toDepth(1))
                .traverse(targetNode)
                .nodes()
                .iterator()).stream().collect(Collectors.toList());

        // Get data nodes
        Map<String, List<Node>> dataMap = new HashMap<>();
        Map<String, Node> classMap = new HashMap<>();
        Map<String, String> uniqueDataNodes = new HashMap<>();

        classNodes.forEach(c -> {
            List<Node> dataNodes = (List<Node>)IteratorUtil.asCollection(db.traversalDescription()
                    .depthFirst()
                    .relationships(withName("HAS_DATA"), Direction.OUTGOING)
                    .evaluator(Evaluators.fromDepth(1))
                    .evaluator(Evaluators.toDepth(1))
                    .traverse(c)
                    .nodes()
                    .iterator());

            String label = (String)c.getProperty("name");
            classMap.put(label, c);
            dataMap.put(label, dataNodes);
            dataNodes.forEach(d -> uniqueDataNodes.put((String)d.getProperty("value"), label));
        });

        // Get the unique union of all the data nodes
        Integer dataSize = uniqueDataNodes.size();

        // Update the target
        targetNode.setProperty("dataSize", dataSize);

        // /data/
        String path = "../" + trainModelRequest.getTargetId() + "_train.txt";

        // Iterate through each class and generate a weighted feature vector for each data node

        // Acquire access to the data directory

        // Write results to text files
        FileWriter fw = new FileWriter(path);

        dataMap.keySet().forEach(label -> {

            System.out.println("Label: " + label + ", Id: " + dataMap.keySet().stream().collect(Collectors.toList()).indexOf(label));

            dataMap.get(label).forEach(data -> {
                String dataValue = (String) data.getProperty("value");

                // Extract features
                List<LinkedHashMap<String, Object>> featureMap = VectorUtil.getFeatureFrequencyMap(db, dataValue, LearningManager.GRAPH_MANAGER);

                // Get matched features and frequencies
                Map<Long, Integer> frequencyMap = VectorUtil.getTermFrequencyMapForInput(featureMap);

                List<Integer> inputVector = VectorUtil.getFeatureVectorFromMap(featureIndexList, featureMap);

                // Get the training set for the label
                List<Double> trainingSet = VectorUtil.getWeightedVector(featureIndexList, inputVector, frequencyMap, db, trainModelRequest.getTargetId());

                String row = getFeatureVectorAsString(dataMap.keySet().stream().collect(Collectors.toList()).indexOf(label), trainingSet);

                // Write row to file
                try {
                    fw.write(row);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        });

        fw.flush();
        fw.close();

        String type = ((String)targetNode.getProperty("type"));

        tx.success();
        tx.close();
        ClassificationModel model = null;

        try {
            if (type == "multi") {
                model = ClassifierUtil.trainNaiveBayesModel(path);
            } else {
                model = ClassifierUtil.trainLogisticRegressionModel(path);
            }
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }

        return model;
    }

    private static Integer[] toObject(int[] intArray) {

        Integer[] result = new Integer[intArray.length];
        for (int i = 0; i < intArray.length; i++) {
            result[i] = Integer.valueOf(intArray[i]);
        }
        return result;
    }

    private static String getFeatureVectorAsString(long id, List<Double> trainingSet) {
        StringBuilder stringBuilder;
        stringBuilder = new StringBuilder();
        // Write row to string buffer
        stringBuilder.append(id).append(",");
        stringBuilder.append(trainingSet.stream().map(Object::toString).collect(Collectors.toList())
                .stream()
                .reduce((x, y) -> x + " " + y).get()).append("\n");

        return stringBuilder.toString();
    }

    private static void validateModel(TrainModelRequest trainModelRequest, GraphDatabaseService db) {
        // Make sure that the targetId node exists
        Transaction tx = db.beginTx();

        if(!db.findNodesByLabelAndProperty(DynamicLabel.label("Target"), "id", trainModelRequest.getTargetId()).iterator().hasNext())
            throw new IllegalArgumentException("The supplied targetId does not exist.");

        tx.success();
        tx.close();

        // Ensure that the training ratio is between .1 and .9
        if(trainModelRequest.getTrainingRatio() < .1 || trainModelRequest.getTrainingRatio() > .9)
            throw new IllegalArgumentException("The supplied trainingRatio value must be between 0.1 and 0.9");
    }

    public static List<Integer> featureIndexForTarget(Integer targetId, GraphDatabaseService db) {
        // Select feature vector from the target
        Transaction tx = db.beginTx();

        // Select target node with ID
        Node targetNode = db.findNodesByLabelAndProperty(DynamicLabel.label("Target"), "id", targetId)
                .iterator()
                .next();

        // Get the feature index for the target
        List<Integer> featureIndexList = Arrays.asList(toObject((int[])targetNode.getProperty("featureIndex")));

        tx.success();
        tx.close();

        return featureIndexList;
    }
}
