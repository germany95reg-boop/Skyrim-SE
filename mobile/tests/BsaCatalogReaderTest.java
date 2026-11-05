import dev.skyrimsemobile.launcher.BsaCatalogReader;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class BsaCatalogReaderTest {
    private static void eq(Object actual,Object expected,String where) {
        if (!Objects.equals(actual,expected)) throw new AssertionError(where+": "+actual+" != "+expected);
    }
    private static void put32(ByteArrayOutputStream o,long n) {
        for(int i=0;i<4;i++)o.write((int)(n>>(i*8))&255);
    }
    private static void put64(ByteArrayOutputStream o,long n) {
        for(int i=0;i<8;i++)o.write((int)(n>>(i*8))&255);
    }
    private static void bytes(ByteArrayOutputStream o,String s){
        byte[] b=s.getBytes(StandardCharsets.US_ASCII);
        o.write(b,0,b.length);
    }
    private static byte[] fixture(String[] folders, String[][] files,boolean compressedArchive) {
        int foldersNum=folders.length,count=0,folderBytes=0,fileBytes=0;
        for(int i=0;i<foldersNum;i++){
            count+=files[i].length;folderBytes+=folders[i].length()+1;
            for(String f:files[i])fileBytes+=f.length()+1;
        }
        int flags=0x83|(compressedArchive?4:0);
        int prefix=36+foldersNum*24;
        ByteArrayOutputStream o=new ByteArrayOutputStream();
        bytes(o,"BSA\0");put32(o,105);put32(o,36);put32(o,flags);
        put32(o,foldersNum);put32(o,count);put32(o,folderBytes);put32(o,fileBytes);put32(o,1);
        int cursor=prefix+fileBytes;
        for(int i=0;i<foldersNum;i++){
            put64(o,i+1);put32(o,files[i].length);put32(o,0);
            put64(o,cursor);
            cursor+=1+folders[i].length()+1+files[i].length*16;
        }
        for(int i=0;i<foldersNum;i++){
            o.write(folders[i].length()+1);bytes(o,folders[i]);o.write(0);
            for(int j=0;j<files[i].length;j++){
                put64(o,100+j);put32(o,(i==0&&j==0?0x40000000L:0)+32+j);put32(o,10000+i*100+j);
            }
        }
        for(String[] group:files)for(String name:group){bytes(o,name);o.write(0);}
        return o.toByteArray();
    }
    private static void set32(byte[] b,int offset,int value){
        for(int i=0;i<4;i++)b[offset+i]=(byte)(value>>>(i*8));
    }
    private static void expectError(byte[] data,String name) {
        try {BsaCatalogReader.read(new ByteArrayInputStream(data));throw new AssertionError("accepted "+name);}
        catch(IOException expected) { /* safe rejection */ }
    }
    public static void main(String[] a)throws Exception {
        byte[] sample=fixture(new String[]{"meshes\\creatures","textures\\fish"},
            new String[][]{{"fish.nif","fish2.nif"},{"fish.dds"}},false);
        BsaCatalogReader.Catalog parsed=BsaCatalogReader.read(new ByteArrayInputStream(sample));
        eq(parsed.folderCount,2,"folders");eq(parsed.fileCount,3,"files");eq(parsed.nifCount,2,"nifs");
        eq(parsed.indexedCount,3,"indexed");eq(parsed.partialIndex,false,"complete");
        eq(parsed.find("FISH",true,30).size(),2,"case insensitive find");
        eq(parsed.find("dds",true,30).size(),0,"NIF filter");
        eq(parsed.entries.get(0).path,"meshes/creatures/fish.nif","path");
        eq(parsed.entries.get(0).storedBytes,32L,"size");
        eq(parsed.entries.get(0).compressed,true,"per file compression toggle");
        byte[] c=fixture(new String[]{"meshes"},new String[][]{{"one.nif","two.dds"}},true);
        eq(BsaCatalogReader.read(new ByteArrayInputStream(c)).entries.get(0).compressed,false,"XOR compression");
        eq(BsaCatalogReader.read(new ByteArrayInputStream(c)).entries.get(1).compressed,true,"default compression");
        // MarketplaceTextures-style 9-byte discrepancy: only actual zero padding is safe.
        byte[] padded=Arrays.copyOf(sample,sample.length+9);
        set32(padded,28,("fish.nif".length()+1)+("fish2.nif".length()+1)+
            ("fish.dds".length()+1)+9);
        eq(BsaCatalogReader.read(new ByteArrayInputStream(padded)).indexedCount,3,
           "allow nine trailing zero filename-table padding bytes");
        byte[] nonzero=padded.clone();
        nonzero[sample.length+3]=1;
        expectError(nonzero,"nonzero filename-table tail");
        byte[] tooMuch=Arrays.copyOf(sample,sample.length+17);
        set32(tooMuch,28,("fish.nif".length()+1)+("fish2.nif".length()+1)+
            ("fish.dds".length()+1)+17);
        expectError(tooMuch,"excessive filename-table tail");
        byte[] corrupt=Arrays.copyOf(sample, sample.length-2);expectError(corrupt,"truncated table");
        corrupt=sample.clone();corrupt[4]=104;expectError(corrupt,"unsupported version");
        corrupt=sample.clone();corrupt[12]=0;expectError(corrupt,"no names");
        corrupt=sample.clone();corrupt[20]=120;expectError(corrupt,"count mismatch");
        corrupt=sample.clone();corrupt[28]=(byte)0xff;corrupt[29]=(byte)0xff;corrupt[30]=(byte)0xff;corrupt[31]=(byte)0x7f;expectError(corrupt,"oversized metadata");
        corrupt=sample.clone();corrupt[0]='X';expectError(corrupt,"bad magic");
        String[] fs=new String[110];String[][] names=new String[110][];
        for(int i=0;i<110;i++){
            fs[i]="meshes\\set"+i;
            int n=6+(i<84?1:0); // 110*6+84=744
            names[i]=new String[n];for(int j=0;j<n;j++)names[i][j]="fish"+j+(j%2==0?".nif":".dds");
        }
        BsaCatalogReader.Catalog fish=BsaCatalogReader.read(new ByteArrayInputStream(fixture(fs,names,false)));
        eq(fish.folderCount,110,"110 folders");eq(fish.fileCount,744,"744 files");
        eq(fish.flags,0x83L,"flags from reported BSA");
        eq(fish.entries.get(743).path.contains("meshes/set109"),true,"last entry");
        System.out.println("PASS: BSA v105 catalogue reader: 110 folders/744 files, names, NIF, search, flags, security checks");
    }
}
