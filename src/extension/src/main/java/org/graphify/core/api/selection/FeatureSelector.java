package org.graphify.core.api.selection;

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

import org.graphify.core.kernel.models.FeatureTargetResponse;
import org.graphify.core.kernel.models.SelectedFeatures;
import org.neo4j.graphdb.DynamicLabel;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import static org.neo4j.graphdb.DynamicRelationshipType.withName;

/**
 * The result of feature selection produces either a `binary target` or a `multi-class target`.
 * Targets are used to select features and to build feature vectors that will be used for building learning models.
 */
public class FeatureSelector {

    /**
     * Creates a feature target for building a machine learning model.
     * @param selectedFeatures
     */
    public static FeatureTargetResponse createFeatureTarget(SelectedFeatures selectedFeatures, GraphDatabaseService db)
    {
        // Validate the model
        validateModel(selectedFeatures, db);

        // Create a new target
        Transaction tx = db.beginTx();

        Node target =  db.createNode(DynamicLabel.label("Target"));

        target.setProperty("labels", selectedFeatures.getLabels().toArray(new String[selectedFeatures.getLabels().size()]));

        // Get all label classes
        List<Node> labelNodes = selectedFeatures.getLabels().stream()
                .map(label -> db.findNodesByLabelAndProperty(DynamicLabel.label("Class"), "name", label)
                        .iterator().next()).collect(Collectors.toList());

        labelNodes.forEach(label -> target.createRelationshipTo(label, withName("TARGETS")));

        target.setProperty("type", selectedFeatures.getType());

        Long featureTargetId = target.getId();

        tx.success();
        tx.close();

        return new FeatureTargetResponse(featureTargetId);
    }

    /**
     * Validates th supplied SelectedFeatures model for creating a feature target.
     * @param selectedFeatures The model to validate.
     * @param db The graph database service object to connect to the data store.
     */
    private static void validateModel(SelectedFeatures selectedFeatures, GraphDatabaseService db) {
        // Assert that labels length is greater or equal to 2
        if(!(selectedFeatures.getLabels().size() >= 2))
            throw new IllegalArgumentException("You must supply 2 or more labels.");

        // Assert that supplied labels are unique
        HashMap<String, Integer> keyMap = new HashMap<>();

        for (String name : selectedFeatures.getLabels()) {
            keyMap.put(name, 1);
        }

        if(keyMap.keySet().size() != selectedFeatures.getLabels().size())
            throw new IllegalArgumentException("You supplied one or more duplicate labels.");

        boolean labelsExist = true;

        // Assert that labels exist
        Transaction tx = db.beginTx();

        for (String name : selectedFeatures.getLabels()) {
            if(!db.findNodesByLabelAndProperty(DynamicLabel.label("Class"), "name", name).iterator().hasNext())
                labelsExist = false;
        }

        tx.close();

        if(!labelsExist)
            throw new IllegalArgumentException("One or more supplied labels do not exist.");

        // Assert that binary classifier must have exactly two labels
        if(selectedFeatures.getType() == "binary" && selectedFeatures.getLabels().size() > 2)
            throw new IllegalArgumentException("Binary classifiers can only have exactly 2 labels");

        List<String> validTypes = new ArrayList<>();
        validTypes.add("multi");
        validTypes.add("binary");

        // Assert that type is either binary or multi
        if(!validTypes.contains(selectedFeatures.getType()))
            throw new IllegalArgumentException("Supported classifiers are 'multi' or 'binary'.");
    }
}
