# Bulbapedia Knowledge Graph

A Semantic Web application that builds a Knowledge Graph (KG) from Bulbapedia, similar to how DBpedia was built from Wikipedia. This project captures Pokémon data from Bulbapedia, converts it into RDF format, and provides various interfaces to access and query the knowledge graph.

## Features

### 1. Knowledge Graph Generation

- Extracts data from Bulbapedia's wiki pages
- Converts wiki content into RDF triples
- Implements proper ontology and vocabulary design
- Reuses schema.org terms where appropriate
- Maintains multilingual labels
- Includes entity linking to DBpedia and Wikidata

### 2. Data Validation

- SHACL shapes for validating the knowledge graph structure
- Constraints on Pokémon properties (height, weight, types)
- Type hierarchy validation
- Property cardinality rules

### 3. Query Interface

- SPARQL endpoint for complex queries
- Support for inference in queries
- Predefined useful queries for common operations

### 4. Linked Data Interface

- Content negotiation (HTML/RDF)
- Human-readable HTML views
- Machine-readable RDF views
- Proper hyperlinking between resources

## Prerequisites

- Java 11 or higher
- Maven
- Git
- Adequate disk space for the knowledge graph

## Installation

1. Clone the repository:

```bash
git clone https://github.com/anjola-adeuyi/bulbapedia-knowledge-graph.git
cd bulbapedia-knowledge-graph
```

2. Build the project:

```bash
mvn clean install
```

## Running the Application

1. Start the application:

```bash
mvn exec:java -Dexec.mainClass="org.example.App"
```

This will start:

- Fuseki SPARQL endpoint on port 3330
- Linked Data interface on port 3331

2. Verify the services are running:

```bash
# Test Fuseki
curl http://localhost:3330/pokemon/query

# Test Linked Data interface
curl -H "Accept: text/turtle" http://localhost:3331/resource/0001
```

## Testing The Features

### 1. SPARQL Endpoint (Port 3330)

#### Using curl:

```bash
# Example query to get all Pokémon names
curl -X POST \
  -H "Content-Type: application/sparql-query" \
  -d 'PREFIX schema: <http://schema.org/>
      SELECT ?name WHERE {
        ?s schema:name ?name
      }' \
  http://localhost:3330/pokemon/query
```

#### Using Postman:

1. Create a new POST request to `http://localhost:3330/pokemon/query`
2. Set Content-Type header to `application/sparql-query`
3. In the body, enter your SPARQL query
4. Send the request

#### Example Queries:

1. Get Pokémon and their types:

```sparql
PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX pokemon: <http://example.org/pokemon/>
PREFIX schema: <http://schema.org/>

SELECT ?name ?primaryType ?secondaryType
WHERE {
  ?pokemon rdf:type pokemon:Pokemon ;
           schema:name ?name ;
           pokemon:primaryType ?primaryType .
  OPTIONAL { ?pokemon pokemon:secondaryType ?secondaryType }
}
ORDER BY ?name
```

2. Find evolution chains:

```sparql
PREFIX pokemon: <http://example.org/pokemon/>
PREFIX schema: <http://schema.org/>

SELECT ?baseName ?evolvedName ?commonType
WHERE {
  ?base schema:name ?baseName ;
        pokemon:primaryType ?commonType .
  ?evolved pokemon:evolvesFrom+ ?base ;
           schema:name ?evolvedName ;
           pokemon:primaryType ?commonType .
}
ORDER BY ?baseName ?evolvedName
```

### 2. Linked Data Interface (Port 3331)

#### Browser Access:

1. Visit `http://localhost:3331/resource/0001` for Bulbasaur
2. Navigate through Pokémon using the evolution chain links

#### Programmatic Access:

```bash
# Get RDF data (Turtle format)
curl -H "Accept: text/turtle" http://localhost:3331/resource/0001

# Get HTML representation
curl -H "Accept: text/html" http://localhost:3331/resource/0001
```

### 3. Validation

The application automatically validates all data against SHACL shapes. You can find the shapes in:

- `pokemon-shapes.ttl`

To manually validate:

1. Extract the shapes file
2. Use a SHACL validator (like Apache Jena SHACL)
3. Run validation against your RDF data

## Project Structure

```bash
bulbapedia-knowledge-graph/
├── src/
│   └── main/
│       ├── java/
│       │   └── org/example/
│       │       ├── App.java                    # Main application
│       │       ├── client/                     # Wiki API client
│       │       ├── inference/                  # RDF inference
│       │       ├── linking/                    # Entity linking
│       │       ├── parser/                     # Wiki parsing
│       │       ├── rdf/                        # RDF conversion
│       │       ├── server/                     # Web servers
│       │       └── validation/                 # SHACL validation
│       └── resources/
│           ├── templates/                      # HTML templates
│           └── queries/                        # SPARQL queries
├── pokemon.ttl                                 # Generated KG
├── pokemon-shapes.ttl                          # SHACL shapes
└── pom.xml
```

## Features Implementation

The project implements all required features from the course specification:

1. Knowledge Graph Creation ✓

   - Captures wiki content as RDF
   - Converts infoboxes to triples
   - Preserves wiki links as RDF relationships

2. Multilingual Support ✓

   - Labels in multiple languages
   - Uses proper language tags
   - Integrates Pokédex translations

3. Schema Validation ✓

   - SHACL shapes for validation
   - Derived from wiki templates
   - Consistent vocabulary usage

4. External Linking ✓

   - Links to DBpedia
   - Links to Wikidata
   - Preserves wiki page links

5. SPARQL Access ✓

   - Full SPARQL endpoint
   - Support for complex queries
   - Inference capabilities

6. Linked Data Interface ✓
   - Content negotiation
   - HTML/RDF views
   - Proper hyperlinking

## Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/new-feature`)
3. Commit your changes (`git commit -m 'Add some new feature'`)
4. Push to the branch (`git push origin feature/new-feature`)
5. Open a Pull Request

## Acknowledgments

- Professor Antoine Zimmermann and Professor Victor Charpenay for the course structure and guidance
- Bulbapedia community for maintaining the Pokémon wiki
- DBpedia and YAGO project for inspiration
