Graphify
==========================

This is a Neo4j unmanaged extension used for document and text classification.

![Training Dataset](http://i.imgur.com/FlsmQkf.png?1 "Training Dataset")

![Natural Language Parsing Model](http://i.imgur.com/hJuRJje.png?1 "Natural Language Parsing Model")

![Classify Unlabeled Documents](http://i.imgur.com/j90qOru.png?2 "Classify Unlabeled Documents")

Building the extension
==========================

1. Build it:

        mvn assembly:assembly -DdescriptorId=jar-with-dependencies

2. Copy target/graphify-1.0.0-jar-with-dependencies.jar to the plugins/ directory of your Neo4j server.

3. Configure Neo4j by adding a line to conf/neo4j-server.properties:

        org.neo4j.server.thirdparty_jaxrs_classes=org.neo4j.nlp.ext=/service

4. Start Neo4j server.

5. Query it over HTTP.

Using it
==========================

####Get similar labels:

    curl http://localhost:7474/service/graphify/similar/{label}

####Train the natural language recognition model on text about 'Document classification':

    curl -H "Content-Type: application/json" -d '{"label": ["Document classification"], "text": ["Documents may be classified according to their subjects or according to other attributes (such as document type, author, printing year etc.). In the rest of this article only subject classification is considered. There are two main philosophies of subject classification of documents: The content based approach and the request based approach."]}' http://localhost:7474/service/graphify/training

####Get the most related classes to a snippet of text:

    curl -H "Content-Type: application/json" -d '{"text": "A document is a written or drawn representation of thoughts. Originating from the Latin Documentum meaning lesson - the verb means to teach, and is pronounced similarly, in the past it was usually used as a term for a written proof used as evidence."}' http://localhost:7474/service/graphify/classify

####Get a sorted list of labels that are most related to the label 'Document classification':

    curl http://localhost:7474/service/graphify/similar/Document%20classification

#####Example response:

    {
        "classes": [
            {
                "class": "Document",
                "similarity": 0.19563160874988336
            },
            {
                "class": "Intelligence",
                "similarity": 0.1778887274627789
            },
            {
                "class": "Machine learning",
                "similarity": 0.14800216450227222
            },
            {
                "class": "Data",
                "similarity": 0.1467923282078174
            },
            {
                "class": "Memory",
                "similarity": 0.14600346713601134
            }
        ]
    }
