package org.clyze.source.irfitter.source;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.clyze.doop.sarif.SARIFGenerator;
import org.clyze.persistent.model.SymbolWithId;
import org.clyze.sarif.model.Result;
import org.clyze.source.irfitter.source.model.ElementWithPosition;

/**
 * A SARIF generator that can read Doop results.
 */
public class DoopSARIFGenerator extends SARIFGenerator {
    final Map<String, Collection<? extends ElementWithPosition<?, ?>>> mapping;
    final boolean debug;

    public DoopSARIFGenerator(File db, File out, String version, boolean standalone,
                              Map<String, Collection<? extends ElementWithPosition<?, ?>>> mapping,
                              boolean debug) {
        super(db, out, version, standalone);
        this.mapping = mapping;
        this.debug = debug;
    }

    @Override
    public void process() {
        List<Result> results = new ArrayList<>();
        AtomicInteger elementCount = new AtomicInteger(0);

        for (Map.Entry<String, Collection<? extends ElementWithPosition<?, ?>>> entry : mapping.entrySet()) {
            String doopId = entry.getKey();
            if (debug)
                System.out.println("[SARIF] Processing id: " + doopId);
            for (ElementWithPosition<?, ?> srcElem : entry.getValue()) {
                SymbolWithId symbol = srcElem.getSymbol();
                if (symbol == null) {
                    System.out.println("Source element has no symbol: " + srcElem);
                    continue;
                }
                processElement(results, symbol, elementCount);
            }
        }
        System.out.println("Elements processed: " + elementCount);

        generateSARIF(results);
    }
}
