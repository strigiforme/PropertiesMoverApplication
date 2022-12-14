package com.utils.Files.PropertiesFile;

/**
 * Abstract class for objects that can have filters applied to their contents
 *
 * @author Howard Pearce
 */
public abstract class BaseFilter {
    /**
     * Filter the provided PropertiesFile
     *
     * @param fileToFilter the file the filter will be applied on
     */
    public abstract void applyFilter(PropertiesFile fileToFilter);
}
