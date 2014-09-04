package org.neo4j.nlp.impl.util;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.Gson;
import org.codehaus.jackson.map.ObjectMapper;
import org.javalite.common.Convert;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.nlp.helpers.GraphManager;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by kennybastani on 8/25/14.
 */
public class VectorUtil {

    public static final Cache<String, Object> vectorSpaceModelCache = CacheBuilder.newBuilder().maximumSize(20000000).build();

    public static Double dotProduct(List<Double> v1, List<Double> v2)
    {
        if(v1.size() != v2.size())
            throw new IllegalArgumentException("Vectors must be of equal length.");

        Double result = 0.0;
        for (int i = 0; i < v1.size(); i++) {
            result = result + (v1.get(i) * v2.get(i));
        }

        return result;
    }

    public static double normVector(List<Double> v1)
    {
        double result = 0.0;

        for (int i = 0; i < v1.size(); i++) {
            result = result + (v1.get(i) * v1.get(i));
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

    public static List<Double> getFeatureVector(GraphDatabaseService db, GraphManager graphManager, String rootPattern, String input) {
        Map<String, Object> params = new HashMap<>();
        Map<Long, Integer> patternMatchers = PatternMatcher.match(rootPattern, input, db, graphManager);
        List<Integer> featureIndexList = getFeatureIndexList(db);

        List<Integer> longs = new ArrayList<>();
        Collections.addAll(longs, patternMatchers.keySet().stream().map(n -> n.intValue()).collect(Collectors.toList()).toArray(new Integer[longs.size()]));

        // Get the total number of recognized features
        Integer sum = patternMatchers.values().stream().mapToInt(a -> a.intValue()).sum();

        return featureIndexList.stream().map(i -> longs.contains(i) ? (patternMatchers.get(i.longValue()).doubleValue() / sum.doubleValue()) : 0.0).collect(Collectors.toList());
    }

    private static List<Integer> getFeatureIndexList(GraphDatabaseService db) {
        List<Integer> featureIndexList = new ArrayList<>();
        ObjectMapper objectMapper = new ObjectMapper();
        String featureIndex = getFeatureIndex();
        Map<String, Object> params = new HashMap<>();
        String cypherResults = CypherUtil.executeCypher(db, featureIndex, params);

        try {
            ArrayList<LinkedHashMap<?, ?>> results;
            results = objectMapper.readValue(cypherResults, ArrayList.class);
            featureIndexList = results.stream()
                    .map(a -> (Integer)a.get("index"))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return featureIndexList;
    }

    private static String getFeatureIndex() {
        return
                "MATCH (pattern:Pattern) RETURN id(pattern) as index ORDER BY pattern.threshold DESC";
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
                classMap.put(otherKey, key == otherKey ? 0.0 : cosineSimilarity(v1, v2));
            });

            final List<LinkedHashMap<String, Object>> finalResultList = resultList;
            classMap.keySet().forEach(ks -> {
                LinkedHashMap<String, Object> localMap = new LinkedHashMap<String, Object>();
                localMap.put("class", ks);
                localMap.put("similarity", classMap.get(ks));
                finalResultList.add(localMap);
            });

            Collections.sort(finalResultList, (a, b) -> ((String)a.get("class")).compareToIgnoreCase((String)b.get("class")));

            //resultList = finalResultList.stream().limit(k).map(n -> n).collect(Collectors.toList());

            results.put(key, finalResultList);
        }

        List<LinkedHashMap<String, Object>> similarityVector = new ArrayList<>();

        for(String key : results.keySet())
        {
            List<Double> cosineVector = new ArrayList<>();
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

        List<Double> features = getFeatureVector(db, graphManager, GraphManager.ROOT_TEMPLATE, input);

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


        List<LinkedHashMap<String, Object>> resultList = new ArrayList<>();
        LinkedHashMap<String, Double> classMap = new LinkedHashMap<>();

        documents.keySet().stream().parallel().forEach(otherKey -> {
            List<Double> v2 = featureIndexList.stream().parallel().map(i -> documents.get(otherKey).contains(i) ? 1.0 : 0.0).collect(Collectors.toList());
            classMap.put(otherKey, cosineSimilarity(features, v2));
        });

        classMap.keySet().forEach(ks -> {
                LinkedHashMap<String, Object> localMap = new LinkedHashMap<>();
                localMap.put("class", ks);
                localMap.put("similarity", classMap.get(ks));
                resultList.add(localMap);
        });

        try {
            resultList.sort((a, b) ->
            {
                Double diff = (((double) a.get("similarity")) - ((double) b.get("similarity")));
                return diff > 0 ? -1 : diff.equals(0) ? 0 : 1;
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

        List<Double> v1 = featureIndexList.stream().parallel().map(i -> documents.get(key).contains(i) ? ((Integer)featureIndexList.indexOf(i)).doubleValue() : 0.0).collect(Collectors.toList());


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
            return diff > 0 ? -1 : diff.equals(0) ? 0 : 1;
        });

        //Collections.sort(resultList, (o1, o2) -> (((double) o2.get("similarity")) - ((double) o1.get("similarity"))));

        results.put("classes", resultList);

        return results;
    }

    public static Map<String, List<LinkedHashMap<String, Object>>> similarDocumentMap(GraphDatabaseService db)
    {
        Map<String, List<Integer>> documents = getFeaturesForAllClasses(db);
        Map<String, List<LinkedHashMap<String, Object>>> results = new HashMap<>();
        List<Integer> featureIndexList = getFeatureIndexList(db);

        for(String key : documents.keySet())
        {
            List<LinkedHashMap<String, Object>> resultList = new ArrayList<>();
            LinkedHashMap<String, Double> classMap = new LinkedHashMap<>();
            List<Double> v1 = featureIndexList.stream().map(i -> documents.get(key).contains(i) ? 1.0 : 0.0).collect(Collectors.toList());
            documents.keySet().stream().filter(otherKey -> key != otherKey).forEach(otherKey -> {
                List<Double> v2 = featureIndexList.stream().map(i -> documents.get(otherKey).contains(i) ? featureIndexList.indexOf(i) : 0.0).collect(Collectors.toList());
                classMap.put(otherKey, cosineSimilarity(v1, v2));
            });

            classMap.keySet().forEach(ks -> {
                LinkedHashMap<String, Object> localMap = new LinkedHashMap<String, Object>();
                localMap.put("class", ks);
                localMap.put("similarity", classMap.get(ks));
                resultList.add(localMap);
            });

            Collections.sort(resultList, (o1, o2) -> ((int)((double)o2.get("similarity") * 10000000.0) - (int)((double)o1.get("similarity") * 10000000.0)));

            results.put(key, resultList);
        }


        return results;
    }

    public static String updateSimilarityRelationships(GraphDatabaseService db)
    {
        Map<String, List<LinkedHashMap<String, Object>>> ranks = VectorUtil.similarDocumentMap(db);

//        for (String className : ranks.keySet())
//        {
//            // Get or create a relationship
//            String mergeRelationships = "MATCH (class:Class { name: {class}})" +
//                    "FOREACH (x in {classes} |" +
//                    "MERGE (class)-[r:RELATED_TO]->(oc:Class { name: x.name })" +
//                    "SET r.similarity = x.rank)";
//
//            ArrayList<Map<String, Object>> relationParams = new ArrayList<>();
//
//            for(String otherClass : ranks.get(className).keySet())
//            {
//                Map<String, Object> classRelationMap = new HashMap<>();
//                relationParams.add(classRelationMap);
//                classRelationMap.put("name", otherClass);
//                classRelationMap.put("rank", ranks.get(className).get(otherClass));
//            }
//
//            Map<String, Object> params = new HashMap<>();
//            params.put("class", className);
//            params.put("classes", relationParams);
//            CypherUtil.executeCypher(db, mergeRelationships, params);
//        }

        return new Gson().toJson(ranks);
    }

    public static Map<String, List<Integer>> getFeaturesForAllClasses(GraphDatabaseService db)
    {
        String featureCypher = "MATCH (class:Class) RETURN class.name as class";
        Map<String, Object> params = new HashMap<>();
        String cypherResults = CypherUtil.executeCypher(db, featureCypher, params);
        ObjectMapper objectMapper = new ObjectMapper();
        ArrayList<LinkedHashMap<?, ?>> results;
        List<String> classes = new ArrayList<>();
        try {
            results = objectMapper.readValue(cypherResults, ArrayList.class);
            classes = results.stream()
                    .map(a -> (String)a.get("class"))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
        }

        Map<String, List<Integer>> featureMap = new HashMap<>();

        for (String thisClass : classes)
        {
            featureMap.put(thisClass, getFeaturesForClass(db, thisClass));
        }

        return featureMap;
    }

    public static List<Integer> getFeaturesForClass(GraphDatabaseService db, String className)
    {
        String featureCypher = "MATCH (pattern:Pattern)-[:HAS_CLASS]->(class:Class { name: {class} }) WHERE NOT (pattern)-[:NEXT]->() RETURN id(pattern) as index";
        Map<String, Object> params = new HashMap<>();
        params.put("class", className);
        String cypherResults = CypherUtil.executeCypher(db, featureCypher, params);
        ObjectMapper objectMapper = new ObjectMapper();
        ArrayList<LinkedHashMap<?, ?>> results;
        List<Integer> featureIndexList = new ArrayList<>();
        try {
            results = objectMapper.readValue(cypherResults, ArrayList.class);
            featureIndexList = results.stream()
                    .map(a -> (Integer)a.get("index"))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
        }

        return featureIndexList;
    }
}
