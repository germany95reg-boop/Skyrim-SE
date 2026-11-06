package dev.skyrimsemobile.launcher;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.net.Uri;
import java.util.Collections;
import java.util.List;

/**
 * v2.9: only metadata/SAF URIs and NIF paths are saved privately.
 * No archive contents, DDS pixels, game files or credentials are copied.
 * A saved URI may be re-opened only while Android still grants persisted read.
 */
final class BsaSessionStore {
    private static final String PREFS="skyrim_bsa_session_v29";
    private static final String ARCHIVE="archive_uri", ARCHIVE_NAME="archive_name";
    private static final String MODEL="model_path", DATA_TREE="data_tree_uri";
    private static final String RECENT="recent_nif_paths";
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
        if(!uri.toString().equals(prior))e.remove(MODEL).remove(RECENT);
        e.putString(ARCHIVE,uri.toString())
         .putString(ARCHIVE_NAME,name==null?"Последний BSA":name)
         .apply();
    }

    void clearArchive(){
        prefs.edit().remove(ARCHIVE).remove(ARCHIVE_NAME)
            .remove(MODEL).remove(RECENT).apply();
    }

    void saveModel(Uri archiveUri,String nifPath){
        if(archiveUri==null||!RecentNifPaths.valid(nifPath))return;
        if(!archiveUri.toString().equals(prefs.getString(ARCHIVE,"")))return;
        prefs.edit().putString(MODEL,nifPath)
            .putString(RECENT,RecentNifPaths.insert(
                prefs.getString(RECENT,""),nifPath)).apply();
    }

    List<String> recentModels(Uri archiveUri){
        if(archiveUri==null||
            !archiveUri.toString().equals(prefs.getString(ARCHIVE,"")))
            return Collections.emptyList();
        return RecentNifPaths.parse(prefs.getString(RECENT,""));
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

    /** Clear metadata and explicitly release the application's retained grants. */
    void forgetAll(){
        Uri archive=lastArchive(), tree=lastDataTree();
        prefs.edit().clear().apply();
        releaseRead(archive);
        if(tree!=null&&!tree.equals(archive))releaseRead(tree);
    }

    private void releaseRead(Uri uri){
        if(uri==null)return;
        try{
            resolver.releasePersistableUriPermission(uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }catch(SecurityException | IllegalArgumentException ignored){
            // The Android provider may already have revoked this permission.
        }
    }
}
