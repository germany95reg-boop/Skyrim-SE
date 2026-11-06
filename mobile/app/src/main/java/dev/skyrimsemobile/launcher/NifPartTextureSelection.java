package dev.skyrimsemobile.launcher;

import java.io.IOException;

/** Select materials by the actual BSTriShape block index, never by texture-set order.
 * Pure Java: can be regression-tested without an Android device or commercial assets.
 */
public final class NifPartTextureSelection {
    private NifPartTextureSelection(){}

    /** Resolve exactly one part. An invalid/missing diffuse is an explicit error. */
    public static NifMaterialLinkInspector.Link require(
            NifMaterialLinkInspector.Result links,int shapeIndex)throws IOException{
        if(links==null)throw new IOException("Сначала прочитай связи частей NIF");
        for(NifMaterialLinkInspector.Link link:links.links){
            if(link.shape!=shapeIndex)continue;
            if(!link.resolved())throw new IOException("BSTriShape #"+shapeIndex+
                ": материалы не разрешены: "+link.problem);
            if(link.diffuse==null||link.diffuse.isEmpty())
                throw new IOException("BSTriShape #"+shapeIndex+": диффузная DDS не задана");
            DdsArchiveFinder.normalizeDds(link.diffuse);
            return link;
        }
        throw new IOException("В NIF нет связей материалов для BSTriShape #"+shapeIndex);
    }
}
