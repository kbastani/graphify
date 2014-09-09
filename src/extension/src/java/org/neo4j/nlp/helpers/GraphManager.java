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
import org.neo4j.nlp.abstractions.Manager;
import org.neo4j.nlp.impl.cache.PatternRelationshipCache;
import org.neo4j.nlp.impl.manager.DataRelationshipManager;
import org.neo4j.nlp.impl.manager.NodeManager;
import org.neo4j.nlp.models.PatternCount;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
public class GraphManager extends Manager {

    private final String label;
    private final String propertyKey;

    private UniqueFactory<Node> patternFactory;

    public static final Cache<String, Long> patternCache = CacheBuilder.newBuilder().maximumSize(20000000).build();
    public static final Cache<Long, String> inversePatternCache = CacheBuilder.newBuilder().maximumSize(20000000).build();
    public static final Cache<String, String> edgeCache = CacheBuilder.newBuilder().maximumSize(2000000).build();

    private final PatternRelationshipCache patternRelationshipCache;
    private final DataRelationshipManager dataRelationshipManager;
    private static final NodeManager nodeManager = new NodeManager();

    public static final int MIN_THRESHOLD = 5;
    public static final String WILDCARD_TEMPLATE = "\\(\\\\b\\[\\\\w'.-\\]\\+\\\\b\\)";
    public static final String ROOT_TEMPLATE = "(\\b[\\w'.-]+\\b)\\s(\\b[\\w'.-]+\\b)";

    public GraphManager(String label) {
        this.label = label;
        this.propertyKey = "pattern";
        patternRelationshipCache = new PatternRelationshipCache();
        dataRelationshipManager = new DataRelationshipManager();
    }

    public List<String> getNextLayer(Long nodeId, GraphDatabaseService db)
    {

        List<Long> getLongs = patternRelationshipCache
                .getRelationships(nodeId, db, this);

        List<String> patterns = new ArrayList<>();

        for(Long patternNode : getLongs)
        {
            String pattern = inversePatternCache.getIfPresent(patternNode);

            // Prime the cache
            if(pattern == null)
            {
                pattern = (String)nodeManager.getNodeProperty(patternNode, "pattern", db);
                inversePatternCache.put(patternNode, pattern);
            }

            patterns.add(pattern);
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
     * @param patternCount A vector of words containing sorted on frequency of matches from historic data.
     * @return Returns a new child pattern that is generated from the patternCount model.
     */
    public String GeneratePattern(PatternCount patternCount) {
        Pattern generalMatcher = Pattern.compile(WILDCARD_TEMPLATE);
        Matcher regexMatcher = generalMatcher.matcher(GraphManager.ROOT_TEMPLATE);
        StringBuffer s = new StringBuffer();
        int counter = 0;

        while (regexMatcher.find()) {
            if (counter == 0) {
                StringBuilder sb = new StringBuilder();
                sb.append(WILDCARD_TEMPLATE);

                regexMatcher.appendReplacement(s, (sb.toString() + ("\\\\s" + patternCount.getPattern())));
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