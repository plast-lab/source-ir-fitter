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
    public List<IRType> irTypes = new ArrayList<>();
}
