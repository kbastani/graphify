/*
 * Copyright (C) 2014 Kenny Bastani
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.neo4j.nlp.helpers;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.index.UniqueFactory;
import org.neo4j.graphdb.traversal.Evaluators;
import org.neo4j.helpers.collection.IteratorUtil;
import org.neo4j.nlp.impl.cache.ClassRelationshipCache;
import org.neo4j.nlp.impl.cache.PatternRelationshipCache;
import org.neo4j.nlp.impl.manager.ClassManager;
import org.neo4j.nlp.impl.manager.DataManager;
import org.neo4j.nlp.impl.manager.DataRelationshipManager;
import org.neo4j.nlp.models.PatternCount;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The GraphManager class is a management wrapper around a cache of patterns that are extracted from input
 * data, whether it be text or any other combination of symbols. Patterns are represented as a RegEx string
 * that expands hierarchy from a genetic algorithm. Each pattern tracks the number of times it has been
 * bound to a piece of text. The number of times eventually exceeds the current threshold and when that
 * occurs then a new pattern is created that inherits the base pattern but extends it to account for
 * the probability of a new part of text being present adjacent to the parent pattern. A cost function
 * is applied as child nodes are created, which makes it more costly to reproduce. This cost function
 * reduces the time complexity of the learning model over time.
 */
public class GraphManager {

    private final String label;
    private final String propertyKey;

    private UniqueFactory<Node> patternFactory;

    public static final Cache<String, Long> patternCache = CacheBuilder.newBuilder().maximumSize(20000000).build();
    public static final Cache<Long, String> inversePatternCache = CacheBuilder.newBuilder().maximumSize(20000000).build();
    public static final Cache<String, String> edgeCache = CacheBuilder.newBuilder().maximumSize(2000000).build();

    private final ClassRelationshipCache classRelationshipCache;
    private final PatternRelationshipCache patternRelationshipCache;

    private final DataRelationshipManager dataRelationshipManager;
    private final DataManager dataManager;
    private final ClassManager classManager;

    public static final int MIN_THRESHOLD = 5;
    private static final String WILDCARD_TEMPLATE = "\\(\\\\b\\[\\\\w'.-\\]\\+\\\\b\\)";
    public static final String ROOT_TEMPLATE = "(\\b[\\w'.-]+\\b)\\s(\\b[\\w'.-]+\\b)";

    public GraphManager(String label) {
        this.label = label;
        this.propertyKey = "pattern";
        classRelationshipCache = new ClassRelationshipCache();
        patternRelationshipCache = new PatternRelationshipCache();
        dataRelationshipManager = new DataRelationshipManager();
        dataManager = new DataManager("Data", "value");
        classManager = new ClassManager("Class", "name");
    }

    public List<String> getNextLayer(Long nodeId, GraphDatabaseService db)
    {
        List<String> patterns = new ArrayList<>();
        for(Long id : patternRelationshipCache.getRelationships(nodeId, db, this))
        {
            patterns.add(inversePatternCache.getIfPresent(id));
        }

        return patterns;
    }

    public Node getOrCreateNode(String keyValue, GraphDatabaseService db) {
        if(keyValue != null) {
            Node nodeStart = null;
            Long nodeId = patternCache.getIfPresent(keyValue);

            if (nodeId == null) {
                try(Transaction tx = db.beginTx()) {
                    ResourceIterator<Node> results = db.findNodesByLabelAndProperty(DynamicLabel.label(label), propertyKey, keyValue).iterator();
                    if (results.hasNext()) {
                        nodeStart = results.next();
                        patternCache.put(keyValue, nodeStart.getId());
                        inversePatternCache.put(nodeStart.getId(), keyValue);
                    }
                    tx.success();
                    tx.close();
                }
            } else {
                try(Transaction tx = db.beginTx()) {
                    nodeStart = db.getNodeById(nodeId);
                    tx.success();
                    tx.close();
                }
            }

            if (nodeStart == null) {
                try(Transaction tx = db.beginTx()) {
                    createNodeFactory(db);
                    nodeStart = patternFactory.getOrCreate(propertyKey, keyValue);
                    Label nodeLabel = DynamicLabel.label(label);
                    nodeStart.addLabel(nodeLabel);
                    tx.success();
                    tx.close();
                } finally {

                    if (nodeStart != null) {
                        patternCache.put(keyValue, nodeStart.getId());
                        inversePatternCache.put(nodeStart.getId(), keyValue);
                    }
                }
            }

            return nodeStart;
        }
        else
        {
            return null;
        }
    }

    private enum Rels implements RelationshipType {
        NEXT, MATCHES
    }

    /**
     * This method is the entry point for training a natural language text model.
     * @param patternNode A node existing within the pattern recognition hierarchy that defines the starting point to perform a pattern matching traversal from.
     * @param text The text snippet or sentence that should be used to train the model.
     * @param db The Neo4j GraphDatabaseService that contains the pattern recognition hierarchy.
     * @param label The name of the label that should be associated with any patterns that are mined in the supplied text snippet contained within the text parameter.
     * @return Returns the ID of the data node that represents the input text as a node within the Neo4j graph database.
     */
    public int handlePattern(Node patternNode, String text, GraphDatabaseService db, String[] label) {
        Node dataNode;
        dataNode = dataManager.getOrCreateNode(text, db);
        // Get child patterns
        Transaction tx = db.beginTx();
        db.getNodeById(dataNode.getId()).setProperty("label", label);
        tx.success();


            recognizeMatch(db, patternNode, dataNode, label, 1, true);


        return (int)dataNode.getId();
    }

    /**
     * The recursive recognition matching algorithm that traverses the pattern recognition hierarchy
     * to bind extracted patterns in the training model to input text for classification on the supplied label.
     * @param db The Neo4j GraphDatabaseService that contains the pattern recognition hierarchy.
     * @param patternNode A node existing within the pattern recognition hierarchy that defines the starting point to perform a pattern matching traversal from.
     * @param dataNode The data node that contains the input text that is currently being trained on.
     * @param label The name of the label that should be associated with any patterns that are mined in the supplied text snippet contained within the text parameter.
     * @param depth The level of depth that the algorithm is currently operating a matching algorithm on.
     * @param cont A flag indicating whether or not to continue recursive matching,
     */
    private void recognizeMatch(GraphDatabaseService db, Node patternNode, Node dataNode, String[] label, int depth, boolean cont) {
        ResourceIterable<Node> nodes;
        int resultCount;
        boolean isRoot;
        try (Transaction tx = db.beginTx()) {
            nodes = db.traversalDescription()
                    .depthFirst()
                    .relationships(Rels.NEXT, Direction.OUTGOING)
                    .evaluator(Evaluators.fromDepth(1))
                    .evaluator(Evaluators.toDepth(1))
                    .traverse(patternNode)
                    .nodes();

            resultCount = IteratorUtil.count(nodes);
            isRoot = patternNode.hasProperty("root");
            tx.success();
        }

        if (resultCount > 0) {
            try (Transaction tx = db.beginTx()) {
                boolean hasMatch = false;

                for (Node currentNode : nodes) {
                    boolean result = matchLeaves(db, dataNode, currentNode, label, depth);
                    if(!hasMatch && result) hasMatch = true;
                }

                if (isRoot && cont)
                    matchLeaves(db, dataNode, patternNode, label, depth);

                tx.success();
            }
        } else {
            if (cont) {
                    matchLeaves(db, dataNode, patternNode, label, depth);
            }
        }
    }

    /**
     * This method matches on the child patterns existing as nodes within the pattern recognition hierarchy.
     * @param db The Neo4j GraphDatabaseService that contains the pattern recognition hierarchy.
     * @param dataNode The data node that contains the input text that is currently being trained on.
     * @param currentNode The current pattern node that the recursive pattern recognition algorithm is operating on within the hierarchy.
     * @param label The name of the label that should be associated with any patterns that are mined in the supplied text snippet contained within the text parameter.
     * @param depth The level of depth that the algorithm is currently operating a matching algorithm on.
     * @return Returns a flag that indicates whether or not the currentPattern parameter's RegEx matched the dataNode parameter representing the input text to train on.
     */
    private boolean matchLeaves(GraphDatabaseService db, Node dataNode, Node currentNode, String[] label, int depth) {

        String pattern;
        String value;
        try(Transaction tx = db.beginTx())
        {
            pattern = (String)currentNode.getProperty("pattern");
            value = (String) dataNode.getProperty("value");
            tx.success();
        }

        Pattern p = Pattern.compile("(?i)" + pattern);
        Matcher m = p.matcher(value);
        int localMatchCount = 0;
        while(m.find())
            localMatchCount++;

        if(localMatchCount > 0) {
            // Increment match


            // Relate to label
            for (String labelName : label) {
                Node labelNode = classManager.getOrCreateNode(labelName, db);
                classRelationshipCache.getOrCreateRelationship(currentNode.getId(), labelNode.getId(), db, this);
            }
            dataRelationshipManager.getOrCreateNode(currentNode.getId(), dataNode.getId(), db);

            try (Transaction tx = db.beginTx()) {
                currentNode.setProperty("matches", ((int) currentNode.getProperty("matches")) + localMatchCount);
                tx.success();
            }

            int threshold = (int) currentNode.getProperty("threshold");

            if ((int) currentNode.getProperty("matches") >= threshold) {
                try (Transaction tx = db.beginTx()) {
                    currentNode.setProperty("matches", 0);
                    currentNode.setProperty("threshold", (threshold / MIN_THRESHOLD) + threshold);
                    tx.success();
                }

                // Populate a map of matched patterns
                Map<Integer, Map<String, PatternCount>> matchDictionary = new HashMap<>();
                populatePatternMap(db, currentNode, matchDictionary);

                // Generate nodes for every wildcard
                generateChildPatterns(db, dataNode, currentNode, label, depth, matchDictionary);
            }
            recognizeMatch(db, currentNode, dataNode, label, depth + 1, false);
        }


        return localMatchCount > 0;
    }

    /**
     * Generates child patterns the inherit the parent's base code but extending it for every wildcard with a new
     * pattern that is most available in previously matched data.
     * @param db The Neo4j GraphDatabaseService that contains the pattern recognition hierarchy.
     * @param dataNode The data node that contains the input text that is currently being trained on.
     * @param currentNode The current pattern node that the recursive pattern recognition algorithm is operating on within the hierarchy.
     * @param label The name of the label that should be associated with any patterns that are mined in the supplied text snippet contained within the text parameter.
     * @param depth The level of depth that the algorithm is currently operating a matching algorithm on.
     * @param matchDictionary A map that has been populated with new patterns extracted from data attached to the base pattern.
     */
    private void generateChildPatterns(GraphDatabaseService db, Node dataNode, Node currentNode, String[] label, int depth, Map<Integer, Map<String, PatternCount>> matchDictionary) {
        for (int i = 0; i < matchDictionary.size(); i++) {

            int counter = 0;
            // Order by match count desc, limit 1
            List<PatternCount> patternCounts = new ArrayList<>(matchDictionary.get(i + 1).values());
            Collections.sort(patternCounts, (o1, o2) -> o2.getCount() - o1.getCount());
            patternCounts = patternCounts.stream()
                    .filter((pc) -> pc.getCount() > 1)
                    .collect(Collectors.toList());

            if(patternCounts.size() > 0) {
                PatternCount patternCount = (PatternCount) patternCounts.toArray()[counter];
                String pattern = (String) currentNode.getProperty("pattern");
                String newPattern = GeneratePattern(i, patternCount, pattern);
                String newTemplate = GetTemplate(newPattern);
                try (Transaction tx = db.beginTx()) {

                    while (this.getOrCreateNode(newPattern, db).hasProperty("matches") && counter < (patternCounts.size() - 1)) {
                        patternCount = (PatternCount) patternCounts.toArray()[counter];
                        pattern = (String) currentNode.getProperty("pattern");
                        newPattern = GeneratePattern(i, patternCount, pattern);
                        newTemplate = GetTemplate(newPattern);
                        counter++;
                    }

                    Node leafNode = this.getOrCreateNode(newPattern, db);

                    if (!leafNode.hasProperty("matches")) {
                        if (edgeCache.getIfPresent((String.valueOf(leafNode.getId()))) == null) {
                            edgeCache.put((String.valueOf(leafNode.getId())), (String.valueOf(leafNode.getId())));
                        }

                        if (edgeCache.getIfPresent((int) currentNode.getId() + "->" + (int) leafNode.getId()) == null) {
                            patternRelationshipCache.getOrCreateRelationship(currentNode.getId(), leafNode.getId(), db, this);
                        }

                        leafNode.setProperty("matches", patternCount.getDataNodes().size());
                        leafNode.setProperty("threshold", MIN_THRESHOLD);
                        leafNode.setProperty("phrase", newTemplate);
                        leafNode.setProperty("depth", depth + 1);

                        // Bind new pattern to the data nodes it was generated from
                        patternCount.getDataNodes().forEach((dn) ->
                        {
                            String[] dataLabels = (String[])dn.getProperty("label");
                            for(String labelName : dataLabels)
                            {
                                Node labelNode = classManager.getOrCreateNode(labelName, db);
                                classRelationshipCache.getOrCreateRelationship(leafNode.getId(), labelNode.getId(), db, this);
                            }
                            dataRelationshipManager.getOrCreateNode(leafNode.getId(), dn.getId(), db);
                        });
                    }
                    else {
                        recognizeMatch(db, leafNode, dataNode, label, depth + 1, true);
                    }

                    tx.success();
                }
            }
        }
    }

    /**
     * Creates a pattern matching dictionary for all data attached to a pattern.
     * @param db The Neo4j GraphDatabaseService that contains the pattern recognition hierarchy.
     * @param currentNode The current pattern node that the recursive pattern recognition algorithm is operating on within the hierarchy.
     * @param matchDictionary A map that will be populated with new patterns extracted from data using the base pattern.
     */
    private void populatePatternMap(GraphDatabaseService db, Node currentNode, Map<Integer, Map<String, PatternCount>> matchDictionary) {
        for (Node matchNodes : db.traversalDescription()
                .depthFirst()
                .relationships(Rels.MATCHES, Direction.OUTGOING)
                .evaluator(Evaluators.fromDepth(1))
                .evaluator(Evaluators.toDepth(1))
                .traverse(currentNode)
                .nodes()) {

            Pattern dataPattern = Pattern.compile("(?i)" + currentNode.getProperty("pattern"));
            Matcher dataMatch = dataPattern.matcher((String) matchNodes.getProperty("value"));

            // Create leaf nodes
            while (dataMatch.find()) {
                for (int i = 1; i <= dataMatch.groupCount(); i++) {
                    String groupMatch = dataMatch.group(i).toLowerCase();
                    if (!matchDictionary.containsKey(i)) {
                        matchDictionary.put(i, new HashMap<>());
                        matchDictionary.get(i).put(groupMatch, new PatternCount(groupMatch, 1, matchNodes));
                    } else {
                        if (!matchDictionary.get(i).containsKey(groupMatch)) {
                            matchDictionary.get(i).put(groupMatch, new PatternCount(groupMatch, 1, matchNodes));
                        } else {
                            PatternCount patternCount = matchDictionary.get(i).get(groupMatch);
                            patternCount.setCount(patternCount.getCount() + 1);
                            patternCount.addDataNode(matchNodes);
                            matchDictionary.get(i).put(groupMatch, patternCount);
                        }
                    }
                }
            }
        }
    }

    /**
     * Gets a template representation of a RegEx string, making it easier to read a text pattern.
     * @param pattern The RegEx string to translate into a readable string template.
     * @return Returns a readable format of the RegEx, with {n} in place of wildcard matches.
     */
    public String GetTemplate(String pattern) {
        Pattern generalMatcher = Pattern.compile(WILDCARD_TEMPLATE);
        Matcher regexMatcher = generalMatcher.matcher(pattern);
        StringBuffer s = new StringBuffer();
        int counter = 0;

        while (regexMatcher.find()) {
            regexMatcher.appendReplacement(s, "{" + counter + "}");
            counter++;
        }
        regexMatcher.appendTail(s);

        return s.toString().replace("\\[\\s\\]", " ").replace("\\s?", " ").replace("\\s", " ");
    }

    /**
     * Generates a child pattern from a supplied parent pattern.
     * @param i The index of the wildcard word within the parent RegEx pattern.
     * @param patternCount A vector of words containing sorted on frequency of matches from historic data.
     * @param pattern The RegEx string of the parent pattern.
     * @return Returns a new child pattern that is generated from the patternCount model.
     */
    public String GeneratePattern(int i, PatternCount patternCount, String pattern) {
        Pattern generalMatcher = Pattern.compile(WILDCARD_TEMPLATE);
        Matcher regexMatcher = generalMatcher.matcher(pattern);
        StringBuffer s = new StringBuffer();
        int counter = 0;

        while (regexMatcher.find()) {
            if (counter == i) {
                StringBuilder sb = new StringBuilder();
                sb.append(WILDCARD_TEMPLATE);

                regexMatcher.appendReplacement(s, ((counter == 0) ? sb.toString() + ("\\\\s" + patternCount.getPattern()) : (patternCount.getPattern() + "\\\\s") + sb.toString()));
            } else {
                regexMatcher.appendReplacement(s, regexMatcher.group().replace("\\", "\\\\"));
            }
            counter++;
        }
        regexMatcher.appendTail(s);

        return s.toString();
    }


    /**
     * The node factory is used for caching.
     * @param db The Neo4j GraphDatabaseService that contains the pattern recognition hierarchy.
     */
    private void createNodeFactory(GraphDatabaseService db) {
        if (patternFactory == null) {
            patternFactory = new UniqueFactory.UniqueNodeFactory(db, label) {
                @Override
                protected void initialize(Node created, Map<String, Object> properties) {
                    created.setProperty(propertyKey, properties.get(propertyKey));
                }
            };
        }
    }
}

