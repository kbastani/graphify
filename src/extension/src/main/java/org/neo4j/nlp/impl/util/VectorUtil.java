package org.neo4j.nlp.impl.util;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.javalite.common.Convert;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.Evaluators;
import org.neo4j.helpers.collection.IteratorUtil;
import org.neo4j.nlp.helpers.GraphManager;
import org.neo4j.nlp.impl.manager.NodeManager;
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

    public static double tfidf(GraphDatabaseService db, Long featureId, Long classId)
    {
        double tfidf;

        double tf = getTermFrequencyForDocument(db, featureId, classId);
        double idf = getInverseDocumentFrequency(db, featureId);

        tfidf = tf * idf;

        return tfidf;
    }

    public static double getInverseDocumentFrequency(GraphDatabaseService db, Long featureId)
    {
        Double idf;

        Double d = ((Integer)getDocumentSize(db)).doubleValue();
        Double dt = ((Integer)getDocumentSizeForFeature(db, featureId)).doubleValue();

        idf = Math.log(d / dt);

        return idf;
    }

    public static Map<Long, Integer> getTermFrequencyMapForDocument(GraphDatabaseService db, Long classId)
    {
        Map<Long, Integer> termDocumentMatrix;

        String cacheKey = "TERM_DOCUMENT_FREQUENCY_" + classId;

        if(vectorSpaceModelCache.getIfPresent(cacheKey) == null) {
            Node classNode = db.getNodeById(classId);

            termDocumentMatrix = new HashMap<>();

            IteratorUtil.asCollection(db.traversalDescription()
                    .depthFirst()
                    .relationships(withName("HAS_CLASS"), Direction.INCOMING)
                    .evaluator(Evaluators.fromDepth(1))
                    .evaluator(Evaluators.toDepth(1))
                    .traverse(classNode)).stream()
                    .forEach(p ->
                            termDocumentMatrix.put(p.endNode().getId(), (Integer) p.lastRelationship().getProperty("matches")));

            vectorSpaceModelCache.put(cacheKey, termDocumentMatrix);
        }
        else
        {
            termDocumentMatrix =  (Map<Long, Integer>)vectorSpaceModelCache.getIfPresent(cacheKey);
        }

        return termDocumentMatrix;
    }

    public static int getTermFrequencyForDocument(GraphDatabaseService db, Long featureId, Long classId)
    {
        int frequency = getTermFrequencyMapForDocument(db, classId).get(featureId);
        return frequency;
    }

    public static int getDocumentSize(GraphDatabaseService db)
    {
        int documentSize;
        String cacheKey = "GLOBAL_DOCUMENT_SIZE";
        if(vectorSpaceModelCache.getIfPresent(cacheKey) == null) {
            documentSize = IteratorUtil.count(GlobalGraphOperations.at(db).getAllNodesWithLabel(DynamicLabel.label("Class")));
            vectorSpaceModelCache.put(cacheKey, documentSize);
        }
        else
        {
            documentSize = (Integer)vectorSpaceModelCache.getIfPresent(cacheKey);
        }
        return documentSize;
    }

    public static int getDocumentSizeForFeature(GraphDatabaseService db, Long id)
    {
        int documentSize;

        String cacheKey = "DOCUMENT_SIZE_FEATURE_" + id;

        if(vectorSpaceModelCache.getIfPresent(cacheKey) == null) {
            Node startNode = db.getNodeById(id);

            Iterator<Node> classes = db.traversalDescription()
                    .depthFirst()
                    .relationships(withName("HAS_CLASS"), Direction.OUTGOING)
                    .evaluator(Evaluators.fromDepth(1))
                    .evaluator(Evaluators.toDepth(1))
                    .traverse(startNode)
                    .nodes().iterator();

            documentSize = IteratorUtil.count(classes);

            vectorSpaceModelCache.put(cacheKey, documentSize);
        }
        else
        {
            documentSize = (Integer)vectorSpaceModelCache.getIfPresent(cacheKey);
        }

        return documentSize;
    }

    public static List<LinkedHashMap<String, Object>> getFeatureFrequencyMap(GraphDatabaseService db, String text, GraphManager graphManager) {
        // This method trains a model on a supplied label and text content
        Map<Long, Integer> patternMatchers = PatternMatcher.match(GraphManager.ROOT_TEMPLATE, text, db, graphManager);

        // Translate map to phrases
        List<LinkedHashMap<String, Object>> results = patternMatchers.keySet().stream()
                .map(a ->
                {
                    LinkedHashMap<String, Object> linkHashMap = new LinkedHashMap<>();
                    linkHashMap.put("feature", a.intValue());
                    linkHashMap.put("frequency", patternMatchers.get(a));
                    return linkHashMap;
                })
                .collect(Collectors.toList());

        results.sort((a, b) ->
        {
            Integer diff = ((Integer)a.get("frequency")) - ((Integer)b.get("frequency"));
            return diff > 0 ? -1 : diff.equals(0) ? 0 : 1;
        });

        return results;
    }

    public static List<LinkedHashMap<String, Object>> getPhrases(GraphDatabaseService db, String text, GraphManager graphManager) {
        // This method trains a model on a supplied label and text content
        Map<Long, Integer> patternMatchers = PatternMatcher.match(GraphManager.ROOT_TEMPLATE, text, db, graphManager);

        // Translate map to phrases
        List<LinkedHashMap<String, Object>> results = patternMatchers.keySet().stream()
                .map(a ->
                {
                    LinkedHashMap<String, Object> linkHashMap = new LinkedHashMap<>();
                    linkHashMap.put("feature", NodeManager.getNodeFromGlobalCache(a).get("phrase"));
                    linkHashMap.put("frequency", patternMatchers.get(a));
                    return linkHashMap;
                })
                .collect(Collectors.toList());

        results.sort((a, b) ->
        {
            Integer diff = ((Integer)a.get("frequency")) - ((Integer)b.get("frequency"));
            return diff > 0 ? -1 : diff.equals(0) ? 0 : 1;
        });

        return results;
    }

    private static List<Double> getFeatureVector(GraphDatabaseService db, GraphManager graphManager, String input, List<Integer> featureIndexList) {
        List<LinkedHashMap<String, Object>> featureFrequencyMap = getFeatureFrequencyMap(db, input, graphManager);

        List<Integer> longs = featureFrequencyMap.stream().map(a -> (Integer)a.get("feature")).collect(Collectors.toList());

//        ((Integer) featureFrequencyMap.stream()
//                .filter(a -> (a.get("feature")).equals(i))
//                .collect(Collectors.toList()).get(0).get("frequency")).doubleValue()

        return featureIndexList.stream().map(i -> longs.contains(i) ?
                1.0
                :
                0.0).collect(Collectors.toList());
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
        Map<String, List<LinkedHashMap<String, Object>>> documents = getFeaturesForAllClasses(db);
        Map<String, List<LinkedHashMap<String, Object>>> results = new HashMap<>();
        List<Integer> featureIndexList = getFeatureIndexList(db);

        List<String> documentList = documents.keySet().stream().collect(Collectors.toList());

        Collections.sort(documentList, (a, b) -> a.compareToIgnoreCase(b));

        for(String key : documentList)
        {
            List<LinkedHashMap<String, Object>> resultList = new ArrayList<>();
            LinkedHashMap<String, Double> classMap = new LinkedHashMap<>();

            List<Double> v1 = featureIndexList.stream().map(i -> documents.get(key).contains(i) ? featureIndexList.indexOf(i) : 0.0).collect(Collectors.toList());
            documents.keySet().stream().forEach(otherKey -> {

                List<Double> v2 = featureIndexList.stream().map(i -> documents.get(otherKey).contains(i) ? featureIndexList.indexOf(i) : 0.0).collect(Collectors.toList());
                classMap.put(otherKey, cosineSimilarity(v1, v2));
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
        Map<String, List<LinkedHashMap<String, Object>>> documents;
        Map<String, List<LinkedHashMap<String, Object>>> results = new HashMap<>();
        List<Integer> featureIndexList;

        VsmCacheModel vsmCacheModel = new VsmCacheModel(db).invoke();
        featureIndexList = vsmCacheModel.getFeatureIndexList();
        documents = vsmCacheModel.getDocuments();

        List<Double> features = getFeatureVector(db, graphManager, input, featureIndexList);

        List<LinkedHashMap<String, Object>> resultList = new ArrayList<>();
        LinkedHashMap<String, Double> classMap = new LinkedHashMap<>();

        documents.keySet().stream().forEach(otherKey -> {
            List<Double> v2 = getWeightVectorForClass(documents, otherKey, featureIndexList, db);
            classMap.put(otherKey, cosineSimilarity(v2, features));
        });

        classMap.keySet().stream().forEach(ks -> {
            if(classMap.get(ks) > 0.0) {
                LinkedHashMap<String, Object> localMap = new LinkedHashMap<>();
                localMap.put("class", ks);
                localMap.put("similarity", classMap.get(ks));
                resultList.add(localMap);
            }
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


        Map<String, List<LinkedHashMap<String, Object>>> documents;
        Map<String, List<LinkedHashMap<String, Object>>> results = new HashMap<>();
        List<Integer> featureIndexList;

        VsmCacheModel vsmCacheModel = new VsmCacheModel(db).invoke();
        featureIndexList = vsmCacheModel.getFeatureIndexList();
        documents = vsmCacheModel.getDocuments();

        final String key = className;

        List<LinkedHashMap<String, Object>> resultList = new ArrayList<>();
        LinkedHashMap<String, Double> classMap = new LinkedHashMap<>();

        List<Double> v1 = getFeatureVectorForDocumentClass(documents, featureIndexList, key);

        documents.keySet().stream().filter(otherKey -> !key.equals(otherKey)).forEach(otherKey -> {
            List<Double> v2 = getBinaryFeatureVectorForDocumentClass(documents, featureIndexList, otherKey);
            classMap.put(otherKey, cosineSimilarity(v1, v2));
        });

        classMap.keySet().forEach(ks -> {
            if (!ks.equals(key) && classMap.get(ks) > 0.0) {
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

    private static List<Double> getWeightVectorForClass(Map<String, List<LinkedHashMap<String, Object>>> documents, String key, List<Integer> featureIndexList, GraphDatabaseService db) {
        List<Double> weightVector;

        Transaction tx = db.beginTx();
        // Get class id
        Long classId = db.findNodesByLabelAndProperty(DynamicLabel.label("Class"), "name", key).iterator().next().getId();

        // Get weight vector for class
        List<Long> longs = documents.get(key)
                .stream()
                .map(a -> ((Integer)a.get("feature")).longValue())
                .collect(Collectors.toList());

        weightVector = featureIndexList.stream().map(i -> longs.contains(i.longValue()) ?
                tfidf(db, i.longValue(), classId) : 0.0)
                .collect(Collectors.toList());
        tx.success();
        tx.close();
        return weightVector;
    }

    private static List<Double> getFeatureVectorForDocumentClass(Map<String, List<LinkedHashMap<String, Object>>> documents, List<Integer> featureIndexList, String key) {
        return featureIndexList.stream().map(i -> documents.get(key).stream()
                    .anyMatch(a -> a.get("feature")
                            .equals(i)) ?
                    ((Integer) documents.get(key).stream()
                            .filter(a -> a.get("feature")
                                    .equals(i))
                            .collect(Collectors.toList())
                            .get(0)
                            .get("frequency"))
                            .doubleValue() : 0.0)
                    .collect(Collectors.toList());
    }

    private static List<Double> getBinaryFeatureVectorForDocumentClass(Map<String, List<LinkedHashMap<String, Object>>> documents, List<Integer> featureIndexList, String key) {
        return featureIndexList.stream().map(i -> documents.get(key).stream()
                .anyMatch(a -> a.get("feature")
                        .equals(i)) ?
                1.0 : 0.0)
                .collect(Collectors.toList());
    }

    private static Map<String, List<LinkedHashMap<String, Object>>> getFeaturesForAllClasses(GraphDatabaseService db)
    {
        List<Node> classes = getAllClasses(db);

        Map<String, List<LinkedHashMap<String, Object>>> featureMap = new HashMap<>();

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

    private static List<LinkedHashMap<String, Object>> getFeaturesForClass(GraphDatabaseService db, Node classNode) {
        List<LinkedHashMap<String, Object>> patternIds = new ArrayList<>();

        for (Path p : db.traversalDescription()
                .depthFirst()
                .relationships(withName("HAS_CLASS"), Direction.INCOMING)
                .evaluator(Evaluators.fromDepth(1))
                .evaluator(Evaluators.toDepth(1))
                .traverse(classNode)) {

            LinkedHashMap<String, Object> featureMap = new LinkedHashMap<>();

            if(p.relationships().iterator().hasNext()) {
                featureMap.put("frequency", p.relationships().iterator().next().getProperty("matches"));
            }
            else {
                featureMap.put("frequency", 0);
            }

            featureMap.put("feature", ((Long)p.endNode().getId()).intValue());

            patternIds.add(featureMap);
        }
        return patternIds;
    }

    private static class VsmCacheModel {
        private GraphDatabaseService db;
        private Object cfIndex;
        private Object vsmIndex;
        private Map<String, List<LinkedHashMap<String, Object>>> documents;
        private List<Integer> featureIndexList;

        public VsmCacheModel(GraphDatabaseService db) {
            this.db = db;
            cfIndex = vectorSpaceModelCache.getIfPresent("CLASS_FEATURE_INDEX");
            vsmIndex = vectorSpaceModelCache.getIfPresent("GLOBAL_FEATURE_INDEX");
        }

        public Map<String, List<LinkedHashMap<String, Object>>> getDocuments() {
            return documents;
        }

        public List<Integer> getFeatureIndexList() {
            return featureIndexList;
        }

        public VsmCacheModel invoke() {
            if(cfIndex != null)
            {
                documents = (Map<String, List<LinkedHashMap<String, Object>>>) cfIndex;
            }
            else
            {
                documents = VectorUtil.getFeaturesForAllClasses(db);
                vectorSpaceModelCache.put("CLASS_FEATURE_INDEX", documents);
            }

            if(vsmIndex != null)
            {
                featureIndexList = (List<Integer>) vsmIndex;
            }
            else
            {
                featureIndexList = VectorUtil.getFeatureIndexList(db);
                vectorSpaceModelCache.put("GLOBAL_FEATURE_INDEX", featureIndexList);
            }
            return this;
        }
    }
}
