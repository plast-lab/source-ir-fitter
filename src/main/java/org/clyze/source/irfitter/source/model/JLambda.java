package org.clyze.source.irfitter.source.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.clyze.persistent.model.Position;
import org.clyze.source.irfitter.ir.model.IRMethod;

/**
 * A source-level lambda (such as Java 8 lambdas).
 */
public class JLambda extends JMethod {
    public JLambda(SourceFile srcFile, String name, List<JVariable> parameters,
                   Position outerPos, JType parent, Position pos) {
        super(srcFile, name, null, parameters, new HashSet<>(), outerPos, parent, pos, false);
    }

    @Override
    public void initSymbolFromIRElement(IRMethod irMethod) {
        List<String> parameterNames = new ArrayList<>();
        List<String> parameterTypes = new ArrayList<>();
        List<String> irArgTypes = irMethod.paramTypes;
        int irArgTypesSize = irArgTypes.size();
        int srcArgsSize = this.parameters.size();
        if (irArgTypesSize < srcArgsSize) {
            System.err.println("ERROR: SRC/IR argument information arity mismatch: " + srcArgsSize + " vs. " + irArgTypesSize);
            return;
        } else if (irArgTypesSize > srcArgsSize)
            System.out.println("WARNING: SRC/IR argument information arity mismatch: " + srcArgsSize + " vs. " + irArgTypesSize + ". Handling as capturing lambda.");
        int captureShift = irArgTypesSize - srcArgsSize;
        for (int i = 0; i < srcArgsSize; i++) {
            parameterNames.add(this.parameters.get(i).name);
            // We assume that capture parameters are first, then original lambda parameters.
            parameterTypes.add(irArgTypes.get(i + captureShift));
        }
        symbol = JMethod.fromIRMethod(irMethod, srcFile, irMethod.name, parameterNames, parameterTypes, pos, outerPos, parent);
    }

    @Override
    public String toString() {
        return "Lambda" + getLocation();
    }
}
