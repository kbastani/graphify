package org.neo4j.nlp.abstractions;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;

/**
 * This is an abstract class for object managers that maintain
 * a global cache for their object members.
 */
public abstract class Manager {
    /**
     * Get or create a node.
     */
    public abstract Node getOrCreateNode(String keyValue, GraphDatabaseService db);
}
