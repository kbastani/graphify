package org.graphify.core.api.selection;

import junit.framework.TestCase;
import org.graphify.core.api.extraction.Features;
import org.graphify.core.kernel.models.LabeledText;
import org.graphify.core.kernel.models.SelectedFeatures;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.test.TestGraphDatabaseFactory;

import java.util.ArrayList;

public class FeatureSelectorTest extends TestCase {

    private static GraphDatabaseService setUpDb()
    {
        return new TestGraphDatabaseFactory().newImpermanentDatabaseBuilder().newGraphDatabase();
    }

    public void testCreateFeatureTarget() throws Exception {
        GraphDatabaseService db = setUpDb();

        LabeledText labeledText = new LabeledText();
        labeledText.setLabel(new String[] { "positive" });
        labeledText.setText(new String[] { "The weather outside is frightful but the fire is so delightful." });
        labeledText.setFocus(5);
        Features.extractFeatures(db, labeledText);

        labeledText.setLabel(new String[] { "negative"});
        labeledText.setText(new String[] { "Let the bodies hit the floor, let the bodies hit the floor!"});
        labeledText.setFocus(5);
        Features.extractFeatures(db, labeledText);

        FeatureSelector.createFeatureTarget(new SelectedFeatures(new ArrayList<String>() {
            {
                add("positive");
                add("negative");
            }
        }, "binary"), db);
    }
}