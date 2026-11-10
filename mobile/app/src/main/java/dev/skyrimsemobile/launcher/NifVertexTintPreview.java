package dev.skyrimsemobile.launcher;

import java.io.IOException;
import java.util.Locale;

/**
 * v2.51: conservative, read-only diagnostic lighting of UV-less SSE
 * vertex-color tint. Skyrim vertex color is a MULTIPLIER, not a diffuse
 * material; never treat RGB==white as an emissive white surface.
 * A bounded neutral base and geometric two-sided Lambert term reveal
 * topology in Vulkan's low-feature atlas-safe shader without extra samplers.
 * This is explicitly NOT Bethesda's lighting/material model.
 */
public final class NifVertexTintPreview {
    private NifVertexTintPreview(){}
    private static final float BASE=0.72f;
    private static final float AMBIENT=0.34f;
    private static final float DIFFUSE=0.66f;

    public static final class Result {
        public final float[] rgba;
        public final int tintedTriangles, nearWhiteVertices, colorVertices,
            degenerateTriangles;
        public final float rawMeanRgb;
        Result(float[] rgba,int triangles,int white,int vertices,int degenerate,
               float mean){
            this.rgba=rgba; tintedTriangles=triangles;
            nearWhiteVertices=white;colorVertices=vertices;
            degenerateTriangles=degenerate;rawMeanRgb=mean;
        }
        public String note(){
            if(colorVertices==0)return "SSE RGBA: нет геометрии с цветами без UV.";
            return String.format(Locale.ROOT,
                "SSE RGBA v2.51: %d треугольников; исходный средний RGB %.1f%%; "+
                "почти белых вершин %.1f%% (%d/%d); вырожденных граней %d. "+
                "В предпросмотре RGB — множитель нейтрального материала с "+
                "двусторонним геометрическим светом, НЕ оригинальный Skyrim-шейдер.",
                tintedTriangles,100.0*rawMeanRgb,
                100.0*nearWhiteVertices/colorVertices,
                nearWhiteVertices,colorVertices,degenerateTriangles);
        }
    }

    /**
     * Triangle order matches the validated world-space NIF surface and atlas
     * vertices. Each material slot is constant over its three vertices.
     * Slot -1 alone uses the new tint path; DDS slots remain untouched.
     */
    public static Result shade(float[] worldTriangles,float[] uvAndSlot,
                               float[] rawRgba)throws IOException{
        if(worldTriangles==null||uvAndSlot==null||rawRgba==null||
           worldTriangles.length<9||worldTriangles.length%9!=0||
           uvAndSlot.length!=worldTriangles.length||
           rawRgba.length!=(long)worldTriangles.length/3*4)
            throw new IOException("SSE tint: неверные размеры треугольников/слотов/RGBA");
        float[] result=rawRgba.clone();
        int triangles=worldTriangles.length/9,tinted=0,white=0,seen=0,degenerate=0;
        double total=0;
        for(int t=0;t<triangles;t++){
            int base=t*9;
            float slot=uvAndSlot[base+2];
            if(!Float.isFinite(slot))throw new IOException("SSE tint: неверный слот");
            for(int v=0;v<3;v++){
                if(uvAndSlot[base+v*3+2]!=slot)
                    throw new IOException("SSE tint: разные слоты у одной грани");
                for(int c=0;c<4;c++){
                    float f=rawRgba[(t*3+v)*4+c];
                    if(!Float.isFinite(f)||f<0f||f>1f)
                        throw new IOException("SSE tint: RGBA вне [0,1]");
                }
                for(int k=0;k<3;k++)
                    if(!Float.isFinite(worldTriangles[base+v*3+k]))
                        throw new IOException("SSE tint: вершина не конечна");
            }
            if(slot!=-1f)continue;
            tinted++;
            double ax=worldTriangles[base],ay=worldTriangles[base+1],
                az=worldTriangles[base+2];
            double ux=worldTriangles[base+3]-ax,
                uy=worldTriangles[base+4]-ay,uz=worldTriangles[base+5]-az;
            double vx=worldTriangles[base+6]-ax,
                vy=worldTriangles[base+7]-ay,vz=worldTriangles[base+8]-az;
            double nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;
            double length=Math.sqrt(nx*nx+ny*ny+nz*nz);
            double light;
            if(!Double.isFinite(length)||length<1e-10){
                light=0.50;degenerate++;
            }else{
                // Direction is normalized. abs gives safe two-sided light
                // while culling and original Skyrim normals are unresolved.
                light=AMBIENT+DIFFUSE*Math.abs(
                    (nx*0.38+ny*0.64+nz*0.67)/length);
            }
            float factor=(float)Math.max(0.0,Math.min(0.78,BASE*light));
            for(int v=0;v<3;v++){
                int c=(t*3+v)*4;
                if(rawRgba[c]>=0.97f&&rawRgba[c+1]>=0.97f&&
                   rawRgba[c+2]>=0.97f)white++;
                total+=(rawRgba[c]+rawRgba[c+1]+rawRgba[c+2])/3.0;
                seen++;
                for(int ch=0;ch<3;ch++)
                    result[c+ch]=rawRgba[c+ch]*factor;
                // Source alpha is retained, not presumed opacity.
            }
        }
        return new Result(result,tinted,white,seen,degenerate,
            seen==0?0:(float)(total/seen));
    }
}
