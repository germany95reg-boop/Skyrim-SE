package dev.skyrimsemobile.launcher;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/** Synthetic link-to-Data-index checks; no commercial BSA contents required. */
public final class NifPartDdsAvailabilityTest {
    private static int checks;
    private static void ok(boolean yes,String message){
        checks++;if(!yes)throw new AssertionError(message);
    }
    private static DdsArchiveFinder.Hit hit(String path){
        return new DdsArchiveFinder.Hit("textures.bsa",path,
            new BsaCatalogReader.Entry(path,128,1024,false));
    }

    public static void main(String[] args)throws Exception{
        byte[] nif=NifMaterialLinkInspectorTest.example();
        NifMaterialLinkInspector.Result parts=NifMaterialLinkInspector.inspect(nif);
        List<String> targets=NifPartDdsAvailability.targets(parts);
        ok(targets.size()==4,"two parts, two distinct texture pairs");
        ok(targets.contains("textures/a_d.dds"),"first diffuse");
        ok(targets.contains("textures/b_n.dds"),"second normal");
        List<DdsArchiveFinder.Hit> found=Arrays.asList(
            hit("textures/a_d.dds"),hit("textures/a_n.dds"),hit("textures/b_n.dds"));
        String report=NifPartDdsAvailability.report(parts,found,true);
        ok(report.contains("BSTriShape #0"),"first part ID shown");
        ok(report.contains("BSTriShape #1"),"second part ID shown");
        ok(report.contains("Итого: 3 найдено из 4"),"separate per-part totals");
        ok(report.contains("не найдено в проверенных индексах: textures/b_d.dds"),
            "missing diffuse is specific to second part");
        ok(report.contains("НАЙДЕНО: textures/b_n.dds"),"found second normal");
        ok(report.contains("не применены к составной Vulkan-модели"),
            "never claims multi-material rendering");
        ok(NifPartDdsAvailability.report(parts,found,false).contains("поиск неполный"),
            "incomplete BSA index clearly marked");

        NifBlockInspector.Result blocks=NifBlockInspector.inspect(nif);
        byte[] shared=nif.clone();
        NifMaterialLinkInspectorTest.set(shared,blocks.blocks.get(1).byteOffset+92,2);
        NifMaterialLinkInspector.Result sharedParts=
            NifMaterialLinkInspector.inspect(shared);
        ok(NifPartDdsAvailability.targets(sharedParts).size()==2,
            "one shared texture set searched once, two geometry parts reported");
        ok(NifPartDdsAvailability.report(sharedParts,Arrays.asList(hit("textures/a_d.dds")),true)
            .contains("BSTriShape #1"),"shared target status reported for second part");

        byte[] unresolved=nif.clone();
        NifMaterialLinkInspectorTest.set(unresolved,blocks.blocks.get(1).byteOffset+92,-1);
        NifMaterialLinkInspector.Result partial=
            NifMaterialLinkInspector.inspect(unresolved);
        ok(NifPartDdsAvailability.targets(partial).size()==2,
            "unresolved geometry part excluded from lookup targets");
        ok(NifPartDdsAvailability.report(partial,found,true)
            .contains("частей с неподдерживаемыми связями: 1"),
            "link failure visible without hiding valid first part");
        try{NifPartDdsAvailability.targets(null);throw new AssertionError("null accepted");}
        catch(IOException expected){checks++;}
        System.out.println("PASS v2.11 per-part DDS BSA index lookup: "+checks+" checks");
    }
}
