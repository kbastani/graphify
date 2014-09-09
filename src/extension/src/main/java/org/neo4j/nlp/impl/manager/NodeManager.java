package org.neo4j.nlp.impl.manager;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.helpers.collection.IteratorUtil;
import org.neo4j.nlp.abstractions.Manager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * This class manages a caching layer for reads and writes to
 * the attached data storage.
 */
public class NodeManager {

    // Initialize static global node cache
    public static final Cache<Long, HashMap<String, Object>>
            globalNodeCache = CacheBuilder.newBuilder()
            .maximumSize(20000000)
            .build();

    public NodeManager() {
    }

    public static Map<String, Object> getNodeFromGlobalCache(Long id)
    {
        return globalNodeCache.getIfPresent(id);
    }

    public Node getOrCreateNode(Manager manager, String key, GraphDatabaseService db)
    {
        // Federates requests to a graph node data management object
        return manager.getOrCreateNode(key, db);
    }

    public Map<String, Object> getNodeAsMap(Long id, GraphDatabaseService graphDb)
    {
        boolean success;

        // Update the node's property in cache
        Map<String, Object> node = globalNodeCache.getIfPresent(id);

        if(node == null)
        {
            // The node isn't available in the cache, go to the database and retrieve it
            success = addNodeToCache((gdb, cache) -> getNodeHashMap(id, gdb, cache), graphDb);
            if(success) node = globalNodeCache.getIfPresent(id);
        }

        return node;
    }

    public Object getNodeProperty(Long id, String key, GraphDatabaseService graphDb)
    {
        boolean success;

        // Update the node's property in cache
        Map<String, Object> node = globalNodeCache.getIfPresent(id);

        if(node == null)
        {
            // The node isn't available in the cache, go to the database and retrieve it
            success = addNodeToCache((gdb, cache) -> getNodeHashMap(id, gdb, cache), graphDb);
            if(success) node = globalNodeCache.getIfPresent(id);
        }

        return node != null ? node.get(key) : null;
    }

    public boolean setNodeProperty(Long id, String key, Object value, GraphDatabaseService graphDb)
    {
        boolean success = true;

        // Update the node's property in cache
        Map<String, Object> node = globalNodeCache.getIfPresent(id);

        if(node == null)
        {
            // The node isn't available in the cache, go to the database and retrieve it
            success = addNodeToCache((gdb, cache) -> getNodeHashMap(id, gdb, cache), graphDb);
            if(success) node = globalNodeCache.getIfPresent(id);
        }

        // Set the node property
        if (node != null) {
            node.put(key, value);
        }

        // TODO: Remove this in favor of a distributed messaging bus architecture
        Transaction tx = graphDb.beginTx();
        graphDb.getNodeById(id).setProperty(key, value);
        tx.success();
        tx.close();

        return success;
    }

    private void getNodeHashMap(Long id, GraphDatabaseService gdb, Cache<Long, HashMap<String, Object>> cache) {
        Node thisNode = gdb.getNodeById(id);
        List<String> keys = new ArrayList<>();
        HashMap<String, Object> nodeMap = new HashMap<>();
        IteratorUtil.addToCollection(thisNode.getPropertyKeys(), keys)
                .stream()
                .forEach(n -> nodeMap.put(n, thisNode.getProperty(n)));
        nodeMap.put("id", id);
        cache.put(id, nodeMap);
    }

    private static boolean addNodeToCache(BiConsumer<GraphDatabaseService, Cache<Long, HashMap<String, Object>>> operation, GraphDatabaseService db)
    {

        try (Transaction tx = db.beginTx()) {
            operation.accept(db, globalNodeCache);
            tx.success();
        } catch (Exception e) {
            return false;
        }

        return true;
    }
}
