package dev.skyrimsemobile.launcher;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.net.Uri;

/**
 * v2.9: only metadata/SAF URIs and NIF paths are saved privately.
 * No archive contents, DDS pixels, game files or credentials are copied.
 * A saved URI may be re-opened only while Android still grants persisted read.
 */
final class BsaSessionStore {
    private static final String PREFS="skyrim_bsa_session_v29";
    private static final String ARCHIVE="archive_uri", ARCHIVE_NAME="archive_name";
    private static final String MODEL="model_path", DATA_TREE="data_tree_uri";
    private final SharedPreferences prefs;
    private final ContentResolver resolver;

    BsaSessionStore(Context context){
        prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        resolver=context.getContentResolver();
    }

    boolean retainReadPermission(Uri uri){
        if(uri==null)return false;
        try{
            resolver.takePersistableUriPermission(uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }catch(SecurityException | IllegalArgumentException denied){
            return false; // Some SAF providers do not allow persistable access.
        }
        return hasReadPermission(uri);
    }

    boolean hasReadPermission(Uri uri){
        if(uri==null)return false;
        for(UriPermission grant:resolver.getPersistedUriPermissions())
            if(grant.isReadPermission()&&uri.equals(grant.getUri()))return true;
        return false;
    }

    private Uri readGrantedUri(String key){
        String raw=prefs.getString(key,"");
        if(raw.isEmpty())return null;
        try{
            Uri uri=Uri.parse(raw);
            return hasReadPermission(uri)?uri:null;
        }catch(IllegalArgumentException ex){return null;}
    }

    Uri lastArchive(){
        Uri uri=readGrantedUri(ARCHIVE);
        if(uri==null && !prefs.getString(ARCHIVE,"").isEmpty())clearArchive();
        return uri;
    }

    String archiveDisplayName(){
        return prefs.getString(ARCHIVE_NAME,"Последний BSA");
    }

    void saveArchive(Uri uri,String name){
        if(!hasReadPermission(uri))return;
        String prior=prefs.getString(ARCHIVE,"");
        SharedPreferences.Editor e=prefs.edit();
        if(!uri.toString().equals(prior))e.remove(MODEL);
        e.putString(ARCHIVE,uri.toString())
         .putString(ARCHIVE_NAME,name==null?"Последний BSA":name)
         .apply();
    }

    void clearArchive(){
        prefs.edit().remove(ARCHIVE).remove(ARCHIVE_NAME)
            .remove(MODEL).apply();
    }

    void saveModel(Uri archiveUri,String nifPath){
        if(archiveUri==null||nifPath==null||nifPath.isEmpty())return;
        if(archiveUri.toString().equals(prefs.getString(ARCHIVE,"")))
            prefs.edit().putString(MODEL,nifPath).apply();
    }

    String lastModel(Uri archiveUri){
        return archiveUri!=null &&
            archiveUri.toString().equals(prefs.getString(ARCHIVE,""))
                ? prefs.getString(MODEL,""):"";
    }

    void clearModel(){
        prefs.edit().remove(MODEL).apply();
    }

    Uri lastDataTree(){
        Uri uri=readGrantedUri(DATA_TREE);
        if(uri==null && !prefs.getString(DATA_TREE,"").isEmpty())clearDataTree();
        return uri;
    }

    void saveDataTree(Uri tree){
        if(hasReadPermission(tree))
            prefs.edit().putString(DATA_TREE,tree.toString()).apply();
    }

    void clearDataTree(){
        prefs.edit().remove(DATA_TREE).apply();
    }
}
