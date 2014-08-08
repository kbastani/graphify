package org.neo4j.nlp.impl.util;

import com.google.gson.Gson;
import junit.framework.TestCase;
import org.codehaus.jackson.map.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Transaction;
import org.neo4j.test.TestGraphDatabaseFactory;
import org.neo4j.nlp.helpers.GraphManager;
import org.neo4j.nlp.models.PatternCount;

import java.io.FileNotFoundException;
import java.util.*;

public class GraphManagerTest extends TestCase {

    @Test
    public void testCypherJsonResult() throws Exception {

        GraphDatabaseService db = new TestGraphDatabaseFactory().newImpermanentDatabaseBuilder().newGraphDatabase();
        db = setUpDb(db);

        Map<String, Object> params = new HashMap<>();

        params.put("name", "Information theory");

        String similarClass = executeCypher(db, getSimilarClass(), params);

        assertEquals("[]", similarClass);

    }

    private static String executeCypher(GraphDatabaseService db, String cypher, Map<String, Object> params) throws FileNotFoundException {
        org.neo4j.cypher.javacompat.ExecutionEngine engine;
        engine = new org.neo4j.cypher.javacompat.ExecutionEngine(db);

        org.neo4j.cypher.javacompat.ExecutionResult result;

        try ( Transaction tx = db.beginTx(); ) {
            result = engine.execute(cypher, params);
            tx.success();
        }

        ObjectMapper objectMapper = new ObjectMapper();

        List<Map> results = new ArrayList<>();
        for (Map<String,Object> row : result) {
            results.add(new LinkedHashMap(row));
        }
        String cypherJsonResult = new Gson().toJson(results); // for gogle Gson

        return cypherJsonResult;
    }

    private static String getSimilarClass() {
        return  "MATCH (class:Class { name: {name} })\n" +
                "MATCH (class)<-[:HAS_CLASS]-(pattern:Pattern),\n" +
                "      (pattern)-[:HAS_CLASS]->(classes:Class)\n" +
                "RETURN class.name as class, classes.name as relatedTo, count(pattern) as patterns\n" +
                "ORDER BY patterns DESC\n" +
                "LIMIT 10";
    }

    @Test
    public void testGetTemplate() throws Exception {
        @NotNull
        GraphManager graphManager = new GraphManager("pattern", "pattern");
        System.out.println(graphManager.GetTemplate("(\\b[\\w']+\\b)\\sis\\sknown\\s?(\\b[\\w']+\\b)"));
    }

    public static GraphDatabaseService setUpDb(GraphDatabaseService graphdb)
    {
        graphdb = new TestGraphDatabaseFactory().newImpermanentDatabaseBuilder().newGraphDatabase();
        Transaction tx = graphdb.beginTx();

        return graphdb;
    }

    @Test
    public void testGeneratePattern() throws Exception {
        GraphManager graphManager = new GraphManager("Pattern", "pattern");
        String result = graphManager.GeneratePattern(0, new PatternCount("word", 2, null), "(\\b[\\w']+\\b)\\s(\\b[\\w']+\\b)");

        assertEquals("(\\b[\\w']+\\b)\\sword\\s(\\b[\\w']+\\b)", result);
    }
}