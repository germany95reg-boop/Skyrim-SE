package dev.skyrimsemobile.launcher;

import java.io.ByteArrayInputStream;

/** Synthetic valid NIF block table, fake shape payload, and actual LZ4 frame. */
public final class Tes4NifStructureProfileTest {
    public static void main(String[] args)throws Exception {
        byte[] nif=NifBlockInspectorTest.valid();
        int offset=512;
        var raw=Tes4MatchedNifVerifier.verify(
            new ByteArrayInputStream(Tes4MatchedNifVerifierTest.place(nif,offset)),
            new BsaCatalogReader.Entry("meshes/sample.nif",nif.length,offset,false),
            0x83,"Meshes0.bsa");
        assert raw.totalBlocks==3:raw.report();
        assert raw.rootBlocks==1;
        assert raw.meshCandidates==1;
        // The fixture has a deliberately nondecodable 24-byte fake shape.
        assert raw.staticParts==0;
        assert raw.report().contains("Таблица NIF: 3 блоков");
        assert raw.report().contains("BSTriShape: 0 частей");
        byte[] compressed=Tes4MatchedNifVerifierTest.packed(nif);
        var decoded=Tes4MatchedNifVerifier.verify(
            new ByteArrayInputStream(Tes4MatchedNifVerifierTest.place(compressed,offset)),
            new BsaCatalogReader.Entry("meshes/sample.nif",compressed.length,offset,true),
            0x83,"Meshes0.bsa");
        assert decoded.totalBlocks==raw.totalBlocks;
        assert decoded.meshCandidates==raw.meshCandidates;
        assert decoded.compressed&&decoded.decodedBytes==nif.length;
        byte[] broken=nif.clone();
        broken[NifBlockInspectorTest.indicesPos+2]=7;
        var safe=Tes4MatchedNifVerifier.verify(
            new ByteArrayInputStream(Tes4MatchedNifVerifierTest.place(broken,offset)),
            new BsaCatalogReader.Entry("meshes/invalidtable.nif",broken.length,offset,false),
            0x83,"Meshes0.bsa");
        assert safe.totalBlocks==-1;
        assert safe.structureStatus!=null;
        assert safe.report().contains("Таблица NIF: не разобрана");
        System.out.println("PASS real LZ4 + valid NIF block table; malformed table reported, no fake geometry claim");
    }
}
