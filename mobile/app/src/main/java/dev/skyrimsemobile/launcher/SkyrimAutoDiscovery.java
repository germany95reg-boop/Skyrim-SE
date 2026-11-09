package dev.skyrimsemobile.launcher;

import java.util.Collection;

/**
 * Pure Java rules for detecting Skyrim's Data directory and primary master.
 * Android SAF resolves the actual document IDs and permissions separately.
 */
public final class SkyrimAutoDiscovery {
    private SkyrimAutoDiscovery(){}

    public static boolean dataDirectory(String name){
        return name!=null&&"Data".equalsIgnoreCase(name.trim());
    }

    public static String mainMaster(Collection<String> fileNames){
        if(fileNames==null)return null;
        for(String name:fileNames)
            if(name!=null&&"Skyrim.esm".equalsIgnoreCase(name.trim()))
                return name;
        return null;
    }

    public static String childDataDirectory(Collection<String> folderNames){
        if(folderNames==null)return null;
        for(String name:folderNames)if(dataDirectory(name))return name;
        return null;
    }
}
