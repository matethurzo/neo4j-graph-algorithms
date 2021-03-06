package org.neo4j.graphalgo.bench;

import org.neo4j.graphalgo.ShortestPathDeltaSteppingProc;
import org.neo4j.graphalgo.ShortestPathsProc;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.kernel.api.exceptions.KernelException;
import org.neo4j.kernel.impl.proc.Procedures;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.test.TestGraphDatabaseFactory;
import org.openjdk.jmh.annotations.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author mknblch
 */
@Threads(1)
@Fork(1)
@Warmup(iterations = 10)
@Measurement(iterations = 10)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class ShortestPathsComparisionBenchmark {

    public static final RelationshipType RELATIONSHIP_TYPE = RelationshipType.withName("TYPE");

    private static GraphDatabaseAPI db;
    private static List<Node> lines = new ArrayList<>();

    private static final Map<String, Object> params = new HashMap<>();

    @Setup
    public static void setup() throws KernelException {
        db = (GraphDatabaseAPI)
                new TestGraphDatabaseFactory()
                        .newImpermanentDatabaseBuilder()
                        .newGraphDatabase();
        final Procedures procedures = db.getDependencyResolver()
                .resolveDependency(Procedures.class);
        procedures.registerProcedure(ShortestPathDeltaSteppingProc.class);
        procedures.registerProcedure(ShortestPathsProc.class);

        createNet(100); // 10000 nodes; 1000000 edges
        params.put("head", lines.get(0).getId());
        params.put("delta", 2.5);
    }

    private static void createNet(int size) {
        try (Transaction tx = db.beginTx()) {
            List<Node> temp = null;
            for (int i = 0; i < size; i++) {
                List<Node> line = createLine(size);
                if (null != temp) {
                    for (int j = 0; j < size; j++) {
                        for (int k = 0; k < size; k++) {
                            if (i == k) {
                                continue;
                            }
                            createRelation(temp.get(j), line.get(k));
                        }
                    }
                }
                temp = line;
            }
            tx.success();
        }
    }

    private static List<Node> createLine(int length) {
        ArrayList<Node> nodes = new ArrayList<>();
        Node temp = db.createNode();
        nodes.add(temp);
        lines.add(temp);
        for (int i = 1; i < length; i++) {
            Node node = db.createNode();
            nodes.add(temp);
            createRelation(temp, node);
            temp = node;
        }
        return nodes;
    }

    private static Relationship createRelation(Node from, Node to) {
        Relationship relationship = from.createRelationshipTo(to, RELATIONSHIP_TYPE);
        double rndCost = Math.random() * 5.0; //(to.getId() % 5) + 1.0; // (0-5)
        relationship.setProperty("cost",  rndCost);
        return relationship;
    }

    @Benchmark
    public Object _01_benchmark_deltaStepping() {
        return db.execute("MATCH (n {id:$head}) WITH n CALL algo.deltaStepping.stream(n, 'cost', $delta" +
                ", {concurrency:1})" +
                " YIELD nodeId, distance RETURN nodeId, distance", params)
                .stream()
                .count();
    }

    @Benchmark
    public Object _02_benchmark_singleDijkstra() {
        return db.execute("MATCH (n {id:$head}) WITH n CALL algo.shortestPaths.stream(n, 'cost')" +
                " YIELD nodeId, distance RETURN nodeId, distance", params)
                .stream()
                .count();
    }

}
