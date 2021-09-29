package org.clyze.source.irfitter.ir;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.clyze.source.irfitter.ir.model.IRLambda;
import org.clyze.source.irfitter.ir.model.IRMethod;
import org.clyze.source.irfitter.ir.model.IRType;

/**
 * State maintained by the IR parsing phase.
 */
public class IRState {
    public final List<IRType> irTypes = new ArrayList<>();

    public void resolveLambdas(boolean debug) {
        if (debug)
            System.out.println("Resolving IR lambdas...");
        Map<String, IRMethod> irMethods = new HashMap<>();
        for (IRType irType : irTypes)
            for (IRMethod irMethod : irType.methods)
                irMethods.put(irMethod.getId(), irMethod);
        for (IRType irType : irTypes)
            for (IRMethod irMethod : irType.methods) {
                List<IRLambda> irLambdas = irMethod.lambdas;
                if (irLambdas == null)
                    continue;
                for (IRLambda irLambda : irLambdas) {
                    IRMethod impl = irMethods.get(irLambda.implementation);
                    if (impl == null)
                        System.err.println("ERROR: could not resolve IR lambda: " + irLambda);
                    else {
                        if (debug)
                            System.out.println("Resolved lambda: " + irLambda);
                        irLambda.implMethod = impl;
                    }
                }
            }

    }
}
