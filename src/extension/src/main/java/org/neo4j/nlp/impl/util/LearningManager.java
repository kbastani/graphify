package org.neo4j.nlp.impl.util;

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
import org.neo4j.nlp.models.PatternCount;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
    private static final PatternRelationshipCache patternRelationshipCache = new PatternRelationshipCache();
    private static final DataNodeManager dataNodeManager = new DataNodeManager();
    private static final ClassNodeManager classNodeManager = new ClassNodeManager();
    private static final NodeManager nodeManager = new NodeManager();

    public static void trainInput(List<String> inputs, List<String> labels, GraphManager graphManager, GraphDatabaseService db)
    {
        VectorUtil.vectorSpaceModelCache.invalidateAll();

        // Get label node identifiers
        List<Long> labelNodeIds = new ArrayList<>();

        // Get or create label nodes and append the ID to the label node list
        labelNodeIds.addAll(labels
                .stream()
                .map(label -> nodeManager.getOrCreateNode(classNodeManager, label, db)
                        .getId())
                .collect(Collectors.toList()));

        // Iterate through the inputs
        for (String input: inputs)
        {
            Transaction tx = db.beginTx();
            // Get data node
            Long dataNodeId = nodeManager.getOrCreateNode(dataNodeManager, input, db).getId();
            nodeManager.setNodeProperty(dataNodeId, "label", labels.toArray(new String[labels.size()]), db);

            Map<Long, Integer> patternMatchers = PatternMatcher.match(GraphManager.ROOT_TEMPLATE, input, db, graphManager);

            tx.success();
            tx.close();

            tx = db.beginTx();

            for (Long nodeId : patternMatchers.keySet()) {
                // Get property
                Integer matchCount = (Integer) nodeManager.getNodeProperty(nodeId, "matches", db);

                if (matchCount == null) {
                    matchCount = 0;
                    nodeManager.setNodeProperty(nodeId, "matches", matchCount, db);
                }


                // Set property
                nodeManager.setNodeProperty(nodeId, "matches", matchCount + patternMatchers.get(nodeId), db);

                // Get or create data relationship
                dataRelationshipManager.getOrCreateNode(nodeId, dataNodeId, db);

                for (Long labelId : labelNodeIds) {
                    // Get or create class relationship
                    classRelationshipCache.getOrCreateRelationship(nodeId, labelId, db, graphManager);
                }

                // Check if the match count has exceeded the threshold
                matchCount = (Integer) nodeManager.getNodeProperty(nodeId, "matches", db);
                Integer threshold = (Integer) nodeManager.getNodeProperty(nodeId, "threshold", db);


                if (matchCount > threshold) {
                    // Set match count to 0
                    nodeManager.setNodeProperty(nodeId, "matches", 0, db);

                    // Increase threshold
                    nodeManager.setNodeProperty(nodeId, "threshold", (threshold / GraphManager.MIN_THRESHOLD) + (threshold), db);

                    // Populate a map of matched patterns
                    Map<Integer, Map<String, PatternCount>> matchDictionary = new HashMap<>();
                    populatePatternMap(db, nodeId, matchDictionary);

                    // Generate nodes for every wildcard
                    generateChildPatterns(db, nodeManager.getNodeAsMap(nodeId, db), matchDictionary, graphManager);
                }

            }

            tx.success();
            tx.close();
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

                while (nodeManager.getOrCreateNode(graphManager, newPattern, db).hasProperty("matches") && counter < (patternCounts.size() - 1)) {
                    patternCount = (PatternCount) patternCounts.toArray()[counter];
                    pattern = (String) currentNode.get("pattern");
                    newPattern = GeneratePattern(i, patternCount, pattern);
                    newTemplate = GetTemplate(newPattern);
                    counter++;
                }

                Node leafNode = nodeManager.getOrCreateNode(graphManager, newPattern, db);

                if (!leafNode.hasProperty("matches")) {
                    if (GraphManager.edgeCache.getIfPresent((String.valueOf(leafNode.getId()))) == null) {
                        GraphManager.edgeCache.put((String.valueOf(leafNode.getId())), (String.valueOf(leafNode.getId())));
                    }

                    if (GraphManager.edgeCache.getIfPresent(currentNode.get("id") + "->" + (int) leafNode.getId()) == null) {
                        patternRelationshipCache.getOrCreateRelationship((Long) currentNode.get("id"), leafNode.getId(), db, graphManager);
                    }

                    Long leafNodeId = leafNode.getId();
                    nodeManager.setNodeProperty(leafNodeId, "matches", patternCount.getDataNodes().size(), db);
                    nodeManager.setNodeProperty(leafNodeId, "threshold", GraphManager.MIN_THRESHOLD, db);
                    nodeManager.setNodeProperty(leafNodeId, "phrase", newTemplate, db);

                    // Bind new pattern to the data nodes it was generated from
                    patternCount.getDataNodes().forEach((dn) ->
                    {
                        String[] dataLabels = (String[]) dn.get("label");
                        for (String labelName : dataLabels) {
                            Node labelNode = nodeManager.getOrCreateNode(classNodeManager, labelName, db);
                            classRelationshipCache.getOrCreateRelationship(leafNode.getId(), labelNode.getId(), db, graphManager);
                        }
                        dataRelationshipManager.getOrCreateNode(leafNode.getId(), (Long) dn.get("id"), db);
                    });
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
