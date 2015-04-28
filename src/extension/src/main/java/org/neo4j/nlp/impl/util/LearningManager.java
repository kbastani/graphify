package org.neo4j.nlp.impl.util;

import org.neo4j.graphalgo.GraphAlgoFactory;
import org.neo4j.graphalgo.PathFinder;
import org.neo4j.graphdb.*;
import org.neo4j.helpers.collection.IteratorUtil;
import org.neo4j.nlp.helpers.GraphManager;
import org.neo4j.nlp.impl.cache.AffinityRelationshipCache;
import org.neo4j.nlp.impl.cache.ClassRelationshipCache;
import org.neo4j.nlp.impl.cache.PatternRelationshipCache;
import org.neo4j.nlp.impl.manager.ClassNodeManager;
import org.neo4j.nlp.impl.manager.DataNodeManager;
import org.neo4j.nlp.impl.manager.DataRelationshipManager;
import org.neo4j.nlp.impl.manager.NodeManager;
import org.neo4j.nlp.models.PatternCount;
import traversal.DecisionTree;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.neo4j.graphdb.DynamicRelationshipType.withName;

/**
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
public class LearningManager {

    private static final DataRelationshipManager dataRelationshipManager = new DataRelationshipManager();
    private static final ClassRelationshipCache classRelationshipCache = new ClassRelationshipCache();
    private static final AffinityRelationshipCache affinityRelationshipCache = new AffinityRelationshipCache();
    private static final PatternRelationshipCache patternRelationshipCache = new PatternRelationshipCache();
    private static final DataNodeManager dataNodeManager = new DataNodeManager();
    private static final ClassNodeManager classNodeManager = new ClassNodeManager();
    private static final NodeManager nodeManager = new NodeManager();

    public static void trainInput(List<String> inputs, List<String> labels, GraphManager graphManager,
                                  GraphDatabaseService db, DecisionTree<Long> tree)
    {
        VectorUtil.vectorSpaceModelCache.invalidateAll();

        // Get label node identifiers
        List<Long> labelNodeIds = new ArrayList<>();

        try(Transaction tx = db.beginTx()) {
            // Get or create label nodes and append the ID to the label node list
            labelNodeIds.addAll(labels
                    .stream()
                    .map(label -> nodeManager.getOrCreateNode(classNodeManager, label, db)
                            .getId())
                    .collect(Collectors.toList()));
            tx.success();
            tx.close();
        }

        Long dataNodeId;

        // Iterate through the inputs
        for (String input: inputs)
        {
            try(Transaction tx = db.beginTx()) {
                // Get data node
                dataNodeId = nodeManager.getOrCreateNode(dataNodeManager, input, db).getId();
                nodeManager.setNodeProperty(dataNodeId, "label", labels.toArray(new String[labels.size()]), db);
                tx.success();
            }

            Map<Long, Integer> patternMatchers = tree.traverseByPattern(input);

            // Get or create affinity relationships from matched patterns on the input
            // Affinity implementation must use an o(n^2) complexity, which is tough to get around
            // Get or create a bidirectional relationship between Pi<-->Pi
            // Rules: No affinity relationships for direct descendants, i.e. "OF" and "OF THE"
            // createAffinityRelationships(graphManager, db, tree, patternMatchers);

            for (Long nodeId : patternMatchers.keySet()) {

                Integer matchCount;
                Integer threshold;

                try(Transaction tx = db.beginTx()) {
                    // Get property
                    matchCount = (Integer) nodeManager.getNodeProperty(nodeId, "matches", db);

                    if (matchCount == null) {
                        matchCount = 0;
                        nodeManager.setNodeProperty(nodeId, "matches", matchCount, db);
                    }

                    // Set property
                    nodeManager.setNodeProperty(nodeId, "matches", matchCount + patternMatchers.get(nodeId), db);
                    tx.success();
                } catch (Exception ex) {
                    throw ex;
                }

                try(Transaction tx = db.beginTx()) {
                    // Get or create data relationship
                    dataRelationshipManager.getOrCreateNode(nodeId, dataNodeId, db);
                    tx.success();
                } catch (Exception ex) {
                    throw ex;
                }

                for (Long labelId : labelNodeIds) {
                    // Get or create class relationship
                    try(Transaction tx = db.beginTx()) {
                        classRelationshipCache.getOrCreateRelationship(nodeId, labelId, db, graphManager, false);
                        tx.success();
                    } catch (Exception ex) {
                        throw ex;
                    }
                }

                try(Transaction tx = db.beginTx()) {
                    // Check if the match count has exceeded the threshold
                    matchCount = (Integer) nodeManager.getNodeProperty(nodeId, "matches", db);
                    threshold = (Integer) nodeManager.getNodeProperty(nodeId, "threshold", db);
                    tx.success();
                } catch (Exception ex) {
                    throw ex;
                }

                try {
                    if (matchCount != null) {
                        if (matchCount > threshold) {
                            try (Transaction tx = db.beginTx()) {
                                // Set match count to 0
                                nodeManager.setNodeProperty(nodeId, "matches", 0, db);

                                // Increase threshold
                                nodeManager.setNodeProperty(nodeId, "threshold", (threshold / GraphManager.MIN_THRESHOLD) + (threshold), db);
                                tx.success();
                            } catch (Exception ex) {
                                throw ex;
                            }
                            // Populate a map of matched patterns
                            Map<Integer, Map<String, PatternCount>> matchDictionary = new HashMap<>();

                            try (Transaction tx = db.beginTx()) {
                                populatePatternMap(db, nodeId, matchDictionary);
                                tx.success();
                            } catch (Exception ex) {
                                throw ex;
                            }

                            // Generate nodes for every wildcard
                            generateChildPatterns(db, NodeManager.getNodeAsMap(nodeId, db), matchDictionary, graphManager);
                        }
                    }
                } catch(Exception ex) {
                    throw ex;
                }
            }
        }
    }

    /**
     * Experimental: Create affinity relationships between patterns that are encountered together during training.
     * @param graphManager is the global graph manager for managing an optimized cache of graph data
     * @param db is the Neo4j database service
     * @param tree is the decision tree for pattern matching
     * @param patternMatchers is the set of pattern matchers produced from the decision tree
     */
    private static void createAffinityRelationships(GraphManager graphManager, GraphDatabaseService db, DecisionTree<Long> tree, Map<Long, Integer> patternMatchers) {
        List<String> toFrom = new ArrayList<>();
        for (Long startId : patternMatchers.keySet()) {
            // Get or create the affinity relationship
            try(Transaction tx = db.beginTx()) {
                patternMatchers.keySet().stream().filter(endId -> startId != endId).forEach(endId -> {
                    Node startNode = db.getNodeById(startId > endId ? endId : startId);
                    Node endNode = db.getNodeById(startId > endId ? startId : endId);
                    String key = startNode.getId() + "_" + endNode.getId();

                    // If startNode matches the end node
                    Pattern generalMatcher = Pattern.compile((String) startNode.getProperty("pattern"));
                    Matcher regexMatcher = generalMatcher.matcher(((String) endNode.getProperty("phrase")).replaceAll("(\\{[01]\\})", "word"));

                    if (!toFrom.contains(key) && !regexMatcher.find()) {
                        // Depth of the start node must be > 2
                        PathFinder<Path> depthFinder = GraphAlgoFactory.shortestPath(PathExpanders.forTypeAndDirection(withName("NEXT"), Direction.OUTGOING), 100);
                        int depth1 = IteratorUtil.count(depthFinder.findSinglePath(db.getNodeById(tree.root()), startNode).nodes().iterator());
                        int depth2 = IteratorUtil.count(depthFinder.findSinglePath(db.getNodeById(tree.root()), endNode).nodes().iterator());

                        if (depth1 > 3 && depth2 > 3 && depth2 > depth1) {
                            // Eliminate descendant
                            PathFinder<Path> finder = GraphAlgoFactory.shortestPath(PathExpanders.forTypeAndDirection(withName("NEXT"), Direction.OUTGOING), 100);
                            Path findPath = finder.findSinglePath(startNode, endNode);

                            if (findPath == null) {
                                toFrom.add(key);
                                // Get or create the affinity relationship
                                affinityRelationshipCache.getOrCreateRelationship(startNode.getId(), endNode.getId(), db, graphManager, true);
                            }
                        }
                    }
                });
                tx.success();
                tx.close();
            } catch (Exception ex) {
                throw ex;
            }
        }
    }

    /**
     * Generates child patterns the inherit the parent's base code but extending it for every wildcard with a new
     * pattern that is most available in previously matched data.
     * @param db The Neo4j GraphDatabaseService that contains the pattern recognition hierarchy.
     * @param currentNode The current pattern node that the recursive pattern recognition algorithm is operating on within the hierarchy.
     * @param matchDictionary A map that has been populated with new patterns extracted from data attached to the base pattern.
     */
    private static void generateChildPatterns(GraphDatabaseService db, Map<String, Object> currentNode, Map<Integer, Map<String, PatternCount>> matchDictionary, GraphManager graphManager) {
        for (int i = 0; i < matchDictionary.size(); i++) {

            int counter = 0;
            // Order by match count desc, limit 1
            List<PatternCount> patternCounts = new ArrayList<>(matchDictionary.get(i + 1).values());
            Collections.sort(patternCounts, (o1, o2) -> o2.getCount() - o1.getCount());
            patternCounts = patternCounts.stream()
                    .filter((pc) -> pc.getCount() > 1 || pc.getPattern().equals("1"))
                    .collect(Collectors.toList());

            if(patternCounts.size() > 0) {
                PatternCount patternCount = (PatternCount) patternCounts.toArray()[counter];
                String pattern = (String) currentNode.get("pattern");
                String newPattern = GeneratePattern(i, patternCount, pattern);
                String newTemplate = GetTemplate(newPattern);

                boolean patternFinish = true;

                while (patternFinish) {

                    patternCount = (PatternCount) patternCounts.toArray()[counter];
                    pattern = (String) currentNode.get("pattern");
                    newPattern = GeneratePattern(i, patternCount, pattern);
                    newTemplate = GetTemplate(newPattern);
                    counter++;
                    Node existingNode;
                    try (Transaction tx = db.beginTx()) {
                        existingNode = nodeManager.getOrCreateNode(graphManager, newPattern, db);
                        tx.success();
                    }

                    if (existingNode != null) {
                        try (Transaction tx = db.beginTx()) {
                            patternFinish = existingNode.hasProperty("matches");
                            tx.success();
                        }
                    }
                    patternFinish = patternFinish && counter < (patternCounts.size() - 1);


                }

                Node leafNode;
                boolean hasMatches;
                try(Transaction tx = db.beginTx()) {
                    leafNode = nodeManager.getOrCreateNode(graphManager, newPattern, db);
                    hasMatches = !leafNode.hasProperty("matches");
                    tx.success();
                }

                if (hasMatches) {
                    try (Transaction tx = db.beginTx()) {
                        mapBranchToLeaf(db, currentNode, graphManager, leafNode);
                        tx.success();
                    }

                    try (Transaction tx = db.beginTx()) {
                        Long leafNodeId = leafNode.getId();
                        nodeManager.setNodeProperty(leafNodeId, "matches", patternCount.getDataNodes().size(), db);
                        nodeManager.setNodeProperty(leafNodeId, "threshold", GraphManager.MIN_THRESHOLD, db);
                        nodeManager.setNodeProperty(leafNodeId, "phrase", newTemplate, db);
                        tx.success();
                    }

                    // Bind new pattern to the data nodes it was generated from
                    patternCount.getDataNodes().forEach((dn) ->
                    {
                        String[] dataLabels = (String[]) dn.get("label");
                        for (String labelName : dataLabels) {
                            Node labelNode;
                            Long leafNodeId;
                            Long labelNodeId;

                            try (Transaction tx = db.beginTx()) {
                                labelNode = nodeManager.getOrCreateNode(classNodeManager, labelName, db);
                                leafNodeId = leafNode.getId();
                                labelNodeId = labelNode.getId();
                                tx.success();
                            }
                            classRelationshipCache.getOrCreateRelationship(leafNodeId, labelNodeId, db, graphManager, false);
                        }
                        try (Transaction tx = db.beginTx()) {
                            dataRelationshipManager.getOrCreateNode(leafNode.getId(), (Long) dn.get("id"), db);
                            tx.success();
                        }
                    });
                }
            }
        }
    }

    public static void mapBranchToLeaf(GraphDatabaseService db, Map<String, Object> currentNode, GraphManager graphManager, Node leafNode) {
        if (GraphManager.edgeCache.getIfPresent((String.valueOf(leafNode.getId()))) == null) {
            GraphManager.edgeCache.put((String.valueOf(leafNode.getId())), (String.valueOf(leafNode.getId())));
        }

        if (GraphManager.edgeCache.getIfPresent(currentNode.get("id") + "->" + (int) leafNode.getId()) == null) {
            patternRelationshipCache.getOrCreateRelationship((Long) currentNode.get("id"), leafNode.getId(), db, graphManager, false);
        }
    }

    /**
     * Creates a pattern matching dictionary for all data attached to a pattern.
     * @param db The Neo4j GraphDatabaseService that contains the pattern recognition hierarchy.
     * @param currentNode The current pattern node that the recursive pattern recognition algorithm is operating on within the hierarchy.
     * @param matchDictionary A map that will be populated with new patterns extracted from data using the base pattern.
     */
    private static void populatePatternMap(GraphDatabaseService db, Long currentNode, Map<Integer, Map<String, PatternCount>> matchDictionary) {

        String pattern = (String)nodeManager.getNodeProperty(currentNode, "pattern", db);
        List<Long> nodes = dataRelationshipManager.getDataNodesById(currentNode);
        List<Map<String, Object>> matchNodes = new ArrayList<>();

        for(Long nodeId : nodes)
        {
            Map<String, Object> dataValue = nodeManager.getNodeAsMap(nodeId, db);
            if(dataValue != null)
            {
                matchNodes.add(dataValue);
            }
        }

        for (Map<String, Object> value : matchNodes)
        {
            Pattern dataPattern = Pattern.compile("(?i)" + pattern);
            Matcher dataMatch = dataPattern.matcher((String)value.get("value"));

            // Create leaf nodes
            while (dataMatch.find()) {
                for (int i = 1; i <= dataMatch.groupCount(); i++) {
                    String groupMatch = dataMatch.group(i).toLowerCase();
                    if (!matchDictionary.containsKey(i)) {
                        matchDictionary.put(i, new HashMap<>());
                        matchDictionary.get(i).put(groupMatch, new PatternCount(groupMatch, 1, value));
                    } else {
                        if (!matchDictionary.get(i).containsKey(groupMatch)) {
                            matchDictionary.get(i).put(groupMatch, new PatternCount(groupMatch, 1, value));
                        } else {
                            PatternCount patternCount = matchDictionary.get(i).get(groupMatch);
                            patternCount.setCount(patternCount.getCount() + 1);
                            patternCount.addDataNode(value);
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
    private static String GetTemplate(String pattern) {
        Pattern generalMatcher = Pattern.compile(GraphManager.WILDCARD_TEMPLATE);
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
    private static String GeneratePattern(int i, PatternCount patternCount, String pattern) {
        Pattern generalMatcher = Pattern.compile(GraphManager.WILDCARD_TEMPLATE);
        Matcher regexMatcher = generalMatcher.matcher(pattern);
        StringBuffer s = new StringBuffer();
        int counter = 0;

        while (regexMatcher.find()) {
            if (counter == i) {
                StringBuilder sb = new StringBuilder();
                sb.append(GraphManager.WILDCARD_TEMPLATE);

                regexMatcher.appendReplacement(s, ((counter == 0) ? sb.toString() + ("\\\\s" + patternCount.getPattern()) : (patternCount.getPattern() + "\\\\s") + sb.toString()));
            } else {
                regexMatcher.appendReplacement(s, regexMatcher.group().replace("\\", "\\\\"));
            }
            counter++;
        }
        regexMatcher.appendTail(s);

        return s.toString();
    }
}
