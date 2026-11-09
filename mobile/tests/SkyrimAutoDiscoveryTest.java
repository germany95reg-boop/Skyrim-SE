package dev.skyrimsemobile.launcher;

import java.util.Arrays;
import java.util.Collections;

public final class SkyrimAutoDiscoveryTest {
    public static void main(String[] args){
        assert SkyrimAutoDiscovery.dataDirectory("Data");
        assert SkyrimAutoDiscovery.dataDirectory("data");
        assert !SkyrimAutoDiscovery.dataDirectory("Data Backup");
        assert !SkyrimAutoDiscovery.dataDirectory(null);
        assert SkyrimAutoDiscovery.childDataDirectory(
            Arrays.asList("Profiles","DATA","Screenshots")).equals("DATA");
        assert SkyrimAutoDiscovery.childDataDirectory(
            Arrays.asList("Meshes","Skyrim")).equals(null);
        assert SkyrimAutoDiscovery.mainMaster(
            Arrays.asList("Update.esm","Skyrim.esm","ccBGSSSE001-Fish.esm"))
            .equals("Skyrim.esm");
        assert SkyrimAutoDiscovery.mainMaster(
            Arrays.asList("skyrim.ESM","Update.esm")).equals("skyrim.ESM");
        assert SkyrimAutoDiscovery.mainMaster(
            Arrays.asList("Skyrim.esm.bak","Skyrim.esm.zip"))==null;
        assert SkyrimAutoDiscovery.mainMaster(Collections.emptyList())==null;
        assert SkyrimAutoDiscovery.mainMaster(null)==null;
        System.out.println("PASS Skyrim root/Data discovery and main master selection");
    }
}
