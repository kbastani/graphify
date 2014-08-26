package org.neo4j.nlp.impl.util;

import com.google.gson.Gson;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Transaction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by kennybastani on 8/25/14.
 */
public class CypherUtil {

    public static String executeCypher(GraphDatabaseService db, String cypher, Map<String, Object> params) {
        org.neo4j.cypher.javacompat.ExecutionEngine engine;
        engine = new org.neo4j.cypher.javacompat.ExecutionEngine(db);
        List<Map<String, Object>> results = new ArrayList<>();

        org.neo4j.cypher.javacompat.ExecutionResult result;

        try ( Transaction tx = db.beginTx() ) {
            result = engine.execute(cypher, params);
            for (Map<String,Object> row : result) {
                results.add(new LinkedHashMap<>(row));
            }
            tx.success();
        } catch(Exception ex)
        {
            throw ex;
        }

        return new Gson().toJson(results);
    }
}
