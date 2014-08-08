Pattern4j
==========================

This is a Neo4j unmanaged extension used for document and text classification using graph-based hierarchical pattern recognition.

1. Build it:

        mvn assembly:assembly -DdescriptorId=jar-with-dependencies

2. Copy target/pattern4j-1.0.0-jar-with-dependencies.jar to the plugins/ directory of your Neo4j server.

3. Configure Neo4j by adding a line to conf/neo4j-server.properties:

        org.neo4j.server.thirdparty_jaxrs_classes=org.neo4j.nlp.ext=/service

4. Start Neo4j server.

5. Query it over HTTP:

Get similar labels:

    curl http://localhost:7474/service/pattern/similar/{label}

Train the natural language recognition model on text about 'Document classification':

    curl -H "Content-Type: application/json" -d '{"label": "Document classification", "text": "Documents may be classified according to their subjects or according to other attributes (such as document type, author, printing year etc.). In the rest of this article only subject classification is considered. There are two main philosophies of subject classification of documents: The content based approach and the request based approach."}' http://localhost:7474/service/pattern/training

Get the top 10 most related text documents in the database for the supplied string of text:

    curl -H "Content-Type: application/json" -d '{"text": "A document is a written or drawn representation of thoughts. Originating from the Latin Documentum meaning lesson - the verb means to teach, and is pronounced similarly, in the past it was usually used as a term for a written proof used as evidence."}' http://localhost:7474/service/pattern/related

Get a sorted list of labels that are most related to the label 'Document':

    curl http://localhost:7474/service/pattern/similar/Document

Example response:

    [
        {
            "weight": 0.3157894736842105,
            "class": "Memory"
        },
        {
            "weight": 0.2631578947368421,
            "class": "Data"
        },
        {
            "weight": 0.21052631578947367,
            "class": "Intelligence"
        },
        {
            "weight": 0.15789473684210525,
            "class": "Machine learning"
        },
        {
            "weight": 0.05263157894736842,
            "class": "Document classification"
        }
    ]

Get a sorted list of labels that are most related to the label 'Document classification':

    curl http://localhost:7474/service/pattern/similar/Document%20classification

Get the top 10 most related text documents in the database for the supplied string of text:

    curl -H "Content-Type: application/json" -d '{"text": "A document is a written or drawn representation of thoughts."}' http://localhost:7474/service/pattern/related

Example response:

    [
        {
            "count": 43,
            "data": "A document is a written or drawn representation of thoughts. Originating from the Latin Documentum meaning lesson - the verb doceō means to teach, and is pronounced similarly, in the past it was usually used as a term for a written proof used as evidence. In the computer age, a document is usually used to describe a primarily textual file, along with its structure and design, such as fonts, colors and additional images.",
            "label": "Document"
        },
        {
            "count": 10,
            "data": "AI research is highly technical and specialised, and is deeply divided into subfields that often fail to communicate with each other.[5] Some of the division is due to social and cultural factors: subfields have grown up around particular institutions and the work of individual researchers. AI research is also divided by several technical issues. Some subfields focus on the solution of specific problems. Others focus on one of several possible approaches or on the use of a particular tool or towards the accomplishment of particular applications.",
            "label": "Artificial intelligence"
        },
        {
            "count": 5,
            "data": "Content based classification is classification in which the weight given to particular subjects in a document determines the class to which the document is assigned. It is, for example, a rule in much library classification that at least 20% of the content of a book should be about the class to which the book is assigned.",
            "label": "Document"
        },
        {
            "count": 5,
            "data": "Iconic memory is a fast decaying store of visual information; a type of sensory memory that briefly stores an image which has been perceived for a small duration. ",
            "label": "Memory"
        },
        {
            "count": 5,
            "data": "The room in which the experiment took place was infused with the scent of vanilla, as odour is a strong cue for memory. ",
            "label": "Memory"
        },
        {
            "count": 5,
            "data": "Raw data, i.e., unprocessed data, refers to a collection of numbers, characters and is a relative term; data processing commonly occurs by stages, and the \"processed data\" from one stage may be considered the \"raw data\" of the next. ",
            "label": "Data"
        },
        {
            "count": 5,
            "data": "Echoic memory is a fast decaying store of auditory information, another type of sensory memory that briefly stores sounds that have been perceived for short durations. ",
            "label": "Memory"
        },
        {
            "count": 5,
            "data": "Natural language processing (NLP) is a field of computer science, artificial intelligence, and linguistics concerned with the interactions between computers and human (natural) languages. As such, NLP is related to the area of human–computer interaction. Many challenges in NLP involve natural language understanding, that is, enabling computers to derive meaning from human or natural language input, and others involve natural language generation.",
            "label": "Natural language processing"
        },
        {
            "count": 5,
            "data": "One is able to place in memory information that resembles objects, places, animals or people in sort of a mental image. ",
            "label": "Memory"
        },
        {
            "count": 5,
            "data": "Methods to optimize memorization Memorization is a method of learning that allows an individual to recall information verbatim. ",
            "label": "Memory"
        }
    ]
