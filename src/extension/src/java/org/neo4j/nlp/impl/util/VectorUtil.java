package org.neo4j.nlp.impl.util;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.javalite.common.Convert;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.Evaluators;
import org.neo4j.nlp.helpers.GraphManager;
import org.neo4j.tooling.GlobalGraphOperations;

import java.util.*;
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
public class VectorUtil {

    public static final Cache<String, Object> vectorSpaceModelCache = CacheBuilder.newBuilder().maximumSize(20000000).build();

    private static Double dotProduct(List<Double> v1, List<Double> v2)
    {
        if(v1.size() != v2.size())
            throw new IllegalArgumentException("Vectors must be of equal length.");

        Double result = 0.0;
        for (int i = 0; i < v1.size(); i++) {
            result = result + (v1.get(i) * v2.get(i));
        }

        return result;
    }

    private static double normVector(List<Double> v1)
    {
        double result = 0.0;

        for (Double aV1 : v1) {
            result = result + (aV1 * aV1);
        }

        return Math.sqrt(result);
    }

    public static double cosineSimilarity(List<Double> v1, List<Double> v2)
    {
        // Get the dot product
        Double dp = dotProduct(v1, v2);

        // Get the norm vector
        double nv = normVector(v1) * normVector(v2);

        return dp / nv;
    }

    private static List<Double> getFeatureVector(GraphDatabaseService db, GraphManager graphManager, String input, List<Integer> featureIndexList) {
        Map<Long, Integer> patternMatchers = PatternMatcher.match(GraphManager.ROOT_TEMPLATE, input, db, graphManager);

        List<Integer> longs = new ArrayList<>();
        Collections.addAll(longs, patternMatchers.keySet().stream().map(Long::intValue).collect(Collectors.toList()).toArray(new Integer[longs.size()]));

        // Get the total number of recognized features
        Integer sum = patternMatchers.values().stream().mapToInt(Integer::intValue).sum();

        //
        return featureIndexList.stream().map(i -> longs.contains(i) ? (patternMatchers.get(i.longValue()).doubleValue() / sum.doubleValue()) : 0.0).collect(Collectors.toList());
    }

    private static List<Integer> getFeatureIndexList(GraphDatabaseService db) {
        Transaction tx = db.beginTx();
        // Get classes using Java API
        final List<Node> patterns = new ArrayList<>();
        GlobalGraphOperations.at(db)
                .getAllNodesWithLabel(DynamicLabel.label("Pattern"))
                .forEach(a -> patterns.add(a));

        Collections.sort(patterns, (a, b) -> ((Integer)b.getProperty("threshold")).compareTo(((Integer)a.getProperty("threshold"))));

        List<Integer> patternIds = patterns.stream().map(a -> ((Long)a.getId()).intValue()).collect(Collectors.toList());

        tx.success();
        tx.close();

        return patternIds;
    }

    public static Map<String, Object> getCosineSimilarityVector(GraphDatabaseService db)
    {
        Map<String, List<Integer>> documents = getFeaturesForAllClasses(db);
        Map<String, List<LinkedHashMap<String, Object>>> results = new HashMap<>();
        List<Integer> featureIndexList = getFeatureIndexList(db);

        List<String> documentList = documents.keySet().stream().collect(Collectors.toList());

        Collections.sort(documentList, (a, b) -> a.compareToIgnoreCase(b));

        for(String key : documentList)
        {
            List<LinkedHashMap<String, Object>> resultList = new ArrayList<>();
            LinkedHashMap<String, Double> classMap = new LinkedHashMap<>();
            List<Double> v1 = featureIndexList.stream().map(i -> documents.get(key).contains(i) ? 1.0 : 0.0).collect(Collectors.toList());
            documents.keySet().stream().forEach(otherKey -> {

                List<Double> v2 = featureIndexList.stream().map(i -> documents.get(otherKey).contains(i) ? ((Integer) featureIndexList.indexOf(i)).doubleValue() : 0.0).collect(Collectors.toList());
                classMap.put(otherKey, key.equals(otherKey) ? 0.0 : cosineSimilarity(v1, v2));
            });

            final List<LinkedHashMap<String, Object>> finalResultList = resultList;
            classMap.keySet().forEach(ks -> {
                LinkedHashMap<String, Object> localMap = new LinkedHashMap<>();
                localMap.put("class", ks);
                localMap.put("similarity", classMap.get(ks));
                finalResultList.add(localMap);
            });

            Collections.sort(finalResultList, (a, b) -> ((String)a.get("class")).compareToIgnoreCase((String)b.get("class")));
            results.put(key, finalResultList);
        }

        List<LinkedHashMap<String, Object>> similarityVector = new ArrayList<>();

        for(String key : results.keySet())
        {
            List<Double> cosineVector;
            cosineVector = results.get(key).stream().map(a -> Convert.toDouble(Math.round(100000 * (Double) a.get("similarity")))).collect(Collectors.toList());
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("class", key);
            row.put("vector", cosineVector);
            similarityVector.add(row);
        }

        Collections.sort(similarityVector, (a, b) -> ((String)a.get("class")).compareToIgnoreCase((String)b.get("class")));

        Map<String, Object> vectorMap = new LinkedHashMap<>();

        List<ArrayList<Double>> vectors = new ArrayList<>();
        List<String> classNames = new ArrayList<>();

        for(LinkedHashMap<String, Object> val : similarityVector)
        {
            vectors.add((ArrayList<Double>)val.get("vector"));
            classNames.add((String)val.get("class"));
        }

        vectorMap.put("classes", classNames);
        vectorMap.put("vectors", vectors);

        return vectorMap;
    }

    public static Map<String, List<LinkedHashMap<String, Object>>> similarDocumentMapForVector(GraphDatabaseService db, GraphManager graphManager, String input) {
        Object cfIndex = vectorSpaceModelCache.getIfPresent("CLASS_FEATURE_INDEX");
        Object vsmIndex = vectorSpaceModelCache.getIfPresent("GLOBAL_FEATURE_INDEX");

        Map<String, List<Integer>> documents;
        Map<String, List<LinkedHashMap<String, Object>>> results = new HashMap<>();
        List<Integer> featureIndexList;

        if(cfIndex != null)
        {
            documents = (Map<String, List<Integer>>) cfIndex;
        }
        else
        {
            documents = getFeaturesForAllClasses(db);
            vectorSpaceModelCache.put("CLASS_FEATURE_INDEX", documents);
        }

        if(vsmIndex != null)
        {
            featureIndexList = (List<Integer>) vsmIndex;
        }
        else
        {
            featureIndexList = getFeatureIndexList(db);
            vectorSpaceModelCache.put("GLOBAL_FEATURE_INDEX", featureIndexList);
        }

        List<Double> features = getFeatureVector(db, graphManager, input, featureIndexList);

        List<LinkedHashMap<String, Object>> resultList = new ArrayList<>();
        LinkedHashMap<String, Double> classMap = new LinkedHashMap<>();

        documents.keySet().stream().forEach(otherKey -> {
            List<Double> v2 = featureIndexList.stream().map(i -> documents.get(otherKey).contains(i) ? 1.0 : 0.0).collect(Collectors.toList());
            classMap.put(otherKey, cosineSimilarity(features, v2));
        });

        classMap.keySet().parallelStream().forEach(ks -> {
            LinkedHashMap<String, Object> localMap = new LinkedHashMap<>();
            localMap.put("class", ks);
            localMap.put("similarity", classMap.get(ks));
            resultList.add(localMap);
        });

        try {
            resultList.sort((a, b) ->
            {
                Double diff = (((double) a.get("similarity")) - ((double) b.get("similarity")));
                return diff > 0 ? -1 : diff.equals(0.0) ? 0 : 1;
            });
        }
        catch(NullPointerException ex) {
            // resultList is empty or null
        }

        results.put("classes", resultList);

        return results;
    }

    public static Map<String, List<LinkedHashMap<String, Object>>> similarDocumentMapForClass(GraphDatabaseService db, String className) {
        Object cfIndex = vectorSpaceModelCache.getIfPresent("CLASS_FEATURE_INDEX");
        Object vsmIndex = vectorSpaceModelCache.getIfPresent("GLOBAL_FEATURE_INDEX");

        Map<String, List<Integer>> documents;
        Map<String, List<LinkedHashMap<String, Object>>> results = new HashMap<>();
        List<Integer> featureIndexList;

        if(cfIndex != null)
        {
            documents = (Map<String, List<Integer>>) cfIndex;
        }
        else
        {
            documents = getFeaturesForAllClasses(db);
            vectorSpaceModelCache.put("CLASS_FEATURE_INDEX", documents);
        }

        if(vsmIndex != null)
        {
            featureIndexList = (List<Integer>) vsmIndex;
        }
        else
        {
            featureIndexList = getFeatureIndexList(db);
            vectorSpaceModelCache.put("GLOBAL_FEATURE_INDEX", featureIndexList);
        }

        final String key = className;

        List<LinkedHashMap<String, Object>> resultList = new ArrayList<>();
        LinkedHashMap<String, Double> classMap = new LinkedHashMap<>();

        List<Double> v1 = featureIndexList.stream().map(i -> documents.get(key).contains(i) ? ((Integer)featureIndexList.indexOf(i)).doubleValue() : 0.0).collect(Collectors.toList());


        documents.keySet().stream().filter(otherKey -> !key.equals(otherKey)).parallel().forEach(otherKey -> {
            List<Double> v2 = featureIndexList.stream().parallel().map(i -> documents.get(otherKey).contains(i) ? ((Integer)featureIndexList.indexOf(i)).doubleValue() : 0.0).collect(Collectors.toList());
            classMap.put(otherKey, cosineSimilarity(v1, v2));
        });

        classMap.keySet().forEach(ks -> {
            if (!ks.equals(key)) {
                LinkedHashMap<String, Object> localMap = new LinkedHashMap<>();
                localMap.put("class", ks);
                localMap.put("similarity", classMap.get(ks));
                resultList.add(localMap);
            }
        });

        resultList.sort((a, b) ->
        {
            Double diff = (((double) a.get("similarity")) - ((double) b.get("similarity")));
            return diff > 0 ? -1 : diff.equals(0.0) ? 0 : 1;
        });

        results.put("classes", resultList);

        return results;
    }

    private static Map<String, List<Integer>> getFeaturesForAllClasses(GraphDatabaseService db)
    {
        List<Node> classes = getAllClasses(db);

        Map<String, List<Integer>> featureMap = new HashMap<>();

        Transaction tx = db.beginTx();
        for (Node thisClass : classes)
        {
            featureMap.put((String)thisClass.getProperty("name"), getFeaturesForClass(db, thisClass));
        }
        tx.success();
        tx.close();

        return featureMap;
    }

    private static List<Node> getAllClasses(GraphDatabaseService db) {
        Transaction tx = db.beginTx();
        // Get classes using Java API
        final List<Node> finalClasses = new ArrayList<>();
        GlobalGraphOperations.at(db)
                .getAllNodesWithLabel(DynamicLabel.label("Class"))
                .forEach(a -> finalClasses.add(a));
        tx.success();
        tx.close();

        return finalClasses.stream().map(a -> a).collect(Collectors.toList());
    }

    private static List<Integer> getFeaturesForClass(GraphDatabaseService db, Node classNode) {
        List<Integer> patternIds = new ArrayList<>();
        for (Node endNodes : db.traversalDescription()
                .depthFirst()
                .relationships(withName("HAS_CLASS"), Direction.INCOMING)
                .evaluator(Evaluators.fromDepth(1))
                .evaluator(Evaluators.toDepth(1))
                .traverse(classNode)
                .nodes()) {
            patternIds.add(((Long)endNodes.getId()).intValue());
        }
        return patternIds;
    }
}
