package dev.skyrimsemobile.launcher;

import java.io.IOException;
public final class NifVertexTintPreviewTest {
    public static void main(String[] args)throws Exception{
        // Two perpendicular faces, only face 0 is real vertex tint; face 1 DDS.
        float[] tris={0,0,0,1,0,0,0,1,0,
                      0,0,0,0,1,0,0,0,1};
        float[] slots={0,0,-1,0,0,-1,0,0,-1,
                       0,0,1,0,0,1,0,0,1};
        float[] raw={1,1,1,.5f,1,0,0,1,0,0,1,.8f,
                     1,1,1,1,1,1,1,1,1,1,1,1};
        var out=NifVertexTintPreview.shade(tris,slots,raw);
        assert out.tintedTriangles==1&&out.nearWhiteVertices==1;
        assert out.colorVertices==3&&out.rawMeanRgb<1f;
        assert out.rgba[0]<.8f&&out.rgba[0]>.1f:out.rgba[0];
        assert out.rgba[3]==.5f&&out.rgba[7]==1f;
        assert out.rgba[4]<.8f&&out.rgba[5]==0f;
        assert out.rgba[12]==1f&&out.rgba[20]==1f:
            "real DDS material must remain unmodified";
        assert raw[0]==1f:"raw source must not be mutated";
        assert out.note().contains("множитель нейтрального материала");
        assert NifVertexTintPreview.shade(tris,slots,raw).rgba[0]==out.rgba[0]:
            "deterministic shader-safe preview";
        // v2.52: missing DDS on UV-capable geometry uses neutral slot 0.
        // Neither neutral nor valid DDS geometry may be recolored by tint.
        float[] neutralSlots=slots.clone();
        neutralSlots[11]=0f;neutralSlots[14]=0f;neutralSlots[17]=0f;
        var neutral=NifVertexTintPreview.shade(tris,neutralSlots,raw);
        assert neutral.tintedTriangles==1;
        for(int i=12;i<24;i++)
            assert neutral.rgba[i]==raw[i]:
                "neutral missing-DDS geometry must retain its original RGBA";
        float[] invalidSlot=slots.clone();
        invalidSlot[2]=Float.NaN;
        try{NifVertexTintPreview.shade(tris,invalidSlot,raw);
            throw new AssertionError("NaN material slot accepted");}
        catch(IOException expected){}
        float[] invalidPosition=tris.clone();
        invalidPosition[0]=Float.POSITIVE_INFINITY;
        try{NifVertexTintPreview.shade(invalidPosition,slots,raw);
            throw new AssertionError("non-finite position accepted");}
        catch(IOException expected){}
        float[] broken=slots.clone();broken[5]=1;
        try{NifVertexTintPreview.shade(tris,broken,raw);
            throw new AssertionError("mixed per-face slots accepted");}
        catch(IOException expected){}
        float[] invalid=raw.clone();invalid[0]=Float.NaN;
        try{NifVertexTintPreview.shade(tris,slots,invalid);
            throw new AssertionError("NaN tint accepted");}
        catch(IOException expected){}
        float[] allWhite={1,1,1,1,1,1,1,1,1,1,1,1};
        float[] flatTri={0,0,0,0,0,0,0,0,0};
        var degenerate=NifVertexTintPreview.shade(flatTri,
            new float[]{0,0,-1,0,0,-1,0,0,-1},allWhite);
        assert degenerate.degenerateTriangles==1;
        assert degenerate.nearWhiteVertices==3;
        assert degenerate.rgba[0]>.1f&&degenerate.rgba[0]<.8f;
        System.out.println("PASS v2.51 bounded neutral SSE RGBA shading, shader-safe fidelity stats");
    }
}
