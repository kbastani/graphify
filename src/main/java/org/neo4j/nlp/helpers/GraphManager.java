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
import org.neo4j.nlp.models.PatternCount;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.neo4j.graphdb.DynamicRelationshipType.withName;

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

    private static final Cache<String, Long> patternCache = CacheBuilder.newBuilder().maximumSize(20000000).build();
    private static final Cache<String, String> edgeCache = CacheBuilder.newBuilder().maximumSize(2000000).build();
    private UniqueFactory<Node> patternFactory;
    private final String label;
    private final String propertyKey;
    private final RelationshipManager relationshipManager;
    private final RelationshipManager dataRelationshipManager;
    private final ClassManager classManager;

    public GraphManager(String label, String propertyKey) {
        this.label = label;
        this.propertyKey = propertyKey;
        relationshipManager = new RelationshipManager("HAS_CLASS");
        dataRelationshipManager = new RelationshipManager("MATCHES");
        classManager = new ClassManager("Class", "name");
    }

    public Node getOrCreateNode(String keyValue, GraphDatabaseService db) {
        Node nodeStart = null;
        Long nodeId = patternCache.getIfPresent(keyValue);

        if (nodeId == null) {
            ResourceIterator<Node> results = db.findNodesByLabelAndProperty(DynamicLabel.label(label), propertyKey, keyValue).iterator();

            if (results.hasNext()) {
                nodeStart = results.next();
                patternCache.put(keyValue, nodeStart.getId());
            }

        } else {
            nodeStart = db.getNodeById(nodeId);
        }

        if (nodeStart == null) {
            Transaction tx = db.beginTx();
            try {
                createNodeFactory(db);
                nodeStart = patternFactory.getOrCreate(propertyKey, keyValue);
                Label nodeLabel = DynamicLabel.label(label);
                nodeStart.addLabel(nodeLabel);
                if (label.equals("Pattern")) {
                    Label stateMachine = DynamicLabel.label("Active");
                    nodeStart.addLabel(stateMachine);
                }
                tx.success();
            } catch (final Exception e) {
                System.out.println(e);
                tx.failure();
            } finally {
                if(nodeStart != null)
                    patternCache.put(keyValue, nodeStart.getId());
            }
        }

        return nodeStart;
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
    public int handlePattern(Node patternNode, String text, GraphDatabaseService db, String label) {
        List<Integer> nodeMatches = new ArrayList<>();
        Node dataNode;
        // Get child patterns
        try (Transaction tx = db.beginTx()) {

            GraphManager graphManager = new GraphManager("Data", "value");
            dataNode = graphManager.getOrCreateNode(text, db);
            dataNode.setProperty("label", label);
            dataNode.setProperty("time", System.currentTimeMillis() / 1000L);
            tx.success();
        }

        try (Transaction tx = db.beginTx()) {
            recognizeMatch(db, patternNode, dataNode, label, 1, nodeMatches, true);
            tx.success();
        }

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
     * @param nodeMatches A list of Neo4j node IDs that represent each pattern node that has previously been matched in the recursive matching process.
     * @param cont A flag indicating whether or not to continue recursive matching,
     */
    private void recognizeMatch(GraphDatabaseService db, Node patternNode, Node dataNode, String label, int depth, List<Integer> nodeMatches, boolean cont) {
        ResourceIterable<Node> nodes;
        int resultCount;
        boolean active;
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
            active = patternNode.hasLabel(DynamicLabel.label("Active"));
            isRoot = patternNode.hasProperty("root");
            tx.success();
        }

        if (resultCount > 0) {
            try (Transaction tx = db.beginTx()) {
                boolean hasMatch = false;

                for (Node currentNode : nodes) {
                    boolean result = matchLeaves(db, dataNode, currentNode, label, depth, nodeMatches);
                    if(!hasMatch && result) {
                        hasMatch = true;
                    }
                }


                if (!hasMatch && !nodeMatches.contains((int)patternNode.getId()))
                {
                    nodeMatches.add((int)patternNode.getId());
                }

                if (isRoot && cont)
                    matchLeaves(db, dataNode, patternNode, label, depth, nodeMatches);

                tx.success();
            }
        } else {
            if (active && cont) {
                try (Transaction tx = db.beginTx()) {

                    boolean result = matchLeaves(db, dataNode, patternNode, label, depth, nodeMatches);
                    boolean hasMatch = false;

                    if(!hasMatch && result) {
                        hasMatch = true;
                    }

                    if (!hasMatch && !nodeMatches.contains((int)patternNode.getId()))
                    {
                        nodeMatches.add((int)patternNode.getId());
                    }

                    tx.success();
                }
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
     * @param nodeMatches A list of Neo4j node IDs that represent each pattern node that has previously been matched in the recursive matching process.
     * @return Returns a flag that indicates whether or not the currentPattern parameter's RegEx matched the dataNode parameter representing the input text to train on.
     */
    private boolean matchLeaves(GraphDatabaseService db, Node dataNode, Node currentNode, String label, int depth, List<Integer> nodeMatches) {

        Pattern p = Pattern.compile("(?i)" + (String) currentNode.getProperty("pattern"));
        Matcher m = p.matcher((String) dataNode.getProperty("value"));
        boolean b = m.find();

        // Recursive scan
        if (b) {
            // Increment match
            try (Transaction tx = db.beginTx()) {
                // Relate to label
                Node labelNode = classManager.getOrCreateNode(label, db);

                relationshipManager.getOrCreateNode(currentNode.getId(), labelNode.getId(), db);
                dataRelationshipManager.getOrCreateNode(currentNode.getId(), dataNode.getId(), db);
                currentNode.setProperty("matches", ((int) currentNode.getProperty("matches")) + 1);
                tx.success();
            }

            if ((int) currentNode.getProperty("matches") >= (int) currentNode.getProperty("threshold") && currentNode.hasLabel(DynamicLabel.label("Active"))) {

                try (Transaction tx = db.beginTx()) {

                    currentNode.setProperty("matches", 0);
                    if(currentNode.hasProperty("root"))
                    {
                        currentNode.setProperty("threshold", ((int)currentNode.getProperty("threshold") / 5) + ((int)currentNode.getProperty("threshold")));
                    }
                    else
                    {
                        currentNode.setProperty("threshold", ((int)currentNode.getProperty("threshold") / 5) + ((int)currentNode.getProperty("threshold")));
                    }

                    tx.success();
                }

                // Create dictionary
                Map<Integer, Map<String, PatternCount>> matchDictionary = new HashMap<>();

                for (Node matchNodes : db.traversalDescription()
                        .depthFirst()
                        .relationships(Rels.MATCHES, Direction.OUTGOING)
                        .evaluator(Evaluators.fromDepth(1))
                        .evaluator(Evaluators.toDepth(1))
                        .traverse(currentNode)
                        .nodes()) {

                    Pattern dataPattern = Pattern.compile("(?i)" + (String) currentNode.getProperty("pattern"));
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

                // Generate nodes for every wildcard
                for (int i = 0; i < matchDictionary.size(); i++) {

                    int counter = 0;
                    // Order by match count desc, limit 1
                    List<PatternCount> patternCounts = new ArrayList(matchDictionary.get(i + 1).values());
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
                                    currentNode.createRelationshipTo(leafNode, withName("NEXT"));
                                }

                                leafNode.setProperty("matches", patternCount.getDataNodes().size());
                                leafNode.setProperty("threshold", 5);
                                leafNode.setProperty("phrase", newTemplate);
                                leafNode.setProperty("depth", depth + 1);

                                // Bind new pattern to the data nodes it was generated from
                                patternCount.getDataNodes().forEach((dn) ->
                                {
                                    dataRelationshipManager.getOrCreateNode(leafNode.getId(), dn.getId(), db);
                                });
                            }
                            else {
                                recognizeMatch(db, leafNode, dataNode, label, depth + 1, nodeMatches, true);
                            }

                            tx.success();
                        }
                    }
                }
            }
            recognizeMatch(db, currentNode, dataNode, label, depth + 1, nodeMatches, false);
        }

        return  b;
    }

    /**
     * Gets a template representation of a RegEx string, making it easier to read a text pattern.
     * @param pattern The RegEx string to translate into a readable string template.
     * @return Returns a readable format of the RegEx, with {n} in place of wildcard matches.
     */
    public String GetTemplate(String pattern) {
        Pattern generalMatcher = Pattern.compile("\\(\\\\b\\[\\\\w'\\]\\+\\\\b\\)");
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
        Pattern generalMatcher = Pattern.compile("\\(\\\\b\\[\\\\w'\\]\\+\\\\b\\)");
        Matcher regexMatcher = generalMatcher.matcher(pattern);
        StringBuffer s = new StringBuffer();
        int counter = 0;

        while (regexMatcher.find()) {
            if (counter == i) {
                StringBuffer sb = new StringBuffer();
                sb.append("\\(\\\\b\\[\\\\w'\\]\\+\\\\b\\)");

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

