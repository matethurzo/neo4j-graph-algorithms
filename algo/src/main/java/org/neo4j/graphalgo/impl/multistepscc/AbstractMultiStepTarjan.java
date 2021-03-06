package org.neo4j.graphalgo.impl.multistepscc;

import com.carrotsearch.hppc.*;
import com.carrotsearch.hppc.procedures.IntProcedure;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.api.RelationshipConsumer;
import org.neo4j.graphdb.Direction;

/**
 * @author mknblch
 */
public abstract class AbstractMultiStepTarjan {

    private final Graph graph;

    private BitSet onStack;
    private IntIntMap indices;
    private IntIntMap lowLink;
    private IntStack stack;

    private int index;

    public AbstractMultiStepTarjan(Graph graph) {
        this.graph = graph;
    }

    public AbstractMultiStepTarjan compute(IntSet nodes) {

        indices = new IntIntScatterMap(nodes.size());
        lowLink = new IntIntScatterMap(nodes.size());
        stack = new IntStack(nodes.size());
        onStack = new BitSet();
        nodes.forEach((IntProcedure) this::strongConnect);
        return this;
    }

    private void reset() {
        indices.clear();
        lowLink.clear();
        onStack.clear();
        stack.clear();
        index = 0;
    }

    private void strongConnect(int node) {
        if (indices.containsKey(node)) {
            return;
        }
        lowLink.put(node, index);
        indices.put(node, index);
        index++;
        stack.push(node);
        onStack.set(node);
        graph.forEachRelationship(node, Direction.OUTGOING, this::accept);
        if (indices.get(node) == lowLink.get(node)) {
            relax(node);
        }
    }

    private void relax(int nodeId) {
        IntHashSet connected = new IntHashSet();
        int w;
        do {
            w = stack.pop();
            onStack.clear(w);
            connected.add(w);
        } while (w != nodeId);
        processSCC(nodeId, connected);
    }

    private boolean accept(int source, int target, long edgeId) {
        if (!indices.containsKey(target)) {
            strongConnect(target);
            lowLink.put(source, Math.min(lowLink.get(source), lowLink.get(target)));
        } else if (onStack.get(target)) {
            lowLink.put(source, Math.min(lowLink.get(source), indices.get(target)));
        }
        return true;
    }

    public abstract void processSCC(int root, IntHashSet connected);
}
