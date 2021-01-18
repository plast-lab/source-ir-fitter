package org.clyze.source.irfitter.source.model;

import org.clyze.persistent.metadata.FileInfo;
import org.clyze.persistent.model.BasicMetadata;

/**
 * This class is used with the metadata model library.
 */
public class SourceFileInfo extends FileInfo {
    public SourceFileInfo(String packageName, String inputName, String inputFilePath, String source, BasicMetadata elements) {
        super(packageName == null || "".equals(packageName) ? "" : packageName + ".", inputName, inputFilePath, source, elements);
    }

    @Override
    protected void printPatternError(String s, long l) {
        throw new UnsupportedOperationException("SourceFileInfo(): printPatternError() is not supported");
    }

    @Override
    public long getLineNumber(long l) {
        throw new UnsupportedOperationException("SourceFileInfo(): getLineNumber() is not supported");
    }

    @Override
    public long getColumnNumber(long l) {
        throw new UnsupportedOperationException("SourceFileInfo(): getColumnNumber() is not supported");
    }
}
