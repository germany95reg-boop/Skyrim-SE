package dev.skyrimsemobile.launcher;

import java.util.Collections;
import java.util.List;

/** Pure Java model selection: an explicitly tapped path must resolve exactly.
 * Typing in the search field retains the legacy first-result behavior.
 */
public final class NifCatalogSelection {
    private NifCatalogSelection(){}
    public static List<BsaCatalogReader.Entry> matches(
            BsaCatalogReader.Catalog catalog,String selectedPath,String query){
        if(catalog==null)return Collections.emptyList();
        if(selectedPath!=null&&!selectedPath.isEmpty()){
            for(BsaCatalogReader.Entry entry:catalog.entries)
                if(entry.nif()&&selectedPath.equals(entry.path))
                    return Collections.singletonList(entry);
            // Stale explicit paths MUST NOT accidentally fall back to another NIF.
            return Collections.emptyList();
        }
        return catalog.find(query,true,1);
    }
}
